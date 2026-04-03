package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
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
 * 
 * CLOCK SYNCHRONIZATION & DRIFT TESTS
 * Verifies protocol stability when peers have divergent but "legal" clocks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClockSynchronizationTest {

    private ValidationPhaseHandler receiverHandler;
    private MockWallet senderWallet;
    private MockWallet receiverWallet;
    private GossipStore gossipStore;

    @Before
    public void setUp() {
        senderWallet = new MockWallet();
        receiverWallet = new MockWallet();
        gossipStore = new GossipStore();
        
        com.sotpn.communication.GossipEngine engine = new com.sotpn.communication.GossipEngine(
                mock(BleManager.class), mock(WifiDirectManager.class), gossipStore, mock(GossipEngine.GossipListener.class), "rec");
        
        receiverHandler = new ValidationPhaseHandler(receiverWallet, new NonceStore(), engine);
    }

    // -----------------------------------------------------------------------
    // TEST 1: Relative Drift Gossip Acceptance
    // A peer is 4 seconds ahead (legal skew). We receive their gossip.
    // Our store should accept it even if the timestamp is "in our future".
    // -----------------------------------------------------------------------
    @Test
    public void testDrift_FutureGossip_IsAccepted() {
        long futureTime = System.currentTimeMillis() + 4000; // 4s ahead
        GossipMessage futureMsg = new GossipMessage("tok_1", "peer_ahead", "tx_1", futureTime, 0);
        
        gossipStore.addGossip(futureMsg);
        
        assertTrue("Store must accept gossip from peers slightly ahead in time", 
                   gossipStore.hasSeenToken("tok_1"));
    }

    // -----------------------------------------------------------------------
    // TEST 2: Maximum Drift Transaction
    // Sender is 4s behind, Receiver is 4s ahead. Total drift = 8s.
    // Transaction sent at 59s (near expiry) should still be handled safely.
    // -----------------------------------------------------------------------
    @Test
    public void testDrift_MaxLegalDrift_ValidationBehavior() {
        // Sender thinks it is T=0. Receiver thinks it is T=8.
        long now = System.currentTimeMillis();
        long senderTime = now - 4000;
        
        // Sender signs at their T=59 (Receiver's T=67)
        long txTimestamp = senderTime - 59000; 
        
        Transaction tx = new Transaction("tx_drift", "tok_drift", senderWallet.getPublicKey(), receiverWallet.getPublicKey(),
                                         txTimestamp, "nonce_drift", "");
        // Sign it with senderWallet
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(senderWallet.signTransaction(data));

        final ValidationResult[] result = {null};
        receiverHandler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction tx) {}
            @Override public void onValidationFailed(Transaction tx, ValidationResult r) { result[0] = r; }
        });
        ShadowLooper.idleMainLooper();

        // The system correctly identifies this as expired because from the 
        // Receiver's perspective, the age is > 60s.
        assertNotNull("Validation should have failed due to expiry", result[0]);
        assertEquals(ValidationResult.FailureCode.TOKEN_EXPIRED, result[0].getFailureCode());
    }
}
