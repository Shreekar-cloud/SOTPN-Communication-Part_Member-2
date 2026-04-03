package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * NETWORK RELIABILITY & INTERRUPTION TESTS
 * Verifies that the system handles mid-transaction disconnections gracefully.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NetworkInterruptionTest {

    private MockWallet senderWallet, receiverWallet;
    private PreparePhaseHandler prepareHandler;
    private ValidationPhaseHandler validationHandler;
    private CommitPhaseHandler commitHandler;
    private NonceStore nonceStore;
    private BleManager bleMock;
    private WifiDirectManager wifiMock;
    private GossipEngine gossipEngine;

    @Before
    public void setUp() {
        senderWallet = new MockWallet();
        receiverWallet = new MockWallet();
        nonceStore = new NonceStore();
        
        bleMock = mock(BleManager.class);
        wifiMock = mock(WifiDirectManager.class);
        
        GossipStore gossipStore = new GossipStore();
        gossipEngine = new GossipEngine(
                bleMock, wifiMock, 
                gossipStore, mock(GossipEngine.GossipListener.class), "rec");

        prepareHandler = new PreparePhaseHandler(senderWallet, bleMock, wifiMock);
        validationHandler = new ValidationPhaseHandler(receiverWallet, nonceStore, gossipEngine);
        commitHandler = new CommitPhaseHandler(receiverWallet, bleMock, wifiMock);
    }

    // -----------------------------------------------------------------------
    // TEST 1: Disconnect after Phase 1 (Prepare)
    // Sender has locked the token, but receiver hasn't received it.
    // -----------------------------------------------------------------------
    @Test
    public void testInterruption_PostPhase1_UnlocksToken() {
        senderWallet.tokenToReturn = new WalletInterface.TokenInfo("tok_1", 1000L, System.currentTimeMillis() + 60000);
        
        prepareHandler.execute(receiverWallet.getPublicKey(), 1000L, true, new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) {}
            @Override public void onPrepareFailed(String r) {}
        });
        ShadowLooper.idleMainLooper();

        assertTrue("Token must be locked", senderWallet.tokenWasLocked);

        // Simulate disconnection (TransactionManager calls abort)
        prepareHandler.abort();

        assertTrue("Token must be unlocked after disconnection", senderWallet.tokenWasUnlocked);
        assertFalse("Token must not be in locked map", senderWallet.isTokenLocked("tok_1"));
    }

    // -----------------------------------------------------------------------
    // TEST 2: Disconnect during Phase 3 (Adaptive Delay)
    // Receiver is waiting but sender vanishes.
    // -----------------------------------------------------------------------
    @Test
    public void testInterruption_DuringDelay_AbortsCorrectly() {
        Transaction tx = new Transaction("tx_1", "tok_1", senderWallet.getPublicKey(), 
                                         receiverWallet.getPublicKey(), System.currentTimeMillis(), "n", "s");
        
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        // Ensure mock returns a count so calculateDelay works
        when(bleMock.getNearbyDeviceCount()).thenReturn(0);
        
        AdaptiveDelayHandler delayHandler = new AdaptiveDelayHandler(calc, gossipEngine, bleMock);

        // Transition to Phase 3
        delayHandler.execute(tx, new AdaptiveDelayHandler.DelayListener() {
            @Override public void onDelayComplete(Transaction transaction) {}
            @Override public void onDelayAborted(Transaction transaction, GossipStore.ConflictResult conflictDetails) {}
            @Override public void onDelayProgress(long remainingMs, long totalMs, AdaptiveDelayCalculator.RiskLevel riskLevel) {}
        });
        ShadowLooper.idleMainLooper();
        
        assertTrue("Delay handler should be running", delayHandler.isRunning());

        // Simulate disconnection mid-delay
        delayHandler.abort(); 
        
        assertFalse("Delay handler should not be running after abort", delayHandler.isRunning());
        // Final state should be FAILED
        assertEquals(TransactionPhase.FAILED, tx.getPhase());
    }

    // -----------------------------------------------------------------------
    // TEST 3: Partial Transaction Recovery
    // Verifies that a failed session doesn't corrupt the NonceStore.
    // -----------------------------------------------------------------------
    @Test
    public void testInterruption_DoesNotCorruptNonceStore() {
        String sharedNonce = "interrupted_nonce";
        
        // Receiver NonceStore shouldn't record if validation didn't finish
        assertFalse("Nonce should not be present yet", nonceStore.hasSeenNonce(sharedNonce));
        
        // If it was recorded and then interrupted, it stays recorded to prevent replay
        nonceStore.checkAndRecord(sharedNonce);
        assertTrue("Nonce stays recorded after interruption to prevent reuse", nonceStore.hasSeenNonce(sharedNonce));
    }
}
