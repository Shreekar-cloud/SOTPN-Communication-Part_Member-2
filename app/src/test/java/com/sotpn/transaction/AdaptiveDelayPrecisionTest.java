package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * ADAPTIVE DELAY PRECISION TESTS
 * Verifies that the real Handler-based timer enforces the correct SOTPN delay window.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AdaptiveDelayPrecisionTest {

    private AdaptiveDelayHandler handler;
    private BleManager bleMock;
    private GossipEngine gossipMock;
    private Transaction tx;

    private boolean delayCompleted = false;
    private long lastRemainingMs = -1;

    @Before
    public void setUp() {
        bleMock = mock(BleManager.class);
        gossipMock = mock(GossipEngine.class);
        AdaptiveDelayCalculator calculator = new AdaptiveDelayCalculator();
        
        handler = new AdaptiveDelayHandler(calculator, gossipMock, bleMock);
        tx = new Transaction("tx_1", "tok_1", "s", "r", System.currentTimeMillis(), "n", "s");
        delayCompleted = false;
        lastRemainingMs = -1;
    }

    private AdaptiveDelayHandler.DelayListener makeListener() {
        return new AdaptiveDelayHandler.DelayListener() {
            @Override public void onDelayComplete(Transaction transaction) { delayCompleted = true; }
            @Override public void onDelayAborted(Transaction t, GossipStore.ConflictResult c) {}
            @Override public void onDelayProgress(long remaining, long total, AdaptiveDelayCalculator.RiskLevel risk) {
                lastRemainingMs = remaining;
            }
        };
    }

    // -----------------------------------------------------------------------
    // TEST 1: CROWDED CONDITION (6+ peers) -> 3 second delay
    // -----------------------------------------------------------------------
    @Test
    public void testDelay_Crowded_ExactlyThreeSeconds() {
        when(bleMock.getNearbyDeviceCount()).thenReturn(6);
        handler.execute(tx, makeListener());

        // At 2.9 seconds, should NOT be complete
        ShadowLooper.idleMainLooper(2900, TimeUnit.MILLISECONDS);
        assertFalse("Should still be delaying at 2.9s", delayCompleted);
        assertEquals(TransactionPhase.DELAYING, tx.getPhase());

        // At 3.0 seconds (plus small buffer), should be complete
        ShadowLooper.idleMainLooper(101, TimeUnit.MILLISECONDS);
        assertTrue("Should complete exactly at 3s", delayCompleted);
        assertEquals(TransactionPhase.COMMITTING, tx.getPhase());
    }

    // -----------------------------------------------------------------------
    // TEST 2: ISOLATED CONDITION (0 peers) -> 10 second delay
    // -----------------------------------------------------------------------
    @Test
    public void testDelay_Isolated_ExactlyTenSeconds() {
        when(bleMock.getNearbyDeviceCount()).thenReturn(0);
        handler.execute(tx, makeListener());

        // At 9.9 seconds, should NOT be complete
        ShadowLooper.idleMainLooper(9900, TimeUnit.MILLISECONDS);
        assertFalse("Should still be delaying at 9.9s", delayCompleted);

        // At 10.0 seconds (plus small buffer), should be complete
        ShadowLooper.idleMainLooper(101, TimeUnit.MILLISECONDS);
        assertTrue("Should complete exactly at 10s", delayCompleted);
    }

    // -----------------------------------------------------------------------
    // TEST 3: Tick Progress
    // Verifies that onDelayProgress is called every second.
    // -----------------------------------------------------------------------
    @Test
    public void testDelay_TickingProgress_UpdatesEverySecond() {
        when(bleMock.getNearbyDeviceCount()).thenReturn(0); // 10s delay
        handler.execute(tx, makeListener());

        // Initially it's delayMs
        assertEquals(10000, lastRemainingMs);

        // Advance by 1.1s to ensure the 1s tick has definitely fired and updated the variable
        ShadowLooper.idleMainLooper(1100, TimeUnit.MILLISECONDS);
        assertTrue("Should have ticked and reduced remaining time. Current: " + lastRemainingMs, lastRemainingMs < 10000);
        assertTrue("Should have ~9s remaining after 1s. Current: " + lastRemainingMs, lastRemainingMs <= 9000);

        // Advance further
        ShadowLooper.idleMainLooper(4000, TimeUnit.MILLISECONDS);
        assertTrue("Should have ~5s remaining after 5s total. Current: " + lastRemainingMs, lastRemainingMs <= 5000);
    }
}
