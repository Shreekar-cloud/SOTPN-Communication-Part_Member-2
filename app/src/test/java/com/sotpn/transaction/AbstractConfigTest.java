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
 */
public class AbstractConfigTest {

    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
    }

    @Test
    public void test1_walletInterface_allMethodsCallable() {
        assertNotNull(wallet.getPublicKey());
        assertTrue(wallet.getBalance() >= 0);
        assertNotNull(wallet.generateNonce());

        String sig = wallet.signTransaction("test_data");
        assertNotNull(sig);
        assertFalse(sig.isEmpty());

        assertNotNull(wallet.getSpendableToken(1000L));

        String data = "verify_test_data";
        String signature = wallet.signTransaction(data);
        String publicKey = wallet.getPublicKey();
        boolean valid = wallet.verifySignature(data, signature, publicKey);
        assertTrue(valid);
    }

    @Test
    public void test2_prepareListener_bothCallbacksWork() {
        final boolean[] sentFired   = {false};
        final boolean[] failedFired = {false};

        PreparePhaseHandler.PrepareListener listener = new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) { sentFired[0] = true; }
            @Override public void onPrepareFailed(String reason) { failedFired[0] = true; }
        };

        listener.onPrepareSent(new Transaction("tx_001", "tok_001", "s", "r", System.currentTimeMillis(), "n", "sig"));
        assertTrue(sentFired[0]);

        listener.onPrepareFailed("test failure");
        assertTrue(failedFired[0]);
    }

    @Test
    public void test3_validationListener_bothCallbacksWork() {
        final boolean[] passedFired = {false};
        final boolean[] failedFired = {false};

        ValidationPhaseHandler.ValidationListener listener = new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction tx) { passedFired[0] = true; }
            @Override public void onValidationFailed(Transaction tx, ValidationResult result) { failedFired[0] = true; }
        };

        Transaction tx = new Transaction("tx_001", "tok_001", "s", "r", System.currentTimeMillis(), "n", "sig");
        listener.onValidationPassed(tx);
        assertTrue(passedFired[0]);

        listener.onValidationFailed(tx, ValidationResult.fail(ValidationResult.FailureCode.INVALID_SIGNATURE, "test"));
        assertTrue(failedFired[0]);
    }

    @Test
    public void test4_commitListener_allThreeCallbacksWork() {
        final boolean[] receiverFired = {false};
        final boolean[] senderFired   = {false};
        final boolean[] failedFired   = {false};

        CommitPhaseHandler.CommitListener listener = new CommitPhaseHandler.CommitListener() {
            @Override public void onReceiverCommitComplete(TransactionProof proof) { receiverFired[0] = true; }
            @Override public void onSenderCommitComplete(TransactionProof proof) { senderFired[0] = true; }
            @Override public void onCommitFailed(String txId, String reason) { failedFired[0] = true; }
        };

        TransactionProof proof = new TransactionProof("tx_001", "tok_001", "s", "r", System.currentTimeMillis(), "n", "sig", "ack", System.currentTimeMillis(), TransactionProof.Role.SENDER);
        listener.onReceiverCommitComplete(proof);
        assertTrue(receiverFired[0]);

        listener.onSenderCommitComplete(proof);
        assertTrue(senderFired[0]);

        listener.onCommitFailed("tx_001", "test failure");
        assertTrue(failedFired[0]);
    }

    @Test
    public void test7_transactionPhase_allStatesExist() {
        TransactionPhase[] phases = TransactionPhase.values();
        // SOTPN Current Implementation has 6 phases: PREPARE, VALIDATING, DELAYING, COMMITTING, FINALIZED, FAILED
        assertEquals("Protocol must have exactly 6 phases", 6, phases.length);
        
        assertNotNull(TransactionPhase.PREPARE);
        assertNotNull(TransactionPhase.VALIDATING);
        assertNotNull(TransactionPhase.DELAYING);
        assertNotNull(TransactionPhase.COMMITTING);
        assertNotNull(TransactionPhase.FINALIZED);
        assertNotNull(TransactionPhase.FAILED);
    }

    @Test
    public void test8_validationFailureCodes_exist() {
        ValidationResult.FailureCode[] codes = ValidationResult.FailureCode.values();
        // Current implementation has 8 failure codes
        assertTrue("Must have at least 6 security codes", codes.length >= 6);
        assertNotNull(ValidationResult.FailureCode.INVALID_SIGNATURE);
        assertNotNull(ValidationResult.FailureCode.TOKEN_EXPIRED);
        assertNotNull(ValidationResult.FailureCode.NONCE_REPLAY);
        assertNotNull(ValidationResult.FailureCode.GOSSIP_CONFLICT);
    }
}
