package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
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
    private static final String SIG       = "sig_mock";
    private static final long   NOW       = System.currentTimeMillis();

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    @Test
    public void testSingleSighting_noConflict() {
        GossipMessage msg = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0);
        GossipStore.ConflictResult result = store.addGossip(msg);
        assertFalse("Single sighting should NOT be a conflict", result.isConflict);
    }

    @Test
    public void testSameTokenSameTx_differentDevice_isConflict() {
        GossipMessage msg1 = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0);
        GossipMessage msg2 = new GossipMessage(TOKEN_A, DEVICE_2, TX_1, NOW, SIG, 0);
        store.addGossip(msg1);
        GossipStore.ConflictResult result = store.addGossip(msg2);
        // SOTPN Logic: Multiple devices claiming the SAME TxId is Identity Spoofing.
        assertTrue("Same token same tx from different devices MUST be a conflict",
                result.isConflict);
    }

    @Test
    public void testSameToken_differentTx_conflictDetected() {
        GossipMessage msg1 = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0);
        GossipMessage msg2 = new GossipMessage(TOKEN_A, DEVICE_2, TX_2, NOW, SIG, 0);
        store.addGossip(msg1);
        GossipStore.ConflictResult result = store.addGossip(msg2);
        assertTrue("Same token in two different txs MUST be a conflict",
                result.isConflict);
    }

    @Test
    public void testConflictResult_containsCorrectTokenId() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0));
        GossipStore.ConflictResult result =
                store.addGossip(new GossipMessage(TOKEN_A, DEVICE_2, TX_2, NOW, SIG, 0));
        assertEquals("Conflict must reference correct tokenId",
                TOKEN_A, result.tokenId);
    }

    @Test
    public void testConflictResult_containsBothTxIds() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0));
        GossipStore.ConflictResult result =
                store.addGossip(new GossipMessage(TOKEN_A, DEVICE_2, TX_2, NOW, SIG, 0));
        assertTrue("Conflict must contain TX_1",
                TX_1.equals(result.txId1) || TX_1.equals(result.txId2));
        assertTrue("Conflict must contain TX_2",
                TX_2.equals(result.txId1) || TX_2.equals(result.txId2));
    }

    @Test
    public void testDuplicateGossip_ignored() {
        GossipMessage msg = new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0);
        store.addGossip(msg);
        store.addGossip(msg); 
        assertEquals("Duplicate gossip should not be stored twice",
                1, store.getGossipForToken(TOKEN_A).size());
    }

    @Test
    public void testDifferentTokens_trackedIndependently() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0));
        store.addGossip(new GossipMessage(TOKEN_B, DEVICE_1, TX_2, NOW, SIG, 0));
        assertTrue("TOKEN_A should be tracked", store.hasSeenToken(TOKEN_A));
        assertTrue("TOKEN_B should be tracked", store.hasSeenToken(TOKEN_B));
        assertEquals("TOKEN_A should have 1 sighting",
                1, store.getGossipForToken(TOKEN_A).size());
        assertEquals("TOKEN_B should have 1 sighting",
                1, store.getGossipForToken(TOKEN_B).size());
    }

    @Test
    public void testHasSeenToken_unknownToken_returnsFalse() {
        assertFalse("Unknown token should return false",
                store.hasSeenToken("tok_unknown"));
    }

    @Test
    public void testClearAll_wipesEverything() {
        store.addGossip(new GossipMessage(TOKEN_A, DEVICE_1, TX_1, NOW, SIG, 0));
        store.addGossip(new GossipMessage(TOKEN_B, DEVICE_2, TX_2, NOW, SIG, 0));
        store.clearAll();
        assertFalse("TOKEN_A should be gone after clearAll",
                store.hasSeenToken(TOKEN_A));
        assertFalse("TOKEN_B should be gone after clearAll",
                store.hasSeenToken(TOKEN_B));
        assertEquals("Store should be empty after clearAll",
                0, store.getTrackedTokenCount());
    }

    @Test
    public void testCheckConflict_unseenToken_returnsNoConflict() {
        GossipStore.ConflictResult result = store.checkConflict("tok_never_seen");
        assertFalse("Unseen token should have no conflict", result.isConflict);
    }

    @Test
    public void testClearExpired_removesOldGossip() {
        long oldTime = System.currentTimeMillis() - 200_000; 
        GossipMessage old = new GossipMessage(
                "tok_old", "device_A", "tx_old", oldTime, SIG, 0);
        store.addGossip(old);
        store.clearExpiredGossip(120_000); 
        assertFalse("Old gossip should be cleared",
                store.hasSeenToken("tok_old"));
    }
}
