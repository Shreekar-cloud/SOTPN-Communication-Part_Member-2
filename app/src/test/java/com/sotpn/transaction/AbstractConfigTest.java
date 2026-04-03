package com.sotpn.transaction;

import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : AbstractConfigTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/transaction/
 *   Right-click → Run 'AbstractConfigTest'
 *   No phone needed.
 *
 * -----------------------------------------------------------------------
 * Tests abstract class and interface configurations:
 *
 *   TEST 1  — WalletInterface contract — all methods callable
 *   TEST 2  — PrepareListener interface — both callbacks work
 *   TEST 3  — ValidationListener interface — both callbacks work
 *   TEST 4  — CommitListener interface — all 3 callbacks work
 *   TEST 5  — DelayListener interface — all 3 callbacks work
 *   TEST 6  — GossipListener interface — both callbacks work
 *   TEST 7  — TransactionPhase enum — all 6 states exist
 *   TEST 8  — ValidationResult.FailureCode — all 7 codes exist
 *   TEST 9  — TransactionProof.Role — both roles exist
 *   TEST 10 — WalletInterface.TokenInfo — isValid() logic correct
 */
public class AbstractConfigTest {

    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
    }

    // -----------------------------------------------------------------------
    // TEST 1: WalletInterface contract — all methods are callable
    // -----------------------------------------------------------------------

    @Test
    public void test1_walletInterface_allMethodsCallable() {
        assertNotNull("getPublicKey() must not be null",   wallet.getPublicKey());
        assertTrue("getBalance() must return >= 0",        wallet.getBalance() >= 0);
        assertNotNull("generateNonce() must not be null",  wallet.generateNonce());

        String sig = wallet.signTransaction("test_data");
        assertNotNull("signTransaction() must not be null", sig);
        assertFalse("signTransaction() must not be empty",  sig.isEmpty());

        assertNotNull("getSpendableToken() must not be null",
                wallet.getSpendableToken(1000L));

        // verifySignature — sign first then verify with correct key
        String data      = "verify_test_data";
        String signature = wallet.signTransaction(data);
        String publicKey = wallet.getPublicKey();
        boolean valid    = wallet.verifySignature(data, signature, publicKey);
        assertTrue("verifySignature() must return true for own signature", valid);

        System.out.println("TEST 1 — WalletInterface all methods callable ✅");
    }
    // -----------------------------------------------------------------------
    // TEST 2: PrepareListener — both callbacks are invocable
    // -----------------------------------------------------------------------
    @Test
    public void test2_prepareListener_bothCallbacksWork() {
        final boolean[] sentFired   = {false};
        final boolean[] failedFired = {false};

        PreparePhaseHandler.PrepareListener listener =
                new PreparePhaseHandler.PrepareListener() {
                    @Override
                    public void onPrepareSent(Transaction tx) {
                        sentFired[0] = true;
                    }
                    @Override
                    public void onPrepareFailed(String reason) {
                        failedFired[0] = true;
                    }
                };

        // Trigger onPrepareSent
        listener.onPrepareSent(new Transaction(
                "tx_001", "tok_001", "s", "r",
                System.currentTimeMillis(), "n", "sig"));
        assertTrue("onPrepareSent must fire", sentFired[0]);

        // Trigger onPrepareFailed
        listener.onPrepareFailed("test failure");
        assertTrue("onPrepareFailed must fire", failedFired[0]);

        System.out.println("TEST 2 — PrepareListener both callbacks work ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 3: ValidationListener — both callbacks are invocable
    // -----------------------------------------------------------------------
    @Test
    public void test3_validationListener_bothCallbacksWork() {
        final boolean[] passedFired = {false};
        final boolean[] failedFired = {false};

        ValidationPhaseHandler.ValidationListener listener =
                new ValidationPhaseHandler.ValidationListener() {
                    @Override
                    public void onValidationPassed(Transaction tx) {
                        passedFired[0] = true;
                    }
                    @Override
                    public void onValidationFailed(Transaction tx,
                                                   ValidationResult result) {
                        failedFired[0] = true;
                    }
                };

        Transaction tx = new Transaction("tx_001", "tok_001", "s", "r",
                System.currentTimeMillis(), "n", "sig");

        listener.onValidationPassed(tx);
        assertTrue("onValidationPassed must fire", passedFired[0]);

        listener.onValidationFailed(tx,
                ValidationResult.fail(
                        ValidationResult.FailureCode.INVALID_SIGNATURE, "test"));
        assertTrue("onValidationFailed must fire", failedFired[0]);

        System.out.println("TEST 3 — ValidationListener both callbacks work ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 4: CommitListener — all 3 callbacks are invocable
    // -----------------------------------------------------------------------
    @Test
    public void test4_commitListener_allThreeCallbacksWork() {
        final boolean[] receiverFired = {false};
        final boolean[] senderFired   = {false};
        final boolean[] failedFired   = {false};

        CommitPhaseHandler.CommitListener listener =
                new CommitPhaseHandler.CommitListener() {
                    @Override
                    public void onReceiverCommitComplete(TransactionProof proof) {
                        receiverFired[0] = true;
                    }
                    @Override
                    public void onSenderCommitComplete(TransactionProof proof) {
                        senderFired[0] = true;
                    }
                    @Override
                    public void onCommitFailed(String txId, String reason) {
                        failedFired[0] = true;
                    }
                };

        TransactionProof proof = new TransactionProof(
                "tx_001", "tok_001", "s", "r",
                System.currentTimeMillis(), "n", "sig", "ack",
                System.currentTimeMillis(), TransactionProof.Role.SENDER);

        listener.onReceiverCommitComplete(proof);
        assertTrue("onReceiverCommitComplete must fire", receiverFired[0]);

        listener.onSenderCommitComplete(proof);
        assertTrue("onSenderCommitComplete must fire", senderFired[0]);

        listener.onCommitFailed("tx_001", "test failure");
        assertTrue("onCommitFailed must fire", failedFired[0]);

        System.out.println("TEST 4 — CommitListener all 3 callbacks work ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 5: DelayListener — all 3 callbacks are invocable
    // -----------------------------------------------------------------------
    @Test
    public void test5_delayListener_allThreeCallbacksWork() {
        final boolean[] completeFired  = {false};
        final boolean[] abortedFired   = {false};
        final boolean[] progressFired  = {false};

        AdaptiveDelayHandler.DelayListener listener =
                new AdaptiveDelayHandler.DelayListener() {
                    @Override
                    public void onDelayComplete(Transaction tx) {
                        completeFired[0] = true;
                    }
                    @Override
                    public void onDelayAborted(Transaction tx,
                                               GossipStore.ConflictResult conflict) {
                        abortedFired[0] = true;
                    }
                    @Override
                    public void onDelayProgress(long remainingMs, long totalMs,
                                                AdaptiveDelayCalculator.RiskLevel risk) {
                        progressFired[0] = true;
                    }
                };

        Transaction tx = new Transaction("tx_001", "tok_001", "s", "r",
                System.currentTimeMillis(), "n", "sig");

        listener.onDelayComplete(tx);
        assertTrue("onDelayComplete must fire", completeFired[0]);

        listener.onDelayAborted(tx, GossipStore.ConflictResult.NO_CONFLICT);
        assertTrue("onDelayAborted must fire", abortedFired[0]);

        listener.onDelayProgress(5000, 10000,
                AdaptiveDelayCalculator.RiskLevel.MEDIUM);
        assertTrue("onDelayProgress must fire", progressFired[0]);

        System.out.println("TEST 5 — DelayListener all 3 callbacks work ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 6: GossipListener — both callbacks are invocable
    // -----------------------------------------------------------------------
    @Test
    public void test6_gossipListener_bothCallbacksWork() {
        final boolean[] conflictFired = {false};
        final boolean[] gossipFired   = {false};

        com.sotpn.communication.GossipEngine.GossipListener listener =
                new com.sotpn.communication.GossipEngine.GossipListener() {
                    @Override
                    public void onConflictDetected(GossipStore.ConflictResult result) {
                        conflictFired[0] = true;
                    }
                    @Override
                    public void onGossipReceived(
                            com.sotpn.communication.GossipMessage message) {
                        gossipFired[0] = true;
                    }
                };

        listener.onConflictDetected(new GossipStore.ConflictResult(
                true, "tok", "tx1", "tx2", "dev1", "dev2"));
        assertTrue("onConflictDetected must fire", conflictFired[0]);

        listener.onGossipReceived(new com.sotpn.communication.GossipMessage(
                "tok", "dev", "tx", System.currentTimeMillis(), 0));
        assertTrue("onGossipReceived must fire", gossipFired[0]);

        System.out.println("TEST 6 — GossipListener both callbacks work ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 7: TransactionPhase enum — all 6 states exist
    // -----------------------------------------------------------------------
    @Test
    public void test7_transactionPhase_allStatesExist() {
        TransactionPhase[] phases = TransactionPhase.values();

        // Your enum has 9 values
        assertTrue("Should have at least 6 phases", phases.length >= 6);

        // Verify the critical states exist
        assertNotNull(TransactionPhase.PREPARE);
        assertNotNull(TransactionPhase.VALIDATING);
        assertNotNull(TransactionPhase.DELAYING);
        assertNotNull(TransactionPhase.COMMITTING);
        assertNotNull(TransactionPhase.FINALIZED);
        assertNotNull(TransactionPhase.FAILED);

        System.out.println("TEST 7 — TransactionPhase has "
                + phases.length + " states ✅");
        for (TransactionPhase p : phases) {
            System.out.println("         Phase: " + p.name());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 8: ValidationResult.FailureCode — all 7 codes exist
    // -----------------------------------------------------------------------
    @Test
    public void test8_failureCode_allSevenCodesExist() {
        ValidationResult.FailureCode[] codes =
                ValidationResult.FailureCode.values();

        assertTrue("Should have at least 7 failure codes", codes.length >= 7);

        assertNotNull(ValidationResult.FailureCode.NONE);
        assertNotNull(ValidationResult.FailureCode.INVALID_SIGNATURE);
        assertNotNull(ValidationResult.FailureCode.TOKEN_EXPIRED);
        assertNotNull(ValidationResult.FailureCode.NONCE_REPLAY);
        assertNotNull(ValidationResult.FailureCode.GOSSIP_CONFLICT);
        assertNotNull(ValidationResult.FailureCode.MISSING_FIELDS);
        assertNotNull(ValidationResult.FailureCode.UNKNOWN);

        System.out.println("TEST 8 — All " + codes.length
                + " FailureCodes exist ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 9: TransactionProof.Role — both roles exist and are different
    // -----------------------------------------------------------------------
    @Test
    public void test9_transactionProofRole_bothRolesExist() {
        TransactionProof.Role[] roles = TransactionProof.Role.values();

        assertEquals("Should have exactly 2 roles", 2, roles.length);
        assertNotNull(TransactionProof.Role.SENDER);
        assertNotNull(TransactionProof.Role.RECEIVER);
        assertNotEquals("SENDER and RECEIVER must be different",
                TransactionProof.Role.SENDER, TransactionProof.Role.RECEIVER);

        System.out.println("TEST 9 — Both TransactionProof.Role values exist ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 10: WalletInterface.TokenInfo — isValid() logic is correct
    // -----------------------------------------------------------------------
    @Test
    public void test10_tokenInfo_isValidLogicCorrect() {
        // Valid token — expires in 60 seconds
        WalletInterface.TokenInfo validToken = new WalletInterface.TokenInfo(
                "tok_valid", 50_000L,
                System.currentTimeMillis() + 60_000);
        assertTrue("Token expiring in 60s must be valid", validToken.isValid());

        // Expired token — expired 1 second ago
        WalletInterface.TokenInfo expiredToken = new WalletInterface.TokenInfo(
                "tok_expired", 50_000L,
                System.currentTimeMillis() - 1_000);
        assertFalse("Token expired 1s ago must be invalid",
                expiredToken.isValid());

        // Token expiring right now (edge case)
        WalletInterface.TokenInfo edgeToken = new WalletInterface.TokenInfo(
                "tok_edge", 50_000L,
                System.currentTimeMillis() - 1);
        assertFalse("Token just expired must be invalid", edgeToken.isValid());

        // Token with zero value
        WalletInterface.TokenInfo zeroToken = new WalletInterface.TokenInfo(
                "tok_zero", 0L,
                System.currentTimeMillis() + 60_000);
        assertTrue("Zero-value token can be valid (validity = time only)",
                zeroToken.isValid());

        System.out.println("TEST 10 — TokenInfo.isValid() logic correct ✅");
    }
}