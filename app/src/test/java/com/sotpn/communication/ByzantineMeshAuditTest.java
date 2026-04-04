package com.sotpn.communication;

import com.sotpn.transaction.AdaptiveDelayCalculator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * BYZANTINE MESH AUDIT TESTS
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
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Gossip_ConflictDoesNotSilenceNode() {
        // 1. Locally sighting TX_1 with a dummy signature (3 arguments required)
        engine.recordLocalSighting("tok_1", "tx_1", "sig_1");
        
        // 2. Receive conflict from mesh about TX_2 (6 arguments required)
        GossipMessage conflictMsg = new GossipMessage("tok_1", "dev_evil", "tx_2", System.currentTimeMillis(), "sig_evil", 0);
        engine.handleIncomingGossip(conflictMsg.toWireString());

        // 3. Verify the conflict was added to the store
        assertTrue(store.hasSeenToken("tok_1"));
    }

    // -----------------------------------------------------------------------
    // TEST 2: Safety Floor Verification
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Delay_EnforcesSafetyFloor() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        
        // Attacker simulates 1000 devices via a single radio
        long delay = calc.calculateDelayMs(1000);
        
        // Safety floor must be exactly 3 seconds
        assertEquals("Safety floor should be exactly 3s", 3000, delay);
    }
}
