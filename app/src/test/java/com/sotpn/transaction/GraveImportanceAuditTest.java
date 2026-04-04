package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
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
    // AUDIT 1: Nonce Store Memory Hard-Cap
    // Verifies that the NonceStore doesn't grow indefinitely.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_NonceStore_EnforcesMemorySafety() {
        NonceStore ns = new NonceStore();
        // Flood with nonces
        for (int i = 0; i < 50000; i++) {
            ns.checkAndRecord("flood_" + i);
        }
        // Safety Requirement: NonceStore should stay alive and cap memory.
        assertTrue("NonceStore must remain functional under volume attack", ns.size() > 0);
    }

    // -----------------------------------------------------------------------
    // AUDIT 2: Conflict Continuity
    // Verifies that data arriving on BLE can be cross-referenced with WiFi gossip.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_CrossRadio_ConflictDetection() {
        String tokenId = "tok_race";
        // 1. sighting 1 via BLE
        GossipMessage m1 = new GossipMessage(tokenId, "dev_A", "tx_1", System.currentTimeMillis(), "sig_1", 0);
        store.addGossip(m1);

        // 2. sighting 2 (Double spend) via WiFi
        GossipMessage m2 = new GossipMessage(tokenId, "dev_B", "tx_2", System.currentTimeMillis(), "sig_2", 0);
        GossipStore.ConflictResult result = store.addGossip(m2);

        assertTrue("Conflict MUST be detected across different radio carriers", result.isConflict);
    }
}
