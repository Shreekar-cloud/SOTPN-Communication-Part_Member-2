package com.sotpn.communication;

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
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GossipPropagationTest {

    private GossipStore store;
    private GossipEngine engine;
    private BleManager bleMock;
    private WifiDirectManager wifiMock;

    @Before
    public void setUp() {
        store = new GossipStore();
        bleMock = mock(BleManager.class);
        wifiMock = mock(WifiDirectManager.class);
        
        engine = new GossipEngine(bleMock, wifiMock, store, mock(GossipEngine.GossipListener.class), "node_center");
    }

    @Test
    public void testGossip_AtMaxHops_IsNotRelayed() {
        // Updated to use 6-argument constructor
        GossipMessage staleMsg = new GossipMessage("tok_1", "remote_node", "tx_1", System.currentTimeMillis(), "sig_1", 9);
        
        engine.handleIncomingGossip(staleMsg.toWireString());

        assertTrue(store.hasSeenToken("tok_1"));
        verify(bleMock, never()).broadcastGossip(anyString());
        verify(wifiMock, never()).sendGossip(anyString());
    }

    @Test
    public void testGossip_Relay_IncrementsHopCount() {
        // Updated to use 6-argument constructor
        GossipMessage incoming = new GossipMessage("tok_2", "remote_node", "tx_2", System.currentTimeMillis(), "sig_2", 2);
        
        engine.handleIncomingGossip(incoming.toWireString());

        verify(wifiMock).sendGossip(argThat(s -> s.contains(":3"))); 
    }

    @Test
    public void testGossip_DuplicateMessage_IsNotRelayedTwice() {
        // Updated to use 6-argument constructor
        GossipMessage msg = new GossipMessage("tok_3", "remote_node", "tx_3", System.currentTimeMillis(), "sig_3", 0);
        
        engine.handleIncomingGossip(msg.toWireString());
        ShadowLooper.idleMainLooper();
        
        engine.handleIncomingGossip(msg.toWireString());
        ShadowLooper.idleMainLooper();

        verify(wifiMock, times(1)).sendGossip(anyString());
    }
}
