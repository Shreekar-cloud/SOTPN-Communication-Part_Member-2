package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.wallet.WalletInterface;

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
 * RESOURCE EXHAUSTION & FAIL-SAFE TESTS
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ResourceExhaustionTest {

    private MockWallet wallet;
    private PreparePhaseHandler prepareHandler;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        wallet.tokenToReturn = new WalletInterface.TokenInfo("tok_limit", 1000L, System.currentTimeMillis() + 60000);
        prepareHandler = new PreparePhaseHandler(wallet, mock(BleManager.class), mock(WifiDirectManager.class));
    }

    // -----------------------------------------------------------------------
    // TEST 1: BLE Service Loss mid-Phase 1
    // -----------------------------------------------------------------------
    @Test
    public void testFailSafe_BleServiceFailure_UnlocksToken() {
        BleManager brokenBle = mock(BleManager.class);
        doThrow(new RuntimeException("BLE Service Died")).when(brokenBle).sendTransaction(any());

        PreparePhaseHandler failingHandler = new PreparePhaseHandler(wallet, brokenBle, mock(WifiDirectManager.class));
        
        failingHandler.execute("receiver", 1000L, true, new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) { fail("Should not succeed"); }
            @Override public void onPrepareFailed(String r) { /* expected */ }
        });
        ShadowLooper.idleMainLooper();

        assertTrue("Token MUST be unlocked if BLE fails", wallet.tokenWasUnlocked);
        assertFalse("Token MUST NOT be in locked map", wallet.isTokenLocked("tok_limit"));
    }

    // -----------------------------------------------------------------------
    // TEST 2: Gossip Store Flooding
    // -----------------------------------------------------------------------
    @Test
    public void testScale_GossipFlooding_RemainsFunctional() {
        GossipStore store = new GossipStore();
        
        for (int i = 0; i < 50000; i++) {
            store.addGossip(new GossipMessage("tok_" + i, "dev_" + i, "tx_" + i, System.currentTimeMillis(), 0));
        }

        GossipStore.ConflictResult result = store.checkConflict("tok_999");
        assertNotNull(result);
        assertFalse(result.isConflict);
    }

    // -----------------------------------------------------------------------
    // TEST 3: Wallet Signing Failure
    // -----------------------------------------------------------------------
    @Test
    public void testFailSafe_SigningFailure_RollsBackState() {
        // We set this to true initially to let the handler reach the sign check
        wallet.reset();
        wallet.tokenToReturn = new WalletInterface.TokenInfo("tok_limit", 1000L, System.currentTimeMillis() + 60000);
        wallet.signShouldSucceed = false; 

        prepareHandler.execute("receiver", 1000L, true, new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) { fail("Should not sign"); }
            @Override public void onPrepareFailed(String r) { /* expected */ }
        });
        ShadowLooper.idleMainLooper();

        assertTrue("Token must be released after signing fails", wallet.tokenWasUnlocked);
        assertFalse("Token must not be in locked map", wallet.isTokenLocked("tok_limit"));
    }
}
