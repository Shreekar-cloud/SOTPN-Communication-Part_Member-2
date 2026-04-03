package com.sotpn.transaction;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : AdaptiveDelayCalculatorTest.java
 * Package  : com.sotpn.transaction (test)
 * Step     : Step 6 - Adaptive Delay Calculator
 */
public class AdaptiveDelayCalculatorTest {

    private AdaptiveDelayCalculator calculator;

    @Before
    public void setUp() {
        calculator = new AdaptiveDelayCalculator();
    }

    @Test
    public void testIsolated_zeroPeers_returnsMaxDelay() {
        long delay = calculator.calculateDelayMs(0);
        assertEquals("0 peers should return 10 second delay",
                AdaptiveDelayCalculator.ISOLATED_DELAY_MS, delay);
    }

    @Test
    public void testIsolated_onePeer_returnsMaxDelay() {
        long delay = calculator.calculateDelayMs(1);
        assertEquals("1 peer should return 10 second delay",
                AdaptiveDelayCalculator.ISOLATED_DELAY_MS, delay);
    }

    @Test
    public void testModerate_twoPeers_returnsMediumDelay() {
        long delay = calculator.calculateDelayMs(2);
        assertEquals("2 peers should return 5 second delay",
                AdaptiveDelayCalculator.MODERATE_DELAY_MS, delay);
    }

    @Test
    public void testModerate_threePeers_returnsMediumDelay() {
        long delay = calculator.calculateDelayMs(3);
        assertEquals("3 peers should return 5 second delay",
                AdaptiveDelayCalculator.MODERATE_DELAY_MS, delay);
    }

    @Test
    public void testModerate_fivePeers_returnsMediumDelay() {
        long delay = calculator.calculateDelayMs(5);
        assertEquals("5 peers (= threshold, not above) should return 5 second delay",
                AdaptiveDelayCalculator.MODERATE_DELAY_MS, delay);
    }

    @Test
    public void testCrowded_sixPeers_returnsMinDelay() {
        long delay = calculator.calculateDelayMs(6);
        assertEquals("6 peers should return 3 second delay",
                AdaptiveDelayCalculator.CROWDED_DELAY_MS, delay);
    }

    @Test
    public void testCrowded_twentyPeers_returnsMinDelay() {
        long delay = calculator.calculateDelayMs(20);
        assertEquals("20 peers should return 3 second delay",
                AdaptiveDelayCalculator.CROWDED_DELAY_MS, delay);
    }

    @Test
    public void testRisk_zeroPeers_isHigh() {
        AdaptiveDelayCalculator.RiskLevel risk = calculator.getRiskLevel(0);
        assertEquals("0 peers = HIGH risk", AdaptiveDelayCalculator.RiskLevel.HIGH, risk);
    }

    @Test
    public void testRisk_onePeer_isHigh() {
        AdaptiveDelayCalculator.RiskLevel risk = calculator.getRiskLevel(1);
        assertEquals("1 peer = HIGH risk", AdaptiveDelayCalculator.RiskLevel.HIGH, risk);
    }

    @Test
    public void testRisk_threePeers_isMedium() {
        AdaptiveDelayCalculator.RiskLevel risk = calculator.getRiskLevel(3);
        assertEquals("3 peers = MEDIUM risk", AdaptiveDelayCalculator.RiskLevel.MEDIUM, risk);
    }

    @Test
    public void testRisk_sixPeers_isLow() {
        AdaptiveDelayCalculator.RiskLevel risk = calculator.getRiskLevel(6);
        assertEquals("6 peers = LOW risk", AdaptiveDelayCalculator.RiskLevel.LOW, risk);
    }

    @Test
    public void testBoundary_belowIsolatedThreshold() {
        assertTrue("0 peers should be ISOLATED delay",
                calculator.calculateDelayMs(0) == AdaptiveDelayCalculator.ISOLATED_DELAY_MS);
        assertTrue("1 peer should be ISOLATED delay",
                calculator.calculateDelayMs(1) == AdaptiveDelayCalculator.ISOLATED_DELAY_MS);
    }

    @Test
    public void testBoundary_aboveCrowdedThreshold() {
        assertTrue("6 peers should be CROWDED delay",
                calculator.calculateDelayMs(6) == AdaptiveDelayCalculator.CROWDED_DELAY_MS);
    }

    @Test
    public void testDelayValues_areWithinSotpnSpec() {
        for (int peers = 0; peers <= 20; peers++) {
            long delay = calculator.calculateDelayMs(peers);
            assertTrue("Delay should be >= 3000ms for peers=" + peers, delay >= 3_000);
            assertTrue("Delay should be <= 10000ms for peers=" + peers, delay <= 10_000);
        }
    }
}
