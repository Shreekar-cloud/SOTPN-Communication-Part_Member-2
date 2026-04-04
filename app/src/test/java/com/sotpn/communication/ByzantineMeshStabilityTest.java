package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 *
 * BYZANTINE MESH STABILITY TESTS
 */
public class ByzantineMeshStabilityTest {

    private GossipStore store;

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    // -----------------------------------------------------------------------
    // TEST 1: Hop-Priority Relay
    // Verifies that a valid (relatable) message is not ignored if a
    // malformed or expired version of it arrived first.
    // -----------------------------------------------------------------------
    @Test
    public void testMesh_FreshGossipOverridesStale_PreventsShadowing() {
        String tokenId = "tok_123";
        String devId = "dev_origin";
        String txId = "tx_abc";
        long now = System.currentTimeMillis();

        // 1. Receive 'Stale' gossip first (Hop Count 9)
        GossipMessage stale = new GossipMessage(tokenId, devId, txId, now, 9);
        store.addGossip(stale);

        // 2. Receive 'Fresh' gossip (Hop Count 0)
        GossipMessage fresh = new GossipMessage(tokenId, devId, txId, now, 0);

        // Logic: A secure mesh must treat 'fresh' as a relayable sighting
        // even if the IDs match the stale one.
        boolean isSightingUnique = store.addGossip(fresh).isConflict;

        // Requirement: The store must not have discarded 'fresh'
        assertTrue("Store must keep the fresh sighting for relaying",
                store.getGossipForToken(tokenId).contains(fresh));
    }
}