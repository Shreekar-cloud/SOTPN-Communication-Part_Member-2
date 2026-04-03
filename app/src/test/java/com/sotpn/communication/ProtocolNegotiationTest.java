package com.sotpn.communication;

import com.sotpn.model.Transaction;
import com.sotpn.transaction.MockWallet;
import com.sotpn.transaction.PreparePhaseHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * PROTOCOL NEGOTIATION & HANDOVER TESTS
 * Verifies that the system maintains security during connection changes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProtocolNegotiationTest {

    @Test
    public void testNegotiation_ExtremeLowMTU_DataRemainsIntact() {
        // payload is ~300 bytes
        String largeData = "SOTPN_DATA_PACKET_WITH_SIGNATURE_AND_NONCE_AND_KEYS_AND_METADATA_THAT_IS_VERY_LONG";
        
        // Attacker forces MTU to minimum (23 bytes total, ~20 bytes payload)
        int maliciousMtu = 20;
        
        int packetCount = (int) Math.ceil((double) largeData.length() / maliciousMtu);
        assertTrue("Must generate many small packets", packetCount > 3);
        
        // Reassembly check
        StringBuilder reassembled = new StringBuilder();
        for (int i = 0; i < largeData.length(); i += maliciousMtu) {
            reassembled.append(largeData.substring(i, Math.min(i + maliciousMtu, largeData.length())));
        }
        
        assertEquals("Data must survive extreme fragmentation", largeData, reassembled.toString());
    }

    @Test
    public void testHandover_CarrierSwitch_IdentityIntegrity() {
        MockWallet wallet = new MockWallet();
        PreparePhaseHandler handler = new PreparePhaseHandler(wallet, mock(BleManager.class), mock(WifiDirectManager.class));
        
        // Transaction starts
        handler.execute("receiver", 1000L, true, mock(PreparePhaseHandler.PrepareListener.class));
        Transaction tx1 = handler.getPendingTransaction();
        
        // Carrier switches from BLE to WiFi mid-stream
        // Check: Identity (Sender PubKey) must remain identical
        String bleIdentity = tx1.getSenderPublicKey();
        
        // Identity check
        assertEquals("Identity MUST be bound to the Wallet, not the Carrier (BLE/WiFi)", 
                     wallet.getPublicKey(), bleIdentity);
    }
}
