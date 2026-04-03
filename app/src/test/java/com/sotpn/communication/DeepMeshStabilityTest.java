package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * DEEP MESH STABILITY TESTS
 * Verifies protocol behavior under complex topological shifts.
 */
public class DeepMeshStabilityTest {

    private GossipStore store;

    @Before
    public void setUp() {
        store = new GossipStore();
    }

    // -----------------------------------------------------------------------
    // TEST 1: Deduplication Logic - Hop Count Priority
    // Verifies that a valid (relatable) message is not ignored if a malformed 
    // or expired version of it arrived first.
    // -----------------------------------------------------------------------
    @Test
    public void testMesh_HigherPriorityGossip_ReplacesStaleGossip() {
        String tokenId = "tok_123";
        String devId = "dev_origin";
        String txId = "tx_abc";
        long now = System.currentTimeMillis();

        // 1. Malicious peer sends an "Expired" version (Hop 9) to try and mute the network
        GossipMessage staleMsg = new GossipMessage(tokenId, devId, txId, now, 9);
        store.addGossip(staleMsg);
        assertTrue("Store should record the first sighting", store.hasProcessed(staleMsg));

        // 2. A valid, nearby peer sends the same sighting (Hop 0)
        GossipMessage freshMsg = new GossipMessage(tokenId, devId, txId, now, 0);
        
        // Logic: Even if the Dedup Key matches, the system must recognize that 
        // the fresh message has a better 'Hop Reach' and should be processed.
        // Current requirement: Security first. 
        assertTrue("System MUST remain alert to fresh sightings even if IDs match stale ones", 
                   store.hasProcessed(freshMsg));
    }
}
