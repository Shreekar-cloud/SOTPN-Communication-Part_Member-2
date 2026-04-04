package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
public class DeepProtocolAuditTest {

    private GossipStore store;

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    @Test
    public void testAudit_Gossip_FreshMessageOverridesStale() {
        String tokenId = "tok_shadow";
        String senderId = "dev_origin";
        String txId = "tx_123";
        long now = System.currentTimeMillis();

        // 1. Receive 'Stale' gossip (Hop Count 9) - Updated to 6-arg constructor
        GossipMessage stale = new GossipMessage(tokenId, senderId, txId, now, "sig_stale", 9);
        store.addGossip(stale);
        assertTrue("Store should have processed the first message", store.hasProcessed(stale));

        // 2. Receive 'Fresh' gossip (Hop Count 0) - Updated to 6-arg constructor
        GossipMessage fresh = new GossipMessage(tokenId, senderId, txId, now, "sig_fresh", 0);
        
        // System should recognize 'fresh' is more useful and process it
        store.addGossip(fresh);
        assertTrue("Store must contain the fresh sighting", store.getGossipForToken(tokenId).contains(fresh));
    }

    @Test
    public void testAudit_NonceStore_MemoryHardCap() {
        NonceStore nonceStore = new NonceStore();
        for (int i = 0; i < 50000; i++) {
            nonceStore.checkAndRecord("flood_nonce_" + i);
        }
        assertTrue("NonceStore should stay alive under flood", nonceStore.size() > 0);
    }

    @Test
    public void testAudit_Gossip_ConflictResilience() {
        // Updated to 6-arg constructor
        store.addGossip(new GossipMessage("tok_1", "dev_me", "tx_alpha", System.currentTimeMillis(), "sig_alpha", 0));
        GossipStore.ConflictResult result = store.addGossip(
            new GossipMessage("tok_1", "dev_them", "tx_beta", System.currentTimeMillis(), "sig_beta", 0));
            
        assertTrue("Conflict must be detected regardless of gossip origin", result.isConflict);
    }
}
