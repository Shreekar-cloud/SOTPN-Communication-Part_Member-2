package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : GossipNetworkTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * Tests gossip mesh behavior under different peer counts and conditions.
 */
public class GossipNetworkTest {

    private GossipStore             gossipStore;
    private AdaptiveDelayCalculator calculator;

    @Before
    public void setUp() {
        gossipStore = new GossipStore();
        calculator  = new AdaptiveDelayCalculator();
    }

    // -----------------------------------------------------------------------
    // TEST 1: 0 peers → delay = 10 seconds (ISOLATED)
    // -----------------------------------------------------------------------
    @Test
    public void test1_zeroPeers_tenSecondDelay() {
        long delay = calculator.calculateDelayMs(0);
        assertEquals("0 peers must give 10s delay",
                AdaptiveDelayCalculator.ISOLATED_DELAY_MS, delay);
        assertEquals("0 peers = HIGH risk",
                AdaptiveDelayCalculator.RiskLevel.HIGH,
                calculator.getRiskLevel(0));
        System.out.println("TEST 1 — 0 peers: delay=" + delay + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 2: 1 peer → delay = 10 seconds (still ISOLATED)
    // -----------------------------------------------------------------------
    @Test
    public void test2_onePeer_tenSecondDelay() {
        long delay = calculator.calculateDelayMs(1);
        assertEquals("1 peer must give 10s delay",
                AdaptiveDelayCalculator.ISOLATED_DELAY_MS, delay);
        System.out.println("TEST 2 — 1 peer: delay=" + delay + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 3: 2 peers → delay = 5 seconds (MODERATE)
    // -----------------------------------------------------------------------
    @Test
    public void test3_twoPeers_fiveSecondDelay() {
        long delay = calculator.calculateDelayMs(2);
        assertEquals("2 peers must give 5s delay",
                AdaptiveDelayCalculator.MODERATE_DELAY_MS, delay);
        assertEquals("2 peers = MEDIUM risk",
                AdaptiveDelayCalculator.RiskLevel.MEDIUM,
                calculator.getRiskLevel(2));
        System.out.println("TEST 3 — 2 peers: delay=" + delay + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 4: 5 peers → delay = 5 seconds (still MODERATE)
    // -----------------------------------------------------------------------
    @Test
    public void test4_fivePeers_fiveSecondDelay() {
        long delay = calculator.calculateDelayMs(5);
        assertEquals("5 peers must give 5s delay",
                AdaptiveDelayCalculator.MODERATE_DELAY_MS, delay);
        System.out.println("TEST 4 — 5 peers: delay=" + delay + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 5: 6 peers → delay = 3 seconds (CROWDED)
    // -----------------------------------------------------------------------
    @Test
    public void test5_sixPeers_threeSecondDelay() {
        long delay = calculator.calculateDelayMs(6);
        assertEquals("6 peers must give 3s delay",
                AdaptiveDelayCalculator.CROWDED_DELAY_MS, delay);
        assertEquals("6 peers = LOW risk",
                AdaptiveDelayCalculator.RiskLevel.LOW,
                calculator.getRiskLevel(6));
        System.out.println("TEST 5 — 6 peers: delay=" + delay + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 6: Gossip at hop 0 → should be relayed (not expired)
    // -----------------------------------------------------------------------
    @Test
    public void test6_gossipAtHop0_shouldBeRelayed() {
        GossipMessage msg = new GossipMessage(
                "tok_hop0", "device_A", "tx_001",
                System.currentTimeMillis(), "sig_001", 0);
        assertFalse("Hop 0 message should NOT be expired", msg.isExpired());
        GossipMessage relayed = msg.withIncrementedHop();
        assertEquals("Relayed hop should be 1", 1, relayed.getHopCount());
        System.out.println("TEST 6 — Hop 0 relayed ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 7: Gossip at hop 2 → should be relayed (below max)
    // -----------------------------------------------------------------------
    @Test
    public void test7_gossipAtHop2_shouldBeRelayed() {
        GossipMessage msg = new GossipMessage(
                "tok_hop2", "device_A", "tx_001",
                System.currentTimeMillis(), "sig_001", 2);
        assertFalse("Hop 2 should NOT be expired (max="
                + GossipMessage.MAX_HOPS + ")", msg.isExpired());
        System.out.println("TEST 7 — Hop 2 not expired ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 8: Gossip at MAX_HOPS → NOT relayed (expired)
    // -----------------------------------------------------------------------
    @Test
    public void test8_gossipAtMaxHops_notRelayed() {
        GossipMessage msg = new GossipMessage(
                "tok_maxhop", "device_A", "tx_001",
                System.currentTimeMillis(), "sig_001", GossipMessage.MAX_HOPS);
        assertTrue("Max hop message MUST be expired", msg.isExpired());
        System.out.println("TEST 8 — Max hop expired, not relayed ✅ (MAX="
                + GossipMessage.MAX_HOPS + ")");
    }

    // -----------------------------------------------------------------------
    // TEST 9: Same device gossip twice → deduplicated
    // -----------------------------------------------------------------------
    @Test
    public void test9_sameDeviceGossipTwice_deduplicated() {
        GossipMessage msg1 = new GossipMessage(
                "tok_dedup", "device_A", "tx_001",
                System.currentTimeMillis(), "sig_001", 0);
        GossipMessage msg2 = new GossipMessage(
                "tok_dedup", "device_A", "tx_001", // same sender + token
                System.currentTimeMillis(), "sig_001", 0);

        gossipStore.addGossip(msg1);
        gossipStore.addGossip(msg2);

        assertEquals("Same sender gossip should be deduplicated",
                1, gossipStore.getGossipForToken("tok_dedup").size());
        System.out.println("TEST 9 — Duplicate gossip deduplicated ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 10: Gossip flood 10,000 msgs → store not corrupted
    // -----------------------------------------------------------------------
    @Test
    public void test10_gossipFlood_storeNotCorrupted() {
        for (int i = 0; i < 10_000; i++) {
            gossipStore.addGossip(new GossipMessage(
                    "tok_flood_" + (i % 10), // 10 unique tokens
                    "device_" + i,
                    "tx_" + i,
                    System.currentTimeMillis(), "sig_" + i, 0));
        }

        // Store must still work correctly
        assertTrue("Store should have tokens after flood",
                gossipStore.getTrackedTokenCount() > 0);
        assertTrue("Token count must not exceed unique tokens",
                gossipStore.getTrackedTokenCount() <= 10);

        // Conflict detection must still work
        GossipStore.ConflictResult result =
                gossipStore.checkConflict("tok_flood_0");
        // tok_flood_0 has multiple tx → conflict
        assertTrue("Conflict must be detected after flood",
                result.isConflict);

        System.out.println("TEST 10 — Gossip flood survived ✅. Tokens: "
                + gossipStore.getTrackedTokenCount());
    }
}
