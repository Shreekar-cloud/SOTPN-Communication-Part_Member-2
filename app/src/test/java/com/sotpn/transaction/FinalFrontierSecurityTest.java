package com.sotpn.transaction;

import android.content.Context;
import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
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
 * FINAL FRONTIER SECURITY TESTS
 * Covers Gossip Storms, Mesh Isolation (Eclipse Attacks), and Atomic Limbo Recovery.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FinalFrontierSecurityTest {

    private GossipStore store;
    private TransactionManager manager;
    private MockWallet myWallet;

    @Before
    public void setUp() {
        store = new GossipStore();
        myWallet = new MockWallet();
        Context context = RuntimeEnvironment.getApplication();
        TransactionManager.TransactionListener listener = mock(TransactionManager.TransactionListener.class);
        manager = new TransactionManager(context, myWallet, listener);
    }

    // -----------------------------------------------------------------------
    // TEST 1: Circular Relay (Gossip Storm) Protection
    // Verifies that the system ignores a message if it has been seen with 
    // a better (lower) hop count, preventing infinite looping.
    // -----------------------------------------------------------------------
    @Test
    public void testCircularRelay_HigherHopCountIsIgnored() {
        String tokenId = "tok_storm";
        String senderKey = "dev_origin";
        String txId = "tx_1";
        long now = System.currentTimeMillis();
        String sig = "sig_1";

        // 1. Receive gossip at hop 1
        GossipMessage msgHop1 = new GossipMessage(tokenId, senderKey, txId, now, sig, 1);
        store.addGossip(msgHop1);
        assertTrue("Should process hop 1", store.hasProcessed(msgHop1));

        // 2. Malicious peer relays the SAME message back at hop 2
        GossipMessage msgHop2 = new GossipMessage(tokenId, senderKey, txId, now, sig, 2);
        
        // System MUST identify this as "already processed better"
        assertTrue("Hop 2 must be seen as already processed because hop 1 was better", 
                   store.hasProcessed(msgHop2));
        
        // Ensure adding it doesn't duplicate or change best hop
        store.addGossip(msgHop2);
        assertEquals("Store should still only have 1 sighting", 1, store.getGossipForToken(tokenId).size());
        assertEquals("Hop count should remain at 1", 1, store.getGossipForToken(tokenId).get(0).getHopCount());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Mesh Isolation (Sybil/Eclipse Attack)
    // Verifies that the system prioritizes safe delays if it hears 
    // multiple identical sightings from different IDs (Spoofing).
    // -----------------------------------------------------------------------
    @Test
    public void testEclipseAttack_IdentitySpoofingDetected() {
        String tokenId = "tok_eclipsed";
        String txId = "tx_legit";
        long now = System.currentTimeMillis();

        // Device A claims the transaction
        store.addGossip(new GossipMessage(tokenId, "device_A", txId, now, "sig_A", 0));
        
        // Device B claims the SAME transaction (Identity Spoofing)
        GossipStore.ConflictResult result = store.addGossip(
            new GossipMessage(tokenId, "device_B", txId, now, "sig_B", 0));

        // CRITICAL: Multiple devices claiming the SAME TxID is a protocol violation.
        assertTrue("System MUST detect conflict if multiple devices claim the same TX ID", result.isConflict);
    }

    // -----------------------------------------------------------------------
    // TEST 3: Mid-Commit Ghost Collision
    // If a conflict arrives *just* as we transition from Delay -> Commit.
    // -----------------------------------------------------------------------
    @Test
    public void testAtomicLimbo_ConflictAtCommitBoundary() {
        MockWallet senderWallet = new MockWallet();
        Transaction tx = new Transaction("tx_limbo", "tok_limbo", senderWallet.getPublicKey(), myWallet.getPublicKey(), 
                                         System.currentTimeMillis(), "n_limbo", "");
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(senderWallet.signTransaction(data));

        // Start transaction
        manager.onTransactionReceived(tx);
        ShadowLooper.idleMainLooper();
        assertEquals(TransactionPhase.DELAYING, tx.getPhase());

        // Advance time to 9.9 seconds (just before commit)
        ShadowLooper.idleMainLooper(9900, TimeUnit.MILLISECONDS);
        
        // Conflict arrives at the last millisecond
        manager.onGossipReceived("TOKEN_SEEN:tok_limbo:dev_evil:tx_evil:" + System.currentTimeMillis() + ":sig_evil:0");
        ShadowLooper.idleMainLooper();

        // Safety Requirement: Abort MUST happen even if we were about to finalize
        assertEquals("Transaction MUST be FAILED even if conflict arrives at commit boundary", 
                     TransactionPhase.FAILED, tx.getPhase());
    }
}
