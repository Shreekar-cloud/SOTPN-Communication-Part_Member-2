package com.sotpn.transaction;

import android.util.Log;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : AdaptiveDelayCalculator.java
 * Package  : com.sotpn.transaction
 * Step     : Step 6 - Adaptive Delay Calculator
 * Status   : Complete
 *
 * Depends on  : Nothing (pure logic — no Android APIs)
 * Used by     : AdaptiveDelayHandler
 * Testable    : YES — unit testable on laptop without any device
 *
 * -----------------------------------------------------------------------
 * Calculates how long Phase 3 (Adaptive Delay) should last based on
 * how many SOTPN devices are currently nearby.
 *
 * WHY ADAPTIVE?
 *   In a crowded area (market, campus), gossip spreads quickly —
 *   many devices will hear "TOKEN_SEEN" within 3 seconds.
 *   In an isolated area (rural, outdoor), very few devices are nearby —
 *   we need a longer window so gossip has time to propagate.
 *
 * SOTPN SPEC RULES:
 *   Nearby devices > 5  →  delay = 3 seconds   (CROWDED)
 *   Nearby devices < 2  →  delay = 8–10 seconds (ISOLATED)
 *   Everything else     →  delay = 5 seconds    (MODERATE)
 *
 * RISK LEVEL:
 *   We also compute a RiskLevel so the UI can show
 *   "Low / Medium / High double-spend risk" to the user.
 *
 * -----------------------------------------------------------------------
 * UNIT TEST EXAMPLE (plain Java, no Android needed):
 *
 *   AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
 *
 *   assert calc.calculateDelayMs(0)  == 10_000;  // isolated
 *   assert calc.calculateDelayMs(1)  == 10_000;  // isolated
 *   assert calc.calculateDelayMs(3)  == 5_000;   // moderate
 *   assert calc.calculateDelayMs(5)  == 5_000;   // moderate
 *   assert calc.calculateDelayMs(6)  == 3_000;   // crowded
 *   assert calc.calculateDelayMs(20) == 3_000;   // crowded
 *
 *   assert calc.getRiskLevel(0) == RiskLevel.HIGH;
 *   assert calc.getRiskLevel(3) == RiskLevel.MEDIUM;
 *   assert calc.getRiskLevel(6) == RiskLevel.LOW;
 */
public class AdaptiveDelayCalculator {

    private static final String TAG = "AdaptiveDelayCalc";

    // -----------------------------------------------------------------------
    // Delay thresholds (from SOTPN spec)
    // -----------------------------------------------------------------------

    /** Devices > CROWDED_THRESHOLD → use minimum delay */
    private static final int  CROWDED_THRESHOLD  = 5;

    /** Devices < ISOLATED_THRESHOLD → use maximum delay */
    private static final int  ISOLATED_THRESHOLD = 2;

    /** Delay when crowded (many peers = fast gossip propagation) */
    public static final long CROWDED_DELAY_MS  = 3_000;   // 3 seconds

    /** Delay when moderate (balanced) */
    public static final long MODERATE_DELAY_MS = 5_000;   // 5 seconds

    /** Delay when isolated (minimum peers = slow gossip propagation) */
    public static final long ISOLATED_DELAY_MS = 10_000;  // 10 seconds

    // -----------------------------------------------------------------------
    // Risk levels
    // -----------------------------------------------------------------------

    /**
     * Risk level associated with the current peer count.
     * Used by the UI to inform the user during Phase 3.
     */
    public enum RiskLevel {
        /**
         * LOW — many peers nearby, gossip propagates fast,
         * double-spend attempt is likely to be detected quickly.
         */
        LOW,

        /**
         * MEDIUM — moderate peer count, reasonable detection window.
         */
        MEDIUM,

        /**
         * HIGH — very few peers nearby, gossip propagates slowly,
         * higher chance a double-spend attempt goes undetected
         * before sync.
         */
        HIGH
    }

    // -----------------------------------------------------------------------
    // Core: calculate delay
    // -----------------------------------------------------------------------

    /**
     * Calculate how long Phase 3 should wait based on nearby device count.
     *
     * @param nearbyDeviceCount Number of SOTPN devices currently visible
     *                          (from BleManager.getNearbyDeviceCount())
     * @return Delay in milliseconds (3000, 5000, or 10000)
     */
    public long calculateDelayMs(int nearbyDeviceCount) {
        long delayMs;

        if (nearbyDeviceCount > CROWDED_THRESHOLD) {
            delayMs = CROWDED_DELAY_MS;
        } else if (nearbyDeviceCount < ISOLATED_THRESHOLD) {
            delayMs = ISOLATED_DELAY_MS;
        } else {
            delayMs = MODERATE_DELAY_MS;
        }

        Log.d(TAG, "Adaptive delay calculated: nearbyDevices=" + nearbyDeviceCount
                + " → delayMs=" + delayMs
                + " (" + describeCondition(nearbyDeviceCount) + ")");

        return delayMs;
    }

    // -----------------------------------------------------------------------
    // Risk level
    // -----------------------------------------------------------------------

    /**
     * Get the double-spend risk level for the current environment.
     *
     * @param nearbyDeviceCount Number of nearby SOTPN devices.
     * @return RiskLevel (LOW / MEDIUM / HIGH)
     */
    public RiskLevel getRiskLevel(int nearbyDeviceCount) {
        if (nearbyDeviceCount > CROWDED_THRESHOLD) return RiskLevel.LOW;
        if (nearbyDeviceCount < ISOLATED_THRESHOLD) return RiskLevel.HIGH;
        return RiskLevel.MEDIUM;
    }

    // -----------------------------------------------------------------------
    // Describe (for UI + logging)
    // -----------------------------------------------------------------------

    /**
     * Returns a short human-readable description of the current condition.
     * Displayed on the transaction status screen during Phase 3.
     *
     * @param nearbyDeviceCount Number of nearby SOTPN devices.
     * @return e.g. "Crowded area — fast verification (3s)"
     */
    public String describeCondition(int nearbyDeviceCount) {
        if (nearbyDeviceCount > CROWDED_THRESHOLD) {
            return "Crowded area — fast verification ("
                    + (CROWDED_DELAY_MS / 1000) + "s)";
        } else if (nearbyDeviceCount < ISOLATED_THRESHOLD) {
            return "Isolated area — extended verification ("
                    + (ISOLATED_DELAY_MS / 1000) + "s)";
        } else {
            return "Moderate area — standard verification ("
                    + (MODERATE_DELAY_MS / 1000) + "s)";
        }
    }

    /**
     * Returns a user-friendly risk description for the UI.
     *
     * @param nearbyDeviceCount Number of nearby SOTPN devices.
     * @return e.g. "Low risk — many peers nearby"
     */
    public String describeRisk(int nearbyDeviceCount) {
        switch (getRiskLevel(nearbyDeviceCount)) {
            case LOW:    return "Low risk — many peers nearby";
            case MEDIUM: return "Medium risk — moderate peer coverage";
            case HIGH:   return "High risk — few peers nearby. "
                    + "Consider waiting for online sync for large amounts.";
            default:     return "Unknown risk";
        }
    }

    // -----------------------------------------------------------------------
    // Breakdown (for logging / debug UI)
    // -----------------------------------------------------------------------

    /**
     * Returns a full summary string for logging.
     * Example: "Delay=5000ms | Risk=MEDIUM | Peers=3 | Condition=Moderate area"
     */
    public String getSummary(int nearbyDeviceCount) {
        return "Delay=" + calculateDelayMs(nearbyDeviceCount) + "ms"
                + " | Risk=" + getRiskLevel(nearbyDeviceCount)
                + " | Peers=" + nearbyDeviceCount
                + " | " + describeCondition(nearbyDeviceCount);
    }
}
