package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ThroughputTest {

    @Before
    public void setUp() {}

    @Test
    public void test1_validate1000Transactions_under2Seconds() {
        MockWallet wallet = new MockWallet();
        NonceStore ns     = new NonceStore();
        GossipStore gs    = new GossipStore();
        GossipEngine ge   = new GossipEngine(mock(BleManager.class), mock(WifiDirectManager.class), 
                                             gs, mock(GossipEngine.GossipListener.class), "test");

        ValidationPhaseHandler handler = new ValidationPhaseHandler(wallet, ns, ge);

        long startMs  = System.currentTimeMillis();
        int  passed   = 0;

        for (int i = 0; i < 1000; i++) {
            final boolean[] ok = {false};
            String tokenId = "tok_" + i;
            String receiver = "receiver_key";
            long ts = System.currentTimeMillis();
            String nonce = "nonce_" + i;
            String sig = wallet.signTransaction(tokenId + receiver + ts + nonce);

            Transaction tx = new Transaction("tx_" + i, tokenId, wallet.getPublicKey(), receiver, ts, nonce, sig);

            handler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
                @Override public void onValidationPassed(Transaction t) { ok[0] = true; }
                @Override public void onValidationFailed(Transaction t, ValidationResult r) {}
            });
            if (ok[0]) passed++;
        }

        long elapsed = System.currentTimeMillis() - startMs;
        assertEquals(1000, passed);
        assertTrue("Elapsed: " + elapsed + "ms", elapsed < 2000);
    }

    @Test
    public void test2_check10000Nonces_under1Second() {
        NonceStore store   = new NonceStore();
        long       startMs = System.currentTimeMillis();

        for (int i = 0; i < 10_000; i++) {
            store.checkAndRecord("nonce_speed_" + i);
        }

        long elapsed = System.currentTimeMillis() - startMs;
        assertEquals(10_000, store.size());
        assertTrue(elapsed < 1000);
    }

    @Test
    public void test3_serialize10000Proofs_under5Seconds() {
        long startMs = System.currentTimeMillis();
        int  errors  = 0;

        for (int i = 0; i < 10_000; i++) {
            try {
                TransactionProof proof = new TransactionProof(
                        "tx_" + i, "tok_" + i, "s", "r",
                        System.currentTimeMillis(), "n", "s", "a",
                        System.currentTimeMillis(), TransactionProof.Role.SENDER);
                if (proof.toJson().toString().isEmpty()) errors++;
            } catch (Exception e) {
                errors++;
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        assertEquals(0, errors);
        assertTrue(elapsed < 5000);
    }

    @Test
    public void test4_100CompleteTransactions_under10Seconds() {
        long startMs  = System.currentTimeMillis();
        int  succeeded = 0;

        for (int i = 0; i < 100; i++) {
            MockWallet sender   = new MockWallet();
            MockWallet receiver = new MockWallet();
            sender.tokenToReturn = new WalletInterface.TokenInfo("tok_" + i, 10000L, System.currentTimeMillis() + 60000);

            NonceStore  ns = new NonceStore();
            GossipStore gs = new GossipStore();
            GossipEngine ge = new GossipEngine(mock(BleManager.class), mock(WifiDirectManager.class), 
                                               gs, mock(GossipEngine.GossipListener.class), "dev_" + i);

            PreparePhaseHandler    prepare    = new PreparePhaseHandler(sender, mock(BleManager.class), mock(WifiDirectManager.class));
            ValidationPhaseHandler validate   = new ValidationPhaseHandler(receiver, ns, ge);
            CommitPhaseHandler     recCommit  = new CommitPhaseHandler(receiver, null, null);
            CommitPhaseHandler     sndCommit  = new CommitPhaseHandler(sender, null, null);

            final Transaction[] sentTx = {null}, validTx = {null};
            final TransactionProof[] proof = {null};

            prepare.execute(receiver.getPublicKey(), 10000L, true, new PreparePhaseHandler.PrepareListener() {
                @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                @Override public void onPrepareFailed(String r) {}
            });
            ShadowLooper.idleMainLooper();
            if (sentTx[0] == null) continue;

            validate.execute(sentTx[0], new ValidationPhaseHandler.ValidationListener() {
                @Override public void onValidationPassed(Transaction tx) { validTx[0] = tx; }
                @Override public void onValidationFailed(Transaction tx, ValidationResult r) {}
            });
            ShadowLooper.idleMainLooper();
            if (validTx[0] == null) continue;

            recCommit.executeReceiverCommit(validTx[0], true, new CommitPhaseHandler.CommitListener() {
                @Override public void onReceiverCommitComplete(TransactionProof p) {}
                @Override public void onSenderCommitComplete(TransactionProof p) {}
                @Override public void onCommitFailed(String id, String r) {}
            });
            ShadowLooper.idleMainLooper();

            String ackData = "Received:" + validTx[0].getTxId() + ":" + validTx[0].getTokenId();
            TransactionAck ack = new TransactionAck(validTx[0].getTxId(), validTx[0].getTokenId(), 
                                                    receiver.getPublicKey(), System.currentTimeMillis(), 
                                                    receiver.signTransaction(ackData), true);

            sndCommit.executeSenderCommit(sentTx[0], ack, new CommitPhaseHandler.CommitListener() {
                @Override public void onSenderCommitComplete(TransactionProof p) { proof[0] = p; }
                @Override public void onReceiverCommitComplete(TransactionProof p) {}
                @Override public void onCommitFailed(String id, String r) {}
            });
            ShadowLooper.idleMainLooper();

            if (proof[0] != null) succeeded++;
        }

        long elapsed = System.currentTimeMillis() - startMs;
        assertEquals(100, succeeded);
        assertTrue(elapsed < 10000);
    }

    @Test
    public void test5_100000DelayCalculations_under1Second() {
        AdaptiveDelayCalculator calc    = new AdaptiveDelayCalculator();
        long                    startMs = System.currentTimeMillis();
        for (int i = 0; i < 100_000; i++) {
            calc.calculateDelayMs(i % 20);
        }
        long elapsed = System.currentTimeMillis() - startMs;
        assertTrue(elapsed < 1000);
    }
}
