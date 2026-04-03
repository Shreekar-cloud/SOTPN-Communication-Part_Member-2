package com.sotpn.transaction;

import android.content.Context;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * FINAL ARCHITECTURAL SAFETY AUDIT
 * Verifies the most advanced edge cases identified during the project audit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ArchitecturalSafetyAuditTest {

    private TransactionManager manager;
    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        Context context = RuntimeEnvironment.getApplication();
        manager = new TransactionManager(context, wallet, mock(TransactionManager.TransactionListener.class));
    }

    // -----------------------------------------------------------------------
    // TEST 1: Resource Cleanup Integrity
    // Verifies that aborting a transaction kills BOTH the prepare lock 
    // and the delay timer.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Abort_CleansAllResources() {
        // 1. Start a transaction to lock token and start delay
        manager.startSend("receiver_key", 1000L);
        ShadowLooper.idleMainLooper();
        
        assertTrue("Token must be locked initially", wallet.tokenWasLocked);

        // 2. Abort mid-flow
        manager.abort();
        ShadowLooper.idleMainLooper();

        // 3. CRITICAL CHECK: Both subsystems must be rolled back
        assertTrue("Prepare system must unlock token", wallet.tokenWasUnlocked);
        assertNull("Transaction Manager must clear active state", manager.getActiveTransaction());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Local Inter-Radio Conflict Detection
    // Verifies that if two radios receive the same token simultaneously, 
    // the system treats it as a double-spend conflict.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_SimultaneousRadios_DetectsConflict() {
        Transaction tx1 = new Transaction("tx_ble", "tok_race", wallet.getPublicKey(), "r", System.currentTimeMillis(), "n1", "s1");
        Transaction tx2 = new Transaction("tx_wifi", "tok_race", wallet.getPublicKey(), "r", System.currentTimeMillis(), "n2", "s2");

        // 1. Receive first sighting via BLE
        manager.onTransactionReceived(tx1, "MAC_BLE");
        ShadowLooper.idleMainLooper();

        // 2. Receive second sighting for same token via WiFi simultaneously
        manager.onTransactionReceived(tx2);
        ShadowLooper.idleMainLooper();

        // 3. CRITICAL CHECK: Transaction must fail due to local double-spend detection
        assertEquals("System must detect local inter-radio collision as a failure", 
                     TransactionPhase.FAILED, tx1.getPhase());
    }
}
