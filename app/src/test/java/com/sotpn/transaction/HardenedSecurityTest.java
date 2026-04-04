package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * HARDENED SECURITY & DoS PROTECTION TESTS
 * Verifies that the system is resilient against advanced network attacks.
 */
public class HardenedSecurityTest {

    private GossipStore store;

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    // -----------------------------------------------------------------------
    // TEST 1: Gossip Protocol Fuzzing
    // Verifies that extremely malformed or over-sized wire strings 
    // do not crash the parser.
    // -----------------------------------------------------------------------
    @Test
    public void testFuzzing_GossipParser_IsRobust() {
        String[] payloads = {
            "TOKEN_SEEN:::::::::", // Excessive separators
            "TOKEN_SEEN:tok:dev:tx:abc:0", // Non-numeric timestamp
            "TOKEN_SEEN:tok:dev:tx:123:NaN", // Non-numeric hop
            "", // Empty
            "GIBBERISH_DATA_1234567890",
            "TOKEN_SEEN:" + new String(new char[1000]).replace('\0', 'A') // Buffer overflow attempt
        };

        for (String p : payloads) {
            try {
                GossipMessage.fromWireString(p);
            } catch (Exception e) {
                fail("Gossip parser CRASHED on payload: " + p);
            }
        }
    }

    // -----------------------------------------------------------------------
    // TEST 2: Deduplication Cache Integrity
    // Verifies that the internal dedup cache is purged correctly during TTL cleanup.
    // -----------------------------------------------------------------------
    @Test
    public void testStability_DedupCache_IsPurged() {
        // Updated to use hardened 6-argument constructor
        GossipMessage msg = new GossipMessage("tok_1", "dev_1", "tx_1", System.currentTimeMillis() - 200000, "sig_1", 0);
        store.addGossip(msg);
        
        assertTrue("Pre-condition: Msg should be processed", store.hasProcessed(msg));
        
        // Trigger cleanup (cutoff 60s)
        store.clearExpiredGossip(60000);
        
        // After cleanup, the old message should be removed from the dedup cache
        // to allow future valid transactions with the same IDs (after sync).
        assertFalse("Old messages must be cleared from dedup cache to prevent memory leaks", store.hasProcessed(msg));
    }
}
