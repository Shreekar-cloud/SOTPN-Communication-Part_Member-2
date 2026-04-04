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

    @Test
    public void testMesh_ValidHop_IsRelayed() {
        // Message at hop 1. MAX_HOPS is 3.
        GossipMessage msg = new GossipMessage("tok_1", "origin", "tx_1", System.currentTimeMillis(), "sig_1", 1);
        
        engine.handleIncomingGossip(msg.toWireString());

        verify(wifiMock, atLeastOnce()).sendGossip(argThat(s -> s.contains(":2")));
    }

    @Test
    public void testMesh_MaxHopReached_RelayStops() {
        // Message at hop 3. MAX_HOPS is 3.
        GossipMessage msg = new GossipMessage("tok_2", "origin", "tx_2", System.currentTimeMillis(), "sig_2", 3);
        
        engine.handleIncomingGossip(msg.toWireString());

        verify(wifiMock, never()).sendGossip(anyString());
    }

    @Test
    public void testMesh_RelayedConflict_TriggersLocalAbort() {
        GossipEngine.GossipListener listener = mock(GossipEngine.GossipListener.class);
        GossipEngine engineWithListener = new GossipEngine(bleMock, wifiMock, store, listener, "node_audit");

        engineWithListener.recordLocalSighting("tok_3", "tx_1", "sig_1");

        GossipMessage relayedConflict = new GossipMessage("tok_3", "remote_dev", "tx_2", System.currentTimeMillis(), "sig_2", 1);
        engineWithListener.handleIncomingGossip(relayedConflict.toWireString());

        verify(listener, atLeastOnce()).onConflictDetected(any());
    }
}
