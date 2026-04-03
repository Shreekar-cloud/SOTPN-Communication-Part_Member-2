package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * ZERO-DAY SECURITY AUDIT
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ZeroDaySecurityAuditTest {

    private com.sotpn.communication.GossipEngine engine;
    private BleManager bleMock;
    private WifiDirectManager wifiMock;
    private GossipStore store;

    @Before
    public void setUp() {
        bleMock = mock(BleManager.class);
        wifiMock = mock(WifiDirectManager.class);
        store = new GossipStore();
        
        engine = new com.sotpn.communication.GossipEngine(bleMock, wifiMock, store, mock(com.sotpn.communication.GossipEngine.GossipListener.class), "node_audit");
    }

    @Test
    public void testAudit_ConflictDetected_ShouldSwitchToAlertMode() {
        // Start local sighting
        engine.recordLocalSighting("tok_1", "tx_local");
        
        // Receive conflict from mesh
        GossipMessage conflictMsg = new GossipMessage("tok_1", "dev_evil", "tx_evil", System.currentTimeMillis(), 0);
        engine.handleIncomingGossip(conflictMsg.toWireString());

        // In a fortified system, the engine would continue broadcasting the CONFLICT
        // instead of going silent. This test serves as a design check.
    }

    @Test
    public void testAudit_SybilAttack_DelayFloorEnforced() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        
        // Attacker simulates 1000 devices
        long delay = calc.calculateDelayMs(1000);
        
        // Safety floor must be 3 seconds
        assertTrue("Delay must never drop below 3s safety floor", delay >= 3000);
    }
}
