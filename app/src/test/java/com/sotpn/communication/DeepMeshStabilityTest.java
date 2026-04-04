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
        // Corrected: Using the 6-argument constructor
        GossipMessage staleMsg = new GossipMessage(tokenId, devId, txId, now, "sig_stale", 9);
        store.addGossip(staleMsg);
        
        // Verification: The stale message is recorded as processed.
        assertTrue("Store should record the first sighting", store.hasProcessed(staleMsg));

        // 2. A valid, nearby peer sends the same sighting (Hop 0)
        GossipMessage freshMsg = new GossipMessage(tokenId, devId, txId, now, "sig_fresh", 0);
        
        // Logic: Even if the Dedup Key matches, the system must recognize that 
        // the fresh message has a better 'Hop Reach' and should NOT be marked as 
        // processed until it is actually added.
        assertFalse("System MUST NOT ignore fresh sightings even if IDs match stale ones", 
                   store.hasProcessed(freshMsg));
                   
        // 3. Add the fresh message
        store.addGossip(freshMsg);
        
        // NOW it should be marked as processed
        assertTrue("Now the higher priority gossip should be marked as processed",
                   store.hasProcessed(freshMsg));
                   
        // And the storage should contain the better sighting
        assertTrue("Store must keep the fresh sighting for relaying", 
                   store.getGossipForToken(tokenId).contains(freshMsg));
    }
}
