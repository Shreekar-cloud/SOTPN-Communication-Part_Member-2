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
 * NETWORK PARTITION RECOVERY TESTS
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NetworkPartitionRecoveryTest {

    private GossipStore storeGroupA;
    private GossipStore storeGroupB;
    private String tokenId = "tok_partition_test";
    private static final String SIG = "sig_partition";

    @Before
    public void setUp() {
        storeGroupA = new GossipStore();
        storeGroupB = new GossipStore();
    }

    @Test
    public void testReconciliation_MergedPartitions_DetectsHistoricalConflict() {
        // 1. Group A records sighting 1
        GossipMessage msg1 = new GossipMessage(tokenId, "device_A", "tx_1", System.currentTimeMillis(), SIG, 0);
        storeGroupA.addGossip(msg1);

        // 2. Group B records sighting 2 (The Double Spend)
        GossipMessage msg2 = new GossipMessage(tokenId, "device_B", "tx_2", System.currentTimeMillis(), SIG, 0);
        storeGroupB.addGossip(msg2);

        // 3. Network Merges: Group A receives gossip from Group B
        GossipStore.ConflictResult result = storeGroupA.addGossip(msg2);

        // 4. CRITICAL CHECK: Conflict must be detected retroactively
        assertTrue("Conflict must be detected after network partitions reunite", result.isConflict);
        assertEquals("tx_1", result.txId1);
        assertEquals("tx_2", result.txId2);
    }
}
