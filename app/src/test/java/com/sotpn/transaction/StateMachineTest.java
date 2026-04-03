package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;

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
public class StateMachineTest {

    private MockWallet senderWallet;
    private MockWallet receiverWallet;

    @Before
    public void setUp() {
        senderWallet = new MockWallet();
        receiverWallet = new MockWallet();
    }

    private Transaction buildTx() {
        return new Transaction(
                "tx_sm_001", "tok_sm_001",
                senderWallet.getPublicKey(), receiverWallet.getPublicKey(),
                System.currentTimeMillis(),
                "nonce_sm_001", "");
    }

    private void signTx(Transaction tx) {
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(senderWallet.signTransaction(data));
    }

    @Test
    public void test1_correctPhaseOrder_allTransitionsWork() {
        Transaction tx = buildTx();

        tx.setPhase(TransactionPhase.PREPARE);
        assertEquals(TransactionPhase.PREPARE, tx.getPhase());

        tx.setPhase(TransactionPhase.VALIDATING);
        assertEquals(TransactionPhase.VALIDATING, tx.getPhase());

        tx.setPhase(TransactionPhase.DELAYING);
        assertEquals(TransactionPhase.DELAYING, tx.getPhase());

        tx.setPhase(TransactionPhase.COMMITTING);
        assertEquals(TransactionPhase.COMMITTING, tx.getPhase());

        tx.setPhase(TransactionPhase.FINALIZED);
        assertEquals(TransactionPhase.FINALIZED, tx.getPhase());
    }

    @Test
    public void test2_anyPhase_canTransitionToFailed() {
        TransactionPhase[] allPhases = {
                TransactionPhase.PREPARE,
                TransactionPhase.VALIDATING,
                TransactionPhase.DELAYING,
                TransactionPhase.COMMITTING,
                TransactionPhase.FINALIZED
        };

        for (TransactionPhase phase : allPhases) {
            Transaction tx = buildTx();
            tx.setPhase(phase);
            tx.setPhase(TransactionPhase.FAILED);
            assertEquals(TransactionPhase.FAILED, tx.getPhase());
        }
    }

    @Test
    public void test3_defaultPhase_isPrepare() {
        Transaction tx = buildTx();
        assertEquals(TransactionPhase.PREPARE, tx.getPhase());
    }

    @Test
    public void test4_phase_isValidatingAfterPrepare() {
        senderWallet.tokenToReturn = new com.sotpn.wallet.WalletInterface.TokenInfo(
                "tok_sm", 10_000L, System.currentTimeMillis() + 60_000);

        PreparePhaseHandler handler = new PreparePhaseHandler(senderWallet, mock(BleManager.class), mock(WifiDirectManager.class));

        final Transaction[] sentTx = {null};
        handler.execute(receiverWallet.getPublicKey(), 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                    @Override public void onPrepareFailed(String r) {}
                });
        ShadowLooper.idleMainLooper();

        assertNotNull(sentTx[0]);
        assertEquals(TransactionPhase.VALIDATING, sentTx[0].getPhase());
    }

    @Test
    public void test5_phase_isDelayingAfterValidation() {
        NonceStore ns = new NonceStore();
        GossipStore gs = new GossipStore();
        GossipEngine ge = new GossipEngine(mock(BleManager.class), mock(WifiDirectManager.class), 
                                           gs, mock(GossipEngine.GossipListener.class), "test");

        ValidationPhaseHandler handler = new ValidationPhaseHandler(receiverWallet, ns, ge);

        Transaction tx = buildTx();
        signTx(tx);

        final Transaction[] validTx = {null};
        handler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction t) { validTx[0] = t; }
            @Override public void onValidationFailed(Transaction t, ValidationResult r) {}
        });
        ShadowLooper.idleMainLooper();

        assertNotNull(validTx[0]);
        assertEquals(TransactionPhase.DELAYING, validTx[0].getPhase());
    }

    @Test
    public void test6_phase_isCommittingAfterDelay() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        GossipStore gs = new GossipStore();
        AdaptiveDelayHandlerTest.MockAdaptiveDelayHandler mock =
                new AdaptiveDelayHandlerTest.MockAdaptiveDelayHandler(calc, gs);

        Transaction tx = buildTx();
        mock.simulateComplete(tx, new AdaptiveDelayHandler.DelayListener() {
            @Override public void onDelayComplete(Transaction t) {}
            @Override public void onDelayAborted(Transaction t, GossipStore.ConflictResult c) {}
            @Override public void onDelayProgress(long r, long total, AdaptiveDelayCalculator.RiskLevel risk) {}
        });

        assertEquals(TransactionPhase.COMMITTING, tx.getPhase());
    }

    @Test
    public void test7_phase_isFinalizedAfterCommit() {
        CommitPhaseHandler handler = new CommitPhaseHandler(senderWallet, null, null);

        Transaction tx = buildTx();
        signTx(tx);
        
        String ackData = "Received:" + tx.getTxId() + ":" + tx.getTokenId();
        com.sotpn.model.TransactionAck ack = new com.sotpn.model.TransactionAck(
                tx.getTxId(), tx.getTokenId(),
                receiverWallet.getPublicKey(), System.currentTimeMillis(),
                receiverWallet.signTransaction(ackData), true);

        handler.executeSenderCommit(tx, ack,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        assertEquals(TransactionPhase.FINALIZED, tx.getPhase());
    }

    @Test
    public void test8_phase_isFailedAfterConflict() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        GossipStore gs = new GossipStore();
        AdaptiveDelayHandlerTest.MockAdaptiveDelayHandler mock =
                new AdaptiveDelayHandlerTest.MockAdaptiveDelayHandler(calc, gs);

        Transaction tx = buildTx();
        GossipStore.ConflictResult conflict = new GossipStore.ConflictResult(true, "tok", "tx1", "tx2", "dA", "dB");

        mock.simulateAbort(tx, conflict, new AdaptiveDelayHandler.DelayListener() {
            @Override public void onDelayComplete(Transaction t) {}
            @Override public void onDelayAborted(Transaction t, GossipStore.ConflictResult c) {}
            @Override public void onDelayProgress(long r, long total, AdaptiveDelayCalculator.RiskLevel risk) {}
        });

        assertEquals(TransactionPhase.FAILED, tx.getPhase());
    }
}
