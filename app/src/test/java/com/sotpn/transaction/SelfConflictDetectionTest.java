package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * SELF-CONFLICT DETECTION TEST
 * Verifies that the GossipStore detects double-spending even if the 
 * sender device ID is the same for both transactions.
 */
public class SelfConflictDetectionTest {

    private GossipStore store;

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    // -----------------------------------------------------------------------
    // TEST 1: Same Sender, Different TXs
    // A malicious sender tries to spend the same token twice.
    // The GossipStore MUST NOT ignore the second message just because the 
    // sender ID matches.
    // -----------------------------------------------------------------------
    @Test
    public void testGossip_SameSenderDifferentTx_DetectsConflict() {
        String tokenId = "tok_123";
        String senderId = "malicious_device";
        long now = System.currentTimeMillis();

        // 1. First transaction
        GossipMessage msg1 = new GossipMessage(tokenId, senderId, "tx_alpha", now, 0);
        GossipStore.ConflictResult res1 = store.addGossip(msg1);
        assertFalse("First sighting should not be a conflict", res1.isConflict);

        // 2. Second transaction (Same token, Same sender, DIFFERENT TXID)
        GossipMessage msg2 = new GossipMessage(tokenId, senderId, "tx_beta", now + 100, 0);
        GossipStore.ConflictResult res2 = store.addGossip(msg2);

        // This will FAIL if the deduplication logic is too broad (tokenId + senderId)
        assertTrue("CRITICAL: Must detect conflict even if sender is the same", res2.isConflict);
        assertEquals(tokenId, res2.tokenId);
    }
}
