package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : GossipStoreTest.java
 * Package  : com.sotpn.communication (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/communication/
 *   Right-click → Run 'GossipStoreTest'
 *   No phone needed.
 */
public class GossipStoreTest {

    private GossipStore store;

    // Shared test data
    private static final String TOKEN_A   = "tok_aaa";
    private static final String TOKEN_B   = "tok_bbb";
    private static final String DEVICE_1  = "device_001";
    private static final String DEVICE_2  = "device_002";
    private static final String TX_1      = "tx_111";
    private static final String TX_2      = "tx_222";
    private static final long   NOW       = System.currentTimeMillis();

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    // -----------------------------------------------------------------------
    // Test 1: Single sighting — no conflict
    // -----------------------------------------------------------------------
    @Test
    public void testSingleSighting_noConflict() {
        GossipMessage msg = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0);
        GossipStore.ConflictResult result = store.addGossip(msg);
        assertFalse("Single sighting should NOT be a conflict", result.isConflict);
    }

    // -----------------------------------------------------------------------
    // Test 2: Same token, same tx, different device — no conflict
    // -----------------------------------------------------------------------
    @Test
    public void testSameTokenSameTx_differentDevice_noConflict() {
        GossipMessage msg1 = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0);
        GossipMessage msg2 = new GossipMessage(TOKEN_A, DEVICE_2, TX_1, NOW, 0);
        store.addGossip(msg1);
        GossipStore.ConflictResult result = store.addGossip(msg2);
        assertFalse("Same token same tx from different devices is NOT a conflict",
                result.isConflict);
    }

    // -----------------------------------------------------------------------
    // Test 3: Same token, DIFFERENT tx — CONFLICT detected
    // -----------------------------------------------------------------------
    @Test
    public void testSameToken_differentTx_conflictDetected() {
        GossipMessage msg1 = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0);
        GossipMessage msg2 = new GossipMessage(TOKEN_A, DEVICE_2, TX_2, NOW, 0);
        store.addGossip(msg1);
        GossipStore.ConflictResult result = store.addGossip(msg2);
        assertTrue("Same token in two different txs MUST be a conflict",
                result.isConflict);
    }

    // -----------------------------------------------------------------------
    // Test 4: Conflict result contains correct token ID
    // -----------------------------------------------------------------------
    @Test
    public void testConflictResult_containsCorrectTokenId() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0));
        GossipStore.ConflictResult result =
                store.addGossip(new GossipMessage(TOKEN_A, DEVICE_2, TX_2, NOW, 0));
        assertEquals("Conflict must reference correct tokenId",
                TOKEN_A, result.tokenId);
    }

    // -----------------------------------------------------------------------
    // Test 5: Conflict result contains both tx IDs
    // -----------------------------------------------------------------------
    @Test
    public void testConflictResult_containsBothTxIds() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0));
        GossipStore.ConflictResult result =
                store.addGossip(new GossipMessage(TOKEN_A, DEVICE_2, TX_2, NOW, 0));
        assertTrue("Conflict must contain TX_1",
                TX_1.equals(result.txId1) || TX_1.equals(result.txId2));
        assertTrue("Conflict must contain TX_2",
                TX_2.equals(result.txId1) || TX_2.equals(result.txId2));
    }

    // -----------------------------------------------------------------------
    // Test 6: Duplicate gossip is ignored (same device + same token)
    // -----------------------------------------------------------------------
    @Test
    public void testDuplicateGossip_ignored() {
        GossipMessage msg = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0);
        store.addGossip(msg);
        store.addGossip(msg); // add same message again
        assertEquals("Duplicate gossip should not be stored twice",
                1, store.getGossipForToken(TOKEN_A).size());
    }

    // -----------------------------------------------------------------------
    // Test 7: Different tokens tracked independently
    // -----------------------------------------------------------------------
    @Test
    public void testDifferentTokens_trackedIndependently() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0));
        store.addGossip(new GossipMessage(TOKEN_B, DEVICE_1, TX_2, NOW, 0));
        assertTrue("TOKEN_A should be tracked", store.hasSeenToken(TOKEN_A));
        assertTrue("TOKEN_B should be tracked", store.hasSeenToken(TOKEN_B));
        assertEquals("TOKEN_A should have 1 sighting",
                1, store.getGossipForToken(TOKEN_A).size());
        assertEquals("TOKEN_B should have 1 sighting",
                1, store.getGossipForToken(TOKEN_B).size());
    }

    // -----------------------------------------------------------------------
    // Test 8: hasSeenToken returns false for unknown token
    // -----------------------------------------------------------------------
    @Test
    public void testHasSeenToken_unknownToken_returnsFalse() {
        assertFalse("Unknown token should return false",
                store.hasSeenToken("tok_unknown"));
    }

    // -----------------------------------------------------------------------
    // Test 9: clearAll wipes everything
    // -----------------------------------------------------------------------
    @Test
    public void testClearAll_wipesEverything() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, 0));
        store.addGossip(new GossipMessage(TOKEN_B, DEVICE_2, TX_2, NOW, 0));
        store.clearAll();
        assertFalse("TOKEN_A should be gone after clearAll",
                store.hasSeenToken(TOKEN_A));
        assertFalse("TOKEN_B should be gone after clearAll",
                store.hasSeenToken(TOKEN_B));
        assertEquals("Store should be empty after clearAll",
                0, store.getTrackedTokenCount());
    }

    // -----------------------------------------------------------------------
    // Test 10: checkConflict on unseen token returns no conflict
    // -----------------------------------------------------------------------
    @Test
    public void testCheckConflict_unseenToken_returnsNoConflict() {
        GossipStore.ConflictResult result = store.checkConflict("tok_never_seen");
        assertFalse("Unseen token should have no conflict", result.isConflict);
    }
    @Test
    public void testClearExpired_removesOldGossip() {
        long oldTime = System.currentTimeMillis() - 200_000; // 3 min ago
        GossipMessage old = new GossipMessage(
                "tok_old", "device_A", "tx_old", oldTime, 0);
        store.addGossip(old);
        store.clearExpiredGossip(120_000); // clear older than 2 min
        assertFalse("Old gossip should be cleared",
                store.hasSeenToken("tok_old"));
    }
}