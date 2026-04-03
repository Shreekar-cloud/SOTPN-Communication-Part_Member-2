package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
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
 * FINAL GRAVE IMPORTANCE AUDIT
 * Verifies the most critical security and stability requirements of the protocol.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GraveImportanceAuditTest {

    private GossipStore store;
    private GossipEngine engine;
    private BleManager bleMock;
    private WifiDirectManager wifiMock;

    @Before
    public void setUp() {
        store = new GossipStore();
        bleMock = mock(BleManager.class);
        wifiMock = mock(WifiDirectManager.class);
        engine = new GossipEngine(bleMock, wifiMock, store, mock(GossipEngine.GossipListener.class), "audit_node");
    }

    // -----------------------------------------------------------------------
    // AUDIT 1: BLE Payload Integrity
    // BLE gossip MUST contain more than just the TokenID to detect conflicts.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_BleGossip_MustContainFullWireString() {
        engine.startBroadcasting("tok_123", "tx_abc", 5000L);
        ShadowLooper.idleMainLooper();

        // Capture the argument sent to BLE. 
        // If it's only "tok_123", it's a FAIL. It must be the full TOKEN_SEEN:... string.
        verify(bleMock, atLeastOnce()).broadcastGossip(argThat(s -> s.contains("TOKEN_SEEN") && s.contains("tx_abc")));
    }

    // -----------------------------------------------------------------------
    // AUDIT 2: Future Timestamp Rejection (DoS Protection)
    // The store must reject timestamps from the future to prevent permanent RAM bloat.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Store_MustRejectFutureTimestamps() {
        long wayInFuture = System.currentTimeMillis() + 86400000; // 24 hours ahead
        GossipMessage maliciousMsg = new GossipMessage("tok_dos", "attacker", "tx_dos", wayInFuture, 0);
        
        engine.handleIncomingGossip(maliciousMsg.toWireString());
        
        // This should be FALSE. If true, the malicious entry will stay in RAM until 2099.
        assertFalse("Store MUST NOT accept future-dated gossip (DoS Risk)", store.hasSeenToken("tok_dos"));
    }

    // -----------------------------------------------------------------------
    // AUDIT 3: Inter-Radio Conflict Sync
    // Verifies that data arriving on BLE can be cross-referenced with WiFi.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_CrossRadio_ConflictDetection() {
        // 1. Receive gossip via BLE
        GossipMessage bleMsg = new GossipMessage("tok_race", "dev_A", "tx_1", System.currentTimeMillis(), 0);
        engine.handleIncomingGossip(bleMsg.toWireString());

        // 2. Receive gossip for same token but different TX via WiFi
        GossipMessage wifiMsg = new GossipMessage("tok_race", "dev_B", "tx_2", System.currentTimeMillis(), 0);
        GossipStore.ConflictResult result = store.addGossip(wifiMsg);

        assertTrue("System MUST detect conflicts across different radio carriers", result.isConflict);
    }
}
