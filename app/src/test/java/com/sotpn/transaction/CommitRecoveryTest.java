package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
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
 * 
 * COMMIT RECOVERY & IDEMPOTENCY TESTS
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommitRecoveryTest {

    private MockWallet senderWallet;
    private MockWallet receiverWallet;
    private CommitPhaseHandler receiverCommit;
    private ValidationPhaseHandler validationHandler;
    private NonceStore nonceStore;
    private Transaction tx;

    @Before
    public void setUp() {
        senderWallet = new MockWallet();
        receiverWallet = new MockWallet();
        nonceStore = new NonceStore();
        GossipStore gs = new GossipStore();
        GossipEngine ge = new GossipEngine(mock(BleManager.class), mock(WifiDirectManager.class), 
                                           gs, mock(GossipEngine.GossipListener.class), receiverWallet.getPublicKey());
        
        receiverCommit = new CommitPhaseHandler(receiverWallet, mock(BleManager.class), mock(WifiDirectManager.class));
        validationHandler = new ValidationPhaseHandler(receiverWallet, nonceStore, ge);
        
        tx = new Transaction("tx_id_unique", "tok_1", senderWallet.getPublicKey(), receiverWallet.getPublicKey(), 
                             System.currentTimeMillis(), "nonce_1", "");
        // Sign it correctly
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(senderWallet.signTransaction(data));
    }

    // -----------------------------------------------------------------------
    // TEST 1: Receiver Idempotency
    // -----------------------------------------------------------------------
    @Test
    public void testIdempotency_DuplicateTransactionReceived_DoesNotDoubleCredit() {
        // 1. Process first time correctly (Validation must happen to record nonce)
        validationHandler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction tx) {}
            @Override public void onValidationFailed(Transaction tx, ValidationResult r) { fail("First validation failed"); }
        });
        ShadowLooper.idleMainLooper();
        
        receiverCommit.executeReceiverCommit(tx, true, mock(CommitPhaseHandler.CommitListener.class));
        assertTrue("Wallet should receive token once", receiverWallet.tokenWasReceived);
        receiverWallet.tokenWasReceived = false; 

        // 2. Sender "Retries" because they didn't get the ACK
        final ValidationResult[] result = {null};
        validationHandler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction tx) {}
            @Override public void onValidationFailed(Transaction tx, ValidationResult r) { result[0] = r; }
        });
        ShadowLooper.idleMainLooper();

        assertNotNull("Receiver must reject the retry", result[0]);
        assertEquals("Must fail as Nonce Replay (Idempotency)", 
                     ValidationResult.FailureCode.NONCE_REPLAY, result[0].getFailureCode());
        assertFalse("Wallet must NOT receive token a second time", receiverWallet.tokenWasReceived);
    }

    @Test
    public void testPersistence_NonceStore_IsInMemoryOnly() {
        nonceStore.checkAndRecord("nonce_to_save");
        NonceStore newInstance = new NonceStore();
        assertFalse("Currently, NonceStore does not persist across restarts",
                    newInstance.hasSeenNonce("nonce_to_save"));
    }
}
