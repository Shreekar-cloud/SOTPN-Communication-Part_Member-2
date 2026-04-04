package com.sotpn.communication;

import com.sotpn.transaction.MockWallet;
import com.sotpn.transaction.TransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * SELECTIVE MUTING SAFETY TEST
 * Verifies that the system remains secure even if one communication 
 * channel (BLE or WiFi) is compromised or jammed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SelectiveMutingSafetyTest {

    private GossipEngine engine;
    private BleManager bleMock;
    private WifiDirectManager wifiMock;

    @Before
    public void setUp() {
        bleMock = mock(BleManager.class);
        wifiMock = mock(WifiDirectManager.class);
        GossipStore store = new GossipStore();
        
        engine = new GossipEngine(bleMock, wifiMock, store, mock(GossipEngine.GossipListener.class), "me");
    }

    // -----------------------------------------------------------------------
    // TEST: Partial Broadcast Failure
    // If BLE fails to broadcast gossip (e.g. jammed), the system must 
    // at least ensure the other channel (WiFi) succeeded, or abort.
    // -----------------------------------------------------------------------
    @Test
    public void testGossip_OneChannelJammed_ContinuesOnOtherChannel() {
        // Simulate BLE hardware failure/jamming
        doThrow(new RuntimeException("BLE Radio Jammed")).when(bleMock).broadcastGossip(anyString());

        // Start broadcasting (Phase 3 simulation)
        // Updated to match 4-argument startBroadcasting: (tokenId, txId, signature, delayMs)
        engine.startBroadcasting("tok_1", "tx_1", "sig_1", 10000L);
        
        // Idle the looper to allow the Handler to execute the first broadcast task
        ShadowLooper.idleMainLooper();
        
        // Verify that even though BLE failed, WiFi still attempted to send
        // This ensures gossip isn't totally silenced by a single radio exploit.
        verify(wifiMock, atLeastOnce()).sendGossip(anyString());
    }
}
