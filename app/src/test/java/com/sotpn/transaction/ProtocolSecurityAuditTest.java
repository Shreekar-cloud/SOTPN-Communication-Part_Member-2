package com.sotpn.transaction;

import android.content.Context;
import com.sotpn.communication.GossipMessage;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * CRITICAL PROTOCOL SECURITY AUDIT
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProtocolSecurityAuditTest {

    private TransactionManager transactionManager;
    private MockWallet wallet;
    private Transaction tx;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        Context context = RuntimeEnvironment.getApplication();
        TransactionManager.TransactionListener listener = mock(TransactionManager.TransactionListener.class);
        transactionManager = new TransactionManager(context, wallet, listener);
        
        tx = new Transaction("tx_1", "tok_1", wallet.getPublicKey(), "receiver_key", 
                             System.currentTimeMillis(), "nonce_1", "");
        // Sign it correctly to pass Phase 2
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(wallet.signTransaction(data));
    }

    // -----------------------------------------------------------------------
    // TEST 1: Mid-Delay Conflict Termination
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_GossipConflictDuringDelay_AbortsTransaction() {
        // 1. Start incoming transaction
        transactionManager.onTransactionReceived(tx);
        ShadowLooper.idleMainLooper();
        
        // 2. Verify it is in Phase 3 (DELAYING)
        assertEquals(TransactionPhase.DELAYING, tx.getPhase());

        // 3. Inject a conflict via Gossip Message mid-way (T+2s)
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        
        // Create a malicious gossip message for the SAME token but DIFFERENT transaction
        GossipMessage maliciousGossip = new GossipMessage(
                "tok_1", "sender_evil", "tx_evil", System.currentTimeMillis(), 0);
        
        // FIXED: Use the public callback onGossipReceived instead of private method
        transactionManager.onGossipReceived(maliciousGossip.toWireString());
        ShadowLooper.idleMainLooper();

        // 4. CRITICAL CHECK: Did the transaction abort?
        assertEquals("Transaction MUST be FAILED after mid-delay conflict", 
                     TransactionPhase.FAILED, tx.getPhase());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Multi-Path Collision Detection
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_InterRadioCollision_IsTreatedAsDoubleSpend() {
        // 1. Start WiFi transaction
        transactionManager.onTransactionReceived(tx);
        ShadowLooper.idleMainLooper();

        // 2. Second sender tries to send the SAME token via BLE simultaneously
        Transaction txConflict = new Transaction("tx_evil", "tok_1", "sender_evil", wallet.getPublicKey(),
                                                 System.currentTimeMillis(), "nonce_evil", "sig");
        
        // This simulates the BLE callback firing for the same token
        transactionManager.onTransactionReceived(txConflict, "MAC_BLE");
        ShadowLooper.idleMainLooper();

        // 3. System should have detected the conflict locally
        assertEquals("System must detect local inter-radio collision as a failure", 
                     TransactionPhase.FAILED, tx.getPhase());
    }
}
