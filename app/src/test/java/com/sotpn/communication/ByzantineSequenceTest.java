package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * BYZANTINE SEQUENCE & TIMESTAMP AUDIT
 * Verifies that the Gossip Store is immune to timestamp manipulation 
 * and sequence divergence attacks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ByzantineSequenceTest {

    private GossipStore store;

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    // -----------------------------------------------------------------------
    // TEST: Timestamp Inversion
    // Even if a "Newer" message arrives for the same transaction, 
    // the system must treat the underlying data as one sighting.
    // -----------------------------------------------------------------------
    @Test
    public void testByzantine_TimestampInversion_DoesNotCreateFalseConflict() {
        String tokenId = "tok_123";
        String senderId = "device_A";
        String txId = "tx_legit";
        long now = System.currentTimeMillis();

        // 1. Receive original message
        GossipMessage msg1 = new GossipMessage(tokenId, senderId, txId, now, 0);
        store.addGossip(msg1);

        // 2. Malicious peer re-broadcasts SAME TX with a "Future" timestamp
        // to try and trigger a false conflict or fill the store.
        GossipMessage forgedMsg = new GossipMessage(tokenId, senderId, txId, now + 10000, 1);
        GossipStore.ConflictResult result = store.addGossip(forgedMsg);

        // A secure store must recognize this is the SAME TX and NOT flag a conflict.
        assertFalse("Same TX with different timestamp MUST NOT be a conflict", result.isConflict);
        assertEquals("Store should only have 1 sighting for this TX", 1, store.getGossipForToken(tokenId).size());
    }
}
