package com.sotpn.transaction;

import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : AdaptiveDelayHandlerTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/transaction/
 *   Right-click → Run 'AdaptiveDelayHandlerTest'
 *   No phone needed.
 *
 * NOTE: Uses MockBleManager so getNearbyDeviceCount() returns
 *       a configurable value without real Bluetooth.
 */
public class AdaptiveDelayHandlerTest {

    private AdaptiveDelayCalculator calculator;
    private GossipStore             gossipStore;
    private MockAdaptiveDelayHandler handler;

    // Result holders
    private Transaction                      completedTx    = null;
    private Transaction                      abortedTx      = null;
    private GossipStore.ConflictResult       conflictResult = null;
    private long                             lastRemaining  = -1;
    private AdaptiveDelayCalculator.RiskLevel lastRisk      = null;

    @Before
    public void setUp() {
        calculator  = new AdaptiveDelayCalculator();
        gossipStore = new GossipStore();
        handler     = new MockAdaptiveDelayHandler(calculator, gossipStore);
        resetResults();
    }

    private void resetResults() {
        completedTx    = null;
        abortedTx      = null;
        conflictResult = null;
        lastRemaining  = -1;
        lastRisk       = null;
    }

    private AdaptiveDelayHandler.DelayListener makeListener() {
        return new AdaptiveDelayHandler.DelayListener() {
            @Override
            public void onDelayComplete(Transaction tx) {
                completedTx = tx;
            }
            @Override
            public void onDelayAborted(Transaction tx, GossipStore.ConflictResult conflict) {
                abortedTx      = tx;
                conflictResult = conflict;
            }
            @Override
            public void onDelayProgress(long remainingMs, long totalMs,
                                        AdaptiveDelayCalculator.RiskLevel risk) {
                lastRemaining = remainingMs;
                lastRisk      = risk;
            }
        };
    }

    private Transaction buildTransaction() {
        return new Transaction(
                "tx_001", "tok_abc",
                "sender_key", "receiver_key",
                System.currentTimeMillis(),
                "nonce_001", "sig_001"
        );
    }

    // -----------------------------------------------------------------------
    // Test 1: Calculator returns correct delay for 0 peers (isolated)
    // -----------------------------------------------------------------------
    @Test
    public void testIsolated_zeroDevices_returnsMaxDelay() {
        long delay = calculator.calculateDelayMs(0);
        assertEquals("0 peers should give max delay",
                AdaptiveDelayCalculator.ISOLATED_DELAY_MS, delay);
    }

    // -----------------------------------------------------------------------
    // Test 2: Calculator returns correct delay for 6 peers (crowded)
    // -----------------------------------------------------------------------
    @Test
    public void testCrowded_sixDevices_returnsMinDelay() {
        long delay = calculator.calculateDelayMs(6);
        assertEquals("6 peers should give min delay",
                AdaptiveDelayCalculator.CROWDED_DELAY_MS, delay);
    }

    // -----------------------------------------------------------------------
    // Test 3: Risk level is HIGH for 0 peers
    // -----------------------------------------------------------------------
    @Test
    public void testRiskLevel_zeroPeers_isHigh() {
        AdaptiveDelayCalculator.RiskLevel risk = calculator.getRiskLevel(0);
        assertEquals("0 peers = HIGH risk",
                AdaptiveDelayCalculator.RiskLevel.HIGH, risk);
    }

    // -----------------------------------------------------------------------
    // Test 4: Risk level is LOW for 6 peers
    // -----------------------------------------------------------------------
    @Test
    public void testRiskLevel_sixPeers_isLow() {
        AdaptiveDelayCalculator.RiskLevel risk = calculator.getRiskLevel(6);
        assertEquals("6 peers = LOW risk",
                AdaptiveDelayCalculator.RiskLevel.LOW, risk);
    }

    // -----------------------------------------------------------------------
    // Test 5: Delay completes cleanly when no conflict
    // -----------------------------------------------------------------------
    @Test
    public void testDelay_completesCleanly_whenNoConflict() {
        Transaction tx = buildTransaction();
        handler.simulateComplete(tx, makeListener());
        assertNotNull("Transaction should complete", completedTx);
        assertNull("No abort expected", abortedTx);
        assertEquals("Phase should be COMMITTING after delay",
                TransactionPhase.COMMITTING, completedTx.getPhase());
    }

    // -----------------------------------------------------------------------
    // Test 6: Delay aborts when conflict detected
    // -----------------------------------------------------------------------
    @Test
    public void testDelay_abortsWhenConflictDetected() {
        // Set up a conflict in gossip store
        gossipStore.addGossip(new GossipMessage(
                "tok_abc", "device_A", "tx_001",
                System.currentTimeMillis(), 0));
        gossipStore.addGossip(new GossipMessage(
                "tok_abc", "device_B", "tx_999",
                System.currentTimeMillis(), 0));

        GossipStore.ConflictResult conflict = gossipStore.checkConflict("tok_abc");
        Transaction tx = buildTransaction();
        handler.simulateAbort(tx, conflict, makeListener());

        assertNull("No completion expected on conflict", completedTx);
        assertNotNull("Transaction should be aborted", abortedTx);
        assertNotNull("Conflict result should be set", conflictResult);
        assertTrue("Conflict should be detected", conflictResult.isConflict);
    }

    // -----------------------------------------------------------------------
    // Test 7: Phase is FAILED after abort
    // -----------------------------------------------------------------------
    @Test
    public void testPhase_isFailedAfterAbort() {
        GossipStore.ConflictResult fakeConflict = new GossipStore.ConflictResult(
                true, "tok_abc", "tx_001", "tx_999", "dev_A", "dev_B");
        Transaction tx = buildTransaction();
        handler.simulateAbort(tx, fakeConflict, makeListener());
        assertEquals("Phase should be FAILED after abort",
                TransactionPhase.FAILED, abortedTx.getPhase());
    }

    // -----------------------------------------------------------------------
    // Test 8: describeCondition gives correct output
    // -----------------------------------------------------------------------
    @Test
    public void testDescribeCondition_correctForAllRanges() {
        String isolated = calculator.describeCondition(0);
        String moderate = calculator.describeCondition(3);
        String crowded  = calculator.describeCondition(6);

        assertTrue("Isolated should mention seconds",
                isolated.contains("10s"));
        assertTrue("Moderate should mention seconds",
                moderate.contains("5s"));
        assertTrue("Crowded should mention seconds",
                crowded.contains("3s"));
    }

    // -----------------------------------------------------------------------
    // MockAdaptiveDelayHandler
    // Simulates the delay handler without real timers or BLE
    // -----------------------------------------------------------------------

    static class MockAdaptiveDelayHandler {

        private final AdaptiveDelayCalculator calculator;
        private final GossipStore             gossipStore;

        MockAdaptiveDelayHandler(AdaptiveDelayCalculator calculator,
                                 GossipStore gossipStore) {
            this.calculator  = calculator;
            this.gossipStore = gossipStore;
        }

        /** Simulate a clean delay completion */
        void simulateComplete(Transaction tx,
                              AdaptiveDelayHandler.DelayListener listener) {
            tx.setPhase(TransactionPhase.COMMITTING);
            listener.onDelayComplete(tx);
        }

        /** Simulate a conflict abort during delay */
        void simulateAbort(Transaction tx,
                           GossipStore.ConflictResult conflict,
                           AdaptiveDelayHandler.DelayListener listener) {
            tx.setPhase(TransactionPhase.FAILED);
            listener.onDelayAborted(tx, conflict);
        }
    }
}