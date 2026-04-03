package com.sotpn.communication;

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
 * RELAY RELIABILITY & MESH HEALTH TESTS
 * Verifies that gossip messages propagate correctly across multiple hops.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RelayReliabilityTest {

    private GossipStore store;
    private GossipEngine engine;
    private BleManager bleMock;
    private WifiDirectManager wifiMock;

    @Before
    public void setUp() {
        store = new GossipStore();
        bleMock = mock(BleManager.class);
        wifiMock = mock(WifiDirectManager.class);
        engine = new GossipEngine(bleMock, wifiMock, store, mock(GossipEngine.GossipListener.class), "node_relay");
    }

    // -----------------------------------------------------------------------
    // TEST 1: Hop Count Enforcement
    // Verifies that a message with hopCount < MAX_HOPS is relayed.
    // -----------------------------------------------------------------------
    @Test
    public void testMesh_ValidHop_IsRelayed() {
        // Message at hop 1. MAX_HOPS is 3.
        GossipMessage msg = new GossipMessage("tok_1", "origin", "tx_1", System.currentTimeMillis(), 1);
        
        engine.handleIncomingGossip(msg.toWireString());

        // Verification: wifiManager should receive a relayed message with hopCount = 2
        verify(wifiMock, atLeastOnce()).sendGossip(argThat(s -> s.contains(":2")));
    }

    // -----------------------------------------------------------------------
    // TEST 2: Multi-Hop Termination
    // Verifies that a message at MAX_HOPS is NOT relayed (prevents broadcast storm).
    // -----------------------------------------------------------------------
    @Test
    public void testMesh_MaxHopReached_RelayStops() {
        // Message at hop 3. MAX_HOPS is 3.
        GossipMessage msg = new GossipMessage("tok_2", "origin", "tx_2", System.currentTimeMillis(), 3);
        
        engine.handleIncomingGossip(msg.toWireString());

        // Verification: Should NOT call broadcast/relay
        verify(wifiMock, never()).sendGossip(anyString());
    }

    // -----------------------------------------------------------------------
    // TEST 3: Conflict Discovery via Relay
    // If a conflict is discovered through a relayed message, it must trigger 
    // the local listener.
    // -----------------------------------------------------------------------
    @Test
    public void testMesh_RelayedConflict_TriggersLocalAbort() {
        GossipEngine.GossipListener listener = mock(GossipEngine.GossipListener.class);
        GossipEngine engineWithListener = new GossipEngine(bleMock, wifiMock, store, listener, "node_audit");

        // 1. Locally see TX_1
        engineWithListener.recordLocalSighting("tok_3", "tx_1");

        // 2. Receive RELAYED gossip from mesh about TX_2 (Double Spend)
        GossipMessage relayedConflict = new GossipMessage("tok_3", "remote_dev", "tx_2", System.currentTimeMillis(), 1);
        engineWithListener.handleIncomingGossip(relayedConflict.toWireString());

        // 3. Verify local node detected the remote double-spend
        verify(listener, atLeastOnce()).onConflictDetected(any());
    }
}
