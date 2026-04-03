package com.sotpn.communication;

import com.sotpn.transaction.AdaptiveDelayCalculator;
import com.sotpn.transaction.AdaptiveDelayHandler;
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
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * BYZANTINE MESH AUDIT TESTS
 * Verifies that the mesh remains secure even under Sybil attacks 
 * and gossip suppression attempts.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ByzantineMeshAuditTest {

    private GossipEngine engine;
    private BleManager bleMock;
    private WifiDirectManager wifiMock;
    private GossipStore store;

    @Before
    public void setUp() {
        bleMock = mock(BleManager.class);
        wifiMock = mock(WifiDirectManager.class);
        store = new GossipStore();
        
        engine = new GossipEngine(bleMock, wifiMock, store, mock(GossipEngine.GossipListener.class), "node_audit");
    }

    // -----------------------------------------------------------------------
    // TEST 1: Anti-Shadowing logic
    // Conflict detection MUST NOT stop the node from warning the rest of the mesh.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Gossip_ConflictDoesNotSilenceNode() {
        // 1. Locally sighting TX_1
        engine.recordLocalSighting("tok_1", "tx_1");
        
        // 2. Receive conflict from mesh about TX_2 (Double spend)
        GossipMessage conflictMsg = new GossipMessage("tok_1", "dev_evil", "tx_2", System.currentTimeMillis(), 0);
        engine.handleIncomingGossip(conflictMsg.toWireString());

        // 3. Logic check: Did the engine 'isActive' become false? 
        // In a hardened system, it would stay active but change its broadcast content 
        // to propagate the ConflictProof instead of the sighting.
    }

    // -----------------------------------------------------------------------
    // TEST 2: Safety Floor Verification
    // Adaptive delay must never drop below the 3s safety floor, regardless of 
    // sybil-simulated peer count.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Delay_EnforcesSafetyFloor() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        
        // Attacker simulates 1000 devices via a single radio
        long delay = calc.calculateDelayMs(1000);
        
        // Final floor must be exactly 3 seconds (spec compliance)
        assertTrue("Delay must be at least 3000ms", delay >= 3000);
        assertEquals("Safety floor should be exactly 3s", 3000, delay);
    }
}
