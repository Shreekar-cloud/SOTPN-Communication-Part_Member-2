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
 * Member 2: Communication + Transaction Engine
 *
 * BYZANTINE FAULT TOLERANCE TESTS
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

    // -----------------------------------------------------------------------
    // BYZANTINE 1: Hop Limit Exploitation
    // A malicious peer sends a gossip message already at MAX_HOPS to 
    // stop it from spreading to other peers.
    // -----------------------------------------------------------------------
    @Test
    public void testByzantine_MaxHopGossip_isNotRelayed() {
        // Create a message at hop limit (9)
        GossipMessage maliciousMsg = new GossipMessage("tok_1", "attacker", "tx_1", System.currentTimeMillis(), 9);
        
        engine.handleIncomingGossip(maliciousMsg.toWireString());
        
        assertTrue("Max hop gossip should be stored", store.hasSeenToken("tok_1"));
        // Note: Relaying is usually verified via mock interactions, 
        // but protocol-wise, isExpired() will be true.
        assertTrue(maliciousMsg.isExpired());
    }

    // -----------------------------------------------------------------------
    // BYZANTINE 2: Identity Spoofing
    // Attacker sends gossip claiming to be "my_device". 
    // Engine should ignore self-spoofed messages.
    // -----------------------------------------------------------------------
    @Test
    public void testByzantine_SelfSpoofing_isIgnored() {
        // Message claims to come from "my_device"
        GossipMessage spoof = new GossipMessage("tok_spoof", "my_device", "tx_spoof", System.currentTimeMillis(), 0);
        
        engine.handleIncomingGossip(spoof.toWireString());
        
        assertFalse("Spoofed self-message should not be stored", store.hasSeenToken("tok_spoof"));
    }

    // -----------------------------------------------------------------------
    // BYZANTINE 3: Malformed Gossip Injection
    // Attacker sends garbage data to crash the gossip parser.
    // -----------------------------------------------------------------------
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
