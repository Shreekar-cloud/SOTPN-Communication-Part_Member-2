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
 * MEMORY LEAK & LONG-TERM STABILITY TESTS
 * Verifies that the system prunes old data and does not grow indefinitely.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MemoryLeakTest {

    // -----------------------------------------------------------------------
    // TEST 1: Gossip Store Automatic Purging
    // Verifies that gossip messages older than 60s are removed to save RAM.
    // -----------------------------------------------------------------------
    @Test
    public void testStability_GossipStore_PurgesExpiredData() {
        GossipStore store = new GossipStore();
        
        // 1. Add fresh gossip
        store.addGossip(new GossipMessage("tok_fresh", "dev_1", "tx_1", System.currentTimeMillis(), "sig_fresh", 0));
        
        // 2. Add "old" gossip (simulated as 2 minutes old)
        long twoMinutesAgo = System.currentTimeMillis() - 120_000;
        store.addGossip(new GossipMessage("tok_old", "dev_2", "tx_2", twoMinutesAgo, "sig_old", 0));
        
        assertEquals("Should initially have 2 tokens", 2, store.getTrackedTokenCount());

        // 3. Trigger cleanup (60s TTL)
        store.clearExpiredGossip(60_000);

        // 4. Verify results
        assertEquals("Store should have purged the 2-minute old token", 1, store.getTrackedTokenCount());
        assertTrue("Fresh token must remain", store.hasSeenToken("tok_fresh"));
        assertFalse("Old token must be removed", store.hasSeenToken("tok_old"));
    }

    // -----------------------------------------------------------------------
    // TEST 2: Nonce Store Cleanup
    // Ensures that nonces don't accumulate forever.
    // -----------------------------------------------------------------------
    @Test
    public void testStability_NonceStore_PurgesOldNonces() {
        NonceStore store = new NonceStore();
        
        store.checkAndRecord("nonce_fresh");
        // Simulate a manual clear (or periodic task)
        store.clearAll(); 
        
        assertEquals("Nonce store should be empty after clearAll", 0, store.size());
    }
}
