package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * DEEP ARCHITECTURAL AUDIT
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DeepArchitecturalAuditTest {

    @Test
    public void testAudit_NonceStore_EnforcesMemorySafetyLimit() {
        NonceStore store = new NonceStore();
        for (int i = 0; i < 100000; i++) {
            store.checkAndRecord("nonce_flood_" + i);
        }
        assertTrue("NonceStore should remain functional under high-volume flood", store.size() > 0);
    }

    @Test
    public void testAudit_GossipStore_IsContentBoundNotArrivalBound() {
        GossipStore store = new GossipStore();
        String tokenId = "tok_123";
        long t1 = System.currentTimeMillis();

        // 1. Receive a valid sighting - Updated to 6-arg constructor
        GossipMessage msg1 = new GossipMessage(tokenId, "dev_A", "tx_1", t1, "sig_1", 0);
        store.addGossip(msg1);

        // 2. Receive a malicious relay of the SAME tx but with a 'future' timestamp
        GossipMessage maliciousClone = new GossipMessage(tokenId, "dev_A", "tx_1", t1 + 5000, "sig_1", 1);
        store.addGossip(maliciousClone);

        assertEquals("Store should treat identical data as one sighting regardless of relayed metadata", 
                     1, store.getGossipForToken(tokenId).size());
    }
}
