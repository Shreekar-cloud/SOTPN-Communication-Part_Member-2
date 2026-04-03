package com.sotpn.communication;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.transaction.MockWallet;
import com.sotpn.transaction.TransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * CARRIER HANDOVER INTEGRITY TEST
 * Verifies that the cryptographic identity is preserved when a transaction 
 * switches between radio carriers (BLE <-> WiFi).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CarrierHandoverIntegrityTest {

    private TransactionManager manager;
    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        manager = new TransactionManager(RuntimeEnvironment.getApplication(), wallet, mock(TransactionManager.TransactionListener.class));
    }

    // -----------------------------------------------------------------------
    // TEST: Identity Binding across Handover
    // Verifies that an ACK arriving on WiFi must match the receiver key 
    // established during the BLE validation phase.
    // -----------------------------------------------------------------------
    @Test
    public void testHandover_AckOnDifferentRadio_MustMatchIdentity() {
        String originalReceiver = "pub_key_established_on_ble";
        Transaction tx = new Transaction("tx_handover", "tok_1", wallet.getPublicKey(), originalReceiver, 
                                         System.currentTimeMillis(), "nonce", "sig");

        // 1. Simulate BLE start
        manager.onTransactionReceived(tx, "MAC_BLE");
        
        // 2. Simulate radio switch: ACK arrives via WiFi from a DIFFERENT key
        TransactionAck maliciousAck = new TransactionAck("tx_handover", "tok_1", "pub_key_malicious", 
                                                         System.currentTimeMillis(), "sig", true);
        
        // The manager should ignore or reject the ACK because the identity changed
        manager.onAckReceived(maliciousAck);

        // Result: Transaction must not be finalized with a hijacked identity
        // Logic check: Transaction remains in its original phase or fails.
        assertNotEquals("CRITICAL: Identity hijacked during carrier handover!", 
                        com.sotpn.model.TransactionPhase.FINALIZED, tx.getPhase());
    }
}
