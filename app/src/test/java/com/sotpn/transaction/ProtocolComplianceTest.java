package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : ProtocolComplianceTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * Verifies all SOTPN spec constants are exactly correct.
 * These are the numbers that were agreed in the architecture document.
 */
public class ProtocolComplianceTest {

    // -----------------------------------------------------------------------
    // TEST 1: Max transaction = exactly ₹500 (50,000 paise)
    // -----------------------------------------------------------------------
    @Test
    public void test1_maxTransaction_exactlyFiveHundredRupees() {
        assertEquals("Max transaction must be exactly ₹500",
                50_000L, PreparePhaseHandler.MAX_TRANSACTION_PAISE);
        System.out.println("TEST 1 — Max transaction = ₹"
                + PreparePhaseHandler.MAX_TRANSACTION_PAISE / 100 + " ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 2: Max wallet = exactly ₹2000 (200,000 paise)
    // -----------------------------------------------------------------------
    @Test
    public void test2_maxWallet_exactlyTwoThousandRupees() {
        assertEquals("Max wallet must be exactly ₹2000",
                200_000L, PreparePhaseHandler.MAX_WALLET_PAISE);
        System.out.println("TEST 2 — Max wallet = ₹"
                + PreparePhaseHandler.MAX_WALLET_PAISE / 100 + " ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 3: Gossip max hops = exactly 3
    // -----------------------------------------------------------------------
    @Test
    public void test3_gossipMaxHops_exactlyThree() {
        assertEquals("Gossip max hops must be exactly 3",
                3, GossipMessage.MAX_HOPS);
        System.out.println("TEST 3 — Gossip max hops = "
                + GossipMessage.MAX_HOPS + " ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 4: Crowded delay = exactly 3 seconds
    // -----------------------------------------------------------------------
    @Test
    public void test4_crowdedDelay_exactlyThreeSeconds() {
        assertEquals("Crowded delay must be exactly 3000ms",
                3_000L, AdaptiveDelayCalculator.CROWDED_DELAY_MS);
        System.out.println("TEST 4 — Crowded delay = "
                + AdaptiveDelayCalculator.CROWDED_DELAY_MS + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 5: Moderate delay = exactly 5 seconds
    // -----------------------------------------------------------------------
    @Test
    public void test5_moderateDelay_exactlyFiveSeconds() {
        assertEquals("Moderate delay must be exactly 5000ms",
                5_000L, AdaptiveDelayCalculator.MODERATE_DELAY_MS);
        System.out.println("TEST 5 — Moderate delay = "
                + AdaptiveDelayCalculator.MODERATE_DELAY_MS + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 6: Isolated delay = exactly 10 seconds
    // -----------------------------------------------------------------------
    @Test
    public void test6_isolatedDelay_exactlyTenSeconds() {
        assertEquals("Isolated delay must be exactly 10000ms",
                10_000L, AdaptiveDelayCalculator.ISOLATED_DELAY_MS);
        System.out.println("TEST 6 — Isolated delay = "
                + AdaptiveDelayCalculator.ISOLATED_DELAY_MS + "ms ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 7: Delay range always within 3–10 seconds
    // -----------------------------------------------------------------------
    @Test
    public void test7_delayRange_alwaysWithinSpec() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        for (int peers = 0; peers <= 100; peers++) {
            long delay = calc.calculateDelayMs(peers);
            assertTrue("Delay must be >= 3000ms for peers=" + peers,
                    delay >= 3_000);
            assertTrue("Delay must be <= 10000ms for peers=" + peers,
                    delay <= 10_000);
        }
        System.out.println("TEST 7 — All delays in 3–10s range ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 8: Crowded threshold = more than 5 devices
    // -----------------------------------------------------------------------
    @Test
    public void test8_crowdedThreshold_moreThanFiveDevices() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        // 5 devices = NOT crowded
        assertEquals("5 peers must NOT be crowded (moderate)",
                AdaptiveDelayCalculator.MODERATE_DELAY_MS,
                calc.calculateDelayMs(5));
        // 6 devices = crowded
        assertEquals("6 peers MUST be crowded",
                AdaptiveDelayCalculator.CROWDED_DELAY_MS,
                calc.calculateDelayMs(6));
        System.out.println("TEST 8 — Crowded threshold at 6 devices ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 9: Isolated threshold = less than 2 devices
    // -----------------------------------------------------------------------
    @Test
    public void test9_isolatedThreshold_lessThanTwoDevices() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        // 2 devices = NOT isolated (moderate)
        assertEquals("2 peers must NOT be isolated (moderate)",
                AdaptiveDelayCalculator.MODERATE_DELAY_MS,
                calc.calculateDelayMs(2));
        // 1 device = isolated
        assertEquals("1 peer MUST be isolated",
                AdaptiveDelayCalculator.ISOLATED_DELAY_MS,
                calc.calculateDelayMs(1));
        System.out.println("TEST 9 — Isolated threshold below 2 devices ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 10: All 3 risk levels exist and are distinct
    // -----------------------------------------------------------------------
    @Test
    public void test10_riskLevels_allThreeExistAndDistinct() {
        AdaptiveDelayCalculator.RiskLevel low    = AdaptiveDelayCalculator.RiskLevel.LOW;
        AdaptiveDelayCalculator.RiskLevel medium = AdaptiveDelayCalculator.RiskLevel.MEDIUM;
        AdaptiveDelayCalculator.RiskLevel high   = AdaptiveDelayCalculator.RiskLevel.HIGH;

        assertNotNull(low);
        assertNotNull(medium);
        assertNotNull(high);
        assertNotEquals("LOW != MEDIUM", low, medium);
        assertNotEquals("MEDIUM != HIGH", medium, high);
        assertNotEquals("LOW != HIGH",    low, high);

        System.out.println("TEST 10 — All 3 risk levels distinct ✅");
    }
}