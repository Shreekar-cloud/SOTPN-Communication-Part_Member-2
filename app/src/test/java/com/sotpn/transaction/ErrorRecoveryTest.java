package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ErrorRecoveryTest {

    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_recovery", 10_000L,
                System.currentTimeMillis() + 60_000);
    }

    @Test
    public void test1_phase1Fails_walletBalanceUnchanged() {
        long balanceBefore = wallet.getBalance();
        wallet.tokenToReturn = null;

        PreparePhaseHandler handler = new PreparePhaseHandler(wallet, null, null);
        final String[] failure = {null};
        handler.execute("receiver", 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) {}
                    @Override public void onPrepareFailed(String r) { failure[0] = r; }
                });

        ShadowLooper.idleMainLooper();
        assertNotNull("Phase 1 must fail", failure[0]);
        assertEquals("Balance must be unchanged", balanceBefore, wallet.getBalance());
    }

    @Test
    public void test2_phase1Fails_tokenNotLocked() {
        wallet.lockShouldSucceed = false;

        PreparePhaseHandler handler = new PreparePhaseHandler(wallet, null, null);
        final String[] failure = {null};
        handler.execute("receiver", 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) {}
                    @Override public void onPrepareFailed(String r) { failure[0] = r; }
                });

        ShadowLooper.idleMainLooper();
        assertNotNull("Must fail when lock fails", failure[0]);
        assertFalse("Token must NOT be locked", wallet.isTokenLocked("tok_recovery"));
    }

    @Test
    public void test3_phase2Fails_tokenUnlocked() {
        PreparePhaseHandler prepare = new PreparePhaseHandler(wallet, null, null);
        final Transaction[] sentTx = {null};
        prepare.execute("receiver", 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                    @Override public void onPrepareFailed(String r) {}
                });

        ShadowLooper.idleMainLooper();
        assertNotNull("Phase 1 must succeed", sentTx[0]);
        assertTrue("Token must be locked", wallet.tokenWasLocked);

        prepare.abort();
        assertTrue("Token must be unlocked after abort", wallet.tokenWasUnlocked);
        assertFalse("Token must not be in locked map", wallet.isTokenLocked("tok_recovery"));
    }

    @Test
    public void test4_fullAbort_systemInCleanState() {
        PreparePhaseHandler handler = new PreparePhaseHandler(wallet, null, null);

        final Transaction[] sentTx = {null};
        handler.execute("receiver", 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                    @Override public void onPrepareFailed(String r) {}
                });

        ShadowLooper.idleMainLooper();
        assertNotNull("Tx must be sent", sentTx[0]);
        assertTrue("Token locked", wallet.tokenWasLocked);

        handler.abort();

        assertTrue("Token unlocked", wallet.tokenWasUnlocked);
        assertFalse("Token not in locked map", wallet.isTokenLocked("tok_recovery"));
        assertNull("No pending transaction after abort", handler.getPendingTransaction());
        assertFalse("Token not spent", wallet.tokenWasSpent);
    }

    @Test
    public void test5_phase4AckMismatch_tokenNotSpent() {
        Transaction tx = new Transaction(
                "tx_recovery", "tok_recovery",
                wallet.getPublicKey(), "receiver",
                System.currentTimeMillis(), "nonce_recovery", "sig");

        com.sotpn.model.TransactionAck wrongAck = new com.sotpn.model.TransactionAck(
                "tx_WRONG_ID", "tok_recovery",
                "receiver", System.currentTimeMillis(),
                "ack_sig", true);

        CommitPhaseHandler commit = new CommitPhaseHandler(wallet, null, null);
        final String[] failure = {null};
        commit.executeSenderCommit(tx, wrongAck,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {
                        failure[0] = r;
                    }
                });

        assertNotNull("ACK mismatch must fail", failure[0]);
        assertFalse("Token must NOT be marked spent", wallet.tokenWasSpent);
    }
}
