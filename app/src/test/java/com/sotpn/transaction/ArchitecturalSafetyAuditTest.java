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
        TransactionManager.TransactionListener listener = mock(TransactionManager.TransactionListener.class);
        manager = new TransactionManager(context, wallet, listener);
    }

    private void signTransaction(Transaction tx, String senderKey) {
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        // Mimic MockWallet signature format: sig:[signerPublicKey]:[dataHash]
        tx.setSignature("sig:" + senderKey + ":" + data.hashCode());
    }

    // -----------------------------------------------------------------------
    // TEST 1: Mid-Delay Conflict Priority
    // If a conflict is detected while the 10s timer is running, the system 
    // MUST kill the timer and abort immediately.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_GossipConflictDuringDelay_AbortsHappyPath() {
        String senderKey = "sender_key";
        Transaction tx = new Transaction("tx_audit", "tok_audit", senderKey, wallet.getPublicKey(), 
                                         System.currentTimeMillis(), "n", "");
        signTransaction(tx, senderKey);
        
        // Start incoming transaction (reaches Phase 3 DELAYING)
        manager.onTransactionReceived(tx);
        ShadowLooper.idleMainLooper();
        assertEquals(TransactionPhase.DELAYING, tx.getPhase());

        // Simulate conflict arriving at T+5s
        ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS);
        
        // This triggers the handleConflict logic via public callback
        manager.onGossipReceived("TOKEN_SEEN:tok_audit:dev_evil:tx_evil:" + System.currentTimeMillis() + ":0");
        ShadowLooper.idleMainLooper();

        // Transaction must be dead
        assertEquals(TransactionPhase.FAILED, tx.getPhase());
        
        // Happy path timer (the 10s delay) MUST NOT wake up and commit later
        ShadowLooper.idleMainLooper(6, TimeUnit.SECONDS);
        assertEquals("Transaction must stay FAILED even after original delay expires", 
                     TransactionPhase.FAILED, tx.getPhase());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Multi-Path Atomic Entry
    // Verifies that receiving two transactions via different radios simultaneously 
    // results in a local conflict detection.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_InterRadioCollision_IsTreatedAsFraud() {
        String senderKey1 = "s1";
        String senderKey2 = "s2";
        Transaction tx1 = new Transaction("tx_1", "tok_race", senderKey1, wallet.getPublicKey(), System.currentTimeMillis(), "n1", "");
        Transaction tx2 = new Transaction("tx_2", "tok_race", senderKey2, wallet.getPublicKey(), System.currentTimeMillis(), "n2", "");
        signTransaction(tx1, senderKey1);
        signTransaction(tx2, senderKey2);

        // 1. Receive via WiFi
        manager.onTransactionReceived(tx1);
        ShadowLooper.idleMainLooper();

        // 2. Receive second sighting for same token via BLE
        manager.onTransactionReceived(tx2, "MAC_BLE");
        ShadowLooper.idleMainLooper();

        // System should have detected the conflict locally
        assertEquals(TransactionPhase.FAILED, tx1.getPhase());
    }
}
