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
 * GOSSIP PROPAGATION & TTL TESTS
 * Verifies that gossip correctly spreads through the mesh network and 
 * terminates safely to prevent infinite loops.
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

    // -----------------------------------------------------------------------
    // TEST 1: Hop Limit Enforcement
    // Messages at the MAX_HOPS limit must NOT be relayed further.
    // -----------------------------------------------------------------------
    @Test
    public void testGossip_AtMaxHops_IsNotRelayed() {
        // Message at hop 9 (assuming MAX_HOPS is 9 or 10)
        GossipMessage staleMsg = new GossipMessage("tok_1", "remote_node", "tx_1", System.currentTimeMillis(), 9);
        
        engine.handleIncomingGossip(staleMsg.toWireString());

        // Verify it was stored locally
        assertTrue(store.hasSeenToken("tok_1"));

        // Verify NO broadcast calls were made (since it's expired)
        verify(bleMock, never()).broadcastGossip(anyString());
        verify(wifiMock, never()).sendGossip(anyString());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Relay Hop Increment
    // Valid messages should have their hop count increased by 1 before relaying.
    // -----------------------------------------------------------------------
    @Test
    public void testGossip_Relay_IncrementsHopCount() {
        GossipMessage incoming = new GossipMessage("tok_2", "remote_node", "tx_2", System.currentTimeMillis(), 2);
        
        engine.handleIncomingGossip(incoming.toWireString());

        // Verify relay attempt via WiFi
        // We capture the string sent to wifiManager and check if it contains "3" (hop count + 1)
        verify(wifiMock).sendGossip(argThat(s -> s.contains(":3"))); 
    }

    // -----------------------------------------------------------------------
    // TEST 3: Deduplication (Loop Prevention)
    // If we receive the same gossip message again, we should NOT relay it.
    // -----------------------------------------------------------------------
    @Test
    public void testGossip_DuplicateMessage_IsNotRelayedTwice() {
        GossipMessage msg = new GossipMessage("tok_3", "remote_node", "tx_3", System.currentTimeMillis(), 0);
        
        // Receive first time
        engine.handleIncomingGossip(msg.toWireString());
        // Receive second time (exact duplicate)
        engine.handleIncomingGossip(msg.toWireString());

        // Should have only called broadcast once total
        verify(wifiMock, times(1)).sendGossip(anyString());
    }
}
