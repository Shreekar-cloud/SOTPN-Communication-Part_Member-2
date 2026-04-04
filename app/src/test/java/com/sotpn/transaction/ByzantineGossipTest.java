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

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ByzantineGossipTest {

    private GossipStore  store;
    private GossipEngine engine;
    private GossipStore.ConflictResult lastConflict;

    @Before
    public void setUp() {
        store = new GossipStore();
        lastConflict = null;
        
        engine = new GossipEngine(
                mock(BleManager.class), mock(WifiDirectManager.class), 
                store, 
                new GossipEngine.GossipListener() {
                    @Override public void onConflictDetected(GossipStore.ConflictResult r) { lastConflict = r; }
                    @Override public void onGossipReceived(GossipMessage m) {}
                }, 
                "my_device"
        );
    }

    @Test
    public void testByzantine_MaxHopGossip_isNotRelayed() {
        // Updated to use 6-argument constructor
        GossipMessage maliciousMsg = new GossipMessage("tok_1", "attacker", "tx_1", System.currentTimeMillis(), "sig_1", 9);
        
        engine.handleIncomingGossip(maliciousMsg.toWireString());
        
        assertTrue("Max hop gossip should be stored", store.hasSeenToken("tok_1"));
        assertTrue(maliciousMsg.isExpired());
    }

    @Test
    public void testByzantine_SelfSpoofing_isIgnored() {
        // Message claims to come from "my_device" (which is the device public key)
        GossipMessage spoof = new GossipMessage("tok_spoof", "my_device", "tx_spoof", System.currentTimeMillis(), "sig_spoof", 0);
        
        engine.handleIncomingGossip(spoof.toWireString());
        
        assertFalse("Spoofed self-message should not be stored", store.hasSeenToken("tok_spoof"));
    }

    @Test
    public void testByzantine_MalformedGossip_doesNotCrash() {
        try {
            engine.handleIncomingGossip("GARBAGE_DATA_!!!_123");
            engine.handleIncomingGossip("TOKEN_SEEN:only:three:parts");
        } catch (Exception e) {
            fail("Gossip Engine crashed on malformed input");
        }
    }
}
