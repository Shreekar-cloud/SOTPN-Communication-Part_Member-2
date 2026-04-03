package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * NETWORK PARTITION RECOVERY TESTS
 * Verifies that the mesh network can detect double-spends after two 
 * isolated groups of devices reunite and exchange gossip.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NetworkPartitionRecoveryTest {

    private GossipStore storeGroupA;
    private GossipStore storeGroupB;
    private String tokenId = "tok_partition_test";

    @Before
    public void setUp() {
        storeGroupA = new GossipStore();
        storeGroupB = new GossipStore();
    }

    // -----------------------------------------------------------------------
    // TEST: Partition Reconciliation
    // Group A sees TX_1. Group B sees TX_2.
    // When they merge, they must detect the conflict.
    // -----------------------------------------------------------------------
    @Test
    public void testReconciliation_MergedPartitions_DetectsHistoricalConflict() {
        // 1. Group A records sighting 1
        GossipMessage msg1 = new GossipMessage(tokenId, "device_A", "tx_1", System.currentTimeMillis(), 0);
        storeGroupA.addGossip(msg1);

        // 2. Group B records sighting 2 (The Double Spend)
        GossipMessage msg2 = new GossipMessage(tokenId, "device_B", "tx_2", System.currentTimeMillis(), 0);
        storeGroupB.addGossip(msg2);

        // At this point, neither store sees a conflict
        assertFalse(storeGroupA.checkConflict(tokenId).isConflict);
        assertFalse(storeGroupB.checkConflict(tokenId).isConflict);

        // 3. Network Merges: Group A receives gossip from Group B
        // This simulates a device moving from Group B to Group A and relaying the message
        GossipStore.ConflictResult result = storeGroupA.addGossip(msg2);

        // 4. CRITICAL CHECK: Conflict must be detected retroactively
        assertTrue("Conflict must be detected after network partitions reunite", result.isConflict);
        assertEquals("tx_1", result.txId1);
        assertEquals("tx_2", result.txId2);
    }
}
