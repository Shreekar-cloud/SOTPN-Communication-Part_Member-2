package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TokenLifecycleTest {

    private MockWallet          wallet;
    private PreparePhaseHandler handler;

    private Transaction lastSentTx  = null;
    private String      lastFailure = null;

    @Before
    public void setUp() {
        wallet  = new MockWallet();
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_lifecycle", 10_000L,
                System.currentTimeMillis() + 60_000);
        handler = new PreparePhaseHandler(wallet, null, null);
        reset();
    }

    private void reset() {
        lastSentTx  = null;
        lastFailure = null;
    }

    private PreparePhaseHandler.PrepareListener listener() {
        return new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) { lastSentTx = tx; }
            @Override public void onPrepareFailed(String r)     { lastFailure = r; }
        };
    }

    @Test
    public void test1_token_lockedImmediatelyAfterPhase1() {
        handler.execute("receiver", 10_000L, true, listener());
        assertTrue("Token must be locked after Phase 1", wallet.tokenWasLocked);
        assertTrue("Token must be in locked map",
                wallet.isTokenLocked("tok_lifecycle"));
    }

    @Test
    public void test2_lockedToken_secondTransactionFails() {
        handler.execute("receiver_A", 10_000L, true, listener());
        assertTrue("First tx should lock token", wallet.tokenWasLocked);

        reset();

        handler.execute("receiver_B", 10_000L, true, listener());
        assertNotNull("Second tx on locked token should fail", lastFailure);
    }

    @Test
    public void test3_abortUnlocks_thirdTransactionSucceeds() {
        handler.execute("receiver_A", 10_000L, true, listener());
        assertTrue("First tx locks token", wallet.tokenWasLocked);

        handler.abort();
        assertTrue("Abort must unlock token", wallet.tokenWasUnlocked);
        assertFalse("Token must be unlocked in map",
                wallet.isTokenLocked("tok_lifecycle"));

        reset();
        wallet.tokenWasLocked   = false;
        wallet.tokenWasUnlocked = false;

        PreparePhaseHandler newHandler =
                new PreparePhaseHandler(wallet, null, null);
        newHandler.execute("receiver_C", 10_000L, true, listener());
        assertTrue("Third tx should succeed after unlock", wallet.tokenWasLocked);
        assertNull("No failure expected", lastFailure);
    }

    @Test
    public void test4_spentToken_cannotBeReused() {
        MockWallet receiver = new MockWallet();
        NonceStore ns = new NonceStore();
        com.sotpn.communication.GossipStore gs =
                new com.sotpn.communication.GossipStore();

        com.sotpn.communication.GossipEngine ge =
                new com.sotpn.communication.GossipEngine(
                        null, null, gs,
                        new com.sotpn.communication.GossipEngine.GossipListener() {
                            @Override public void onConflictDetected(
                                    com.sotpn.communication.GossipStore.ConflictResult r) {}
                            @Override public void onGossipReceived(
                                    com.sotpn.communication.GossipMessage m) {}
                        }, receiver.getPublicKey());

        ValidationPhaseHandler validate =
                new ValidationPhaseHandler(receiver, ns, ge);
        CommitPhaseHandler recCommit =
                new CommitPhaseHandler(receiver, null, null);
        CommitPhaseHandler sndCommit =
                new CommitPhaseHandler(wallet, null, null);

        handler.execute(receiver.getPublicKey(), 10_000L, true, listener());
        assertNotNull("Transaction should be sent", lastSentTx);

        final Transaction[] validTx = {null};
        validate.execute(lastSentTx,
                new ValidationPhaseHandler.ValidationListener() {
                    @Override public void onValidationPassed(Transaction tx) { validTx[0] = tx; }
                    @Override public void onValidationFailed(Transaction tx, ValidationResult r) {}
                });

        assertNotNull("Transaction should validate", validTx[0]);

        recCommit.executeReceiverCommit(validTx[0], true,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        String ackData = "Received:" + validTx[0].getTxId() + ":" + validTx[0].getTokenId();
        String ackSig = receiver.signTransaction(ackData);

        com.sotpn.model.TransactionAck ack = new com.sotpn.model.TransactionAck(
                validTx[0].getTxId(), validTx[0].getTokenId(),
                receiver.getPublicKey(), System.currentTimeMillis(),
                ackSig, true);

        sndCommit.executeSenderCommit(lastSentTx, ack,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        assertTrue("Token should be marked spent", wallet.tokenWasSpent);
    }

    @Test
    public void test5_expiredToken_rejectedBeforeLocking() {
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_expired_lifecycle", 10_000L,
                System.currentTimeMillis() - 5_000);

        handler.execute("receiver", 10_000L, true, listener());

        assertNotNull("Expired token should be rejected", lastFailure);
        assertFalse("Expired token must NOT be locked", wallet.tokenWasLocked);
    }

    @Test
    public void test6_tokenValidForOneSecond_accepted() {
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_1sec", 10_000L,
                System.currentTimeMillis() + 1_000);

        handler.execute("receiver", 10_000L, true, listener());

        assertTrue("Token valid for 1s should be accepted", wallet.tokenWasLocked);
        assertNull("No failure for valid token", lastFailure);
    }
}
