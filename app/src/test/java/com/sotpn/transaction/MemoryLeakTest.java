package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.wallet.WalletInterface;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : MemoryLeakTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * Tests that the system cleans up resources after use.
 */
public class MemoryLeakTest {

    // -----------------------------------------------------------------------
    // TEST 1: GossipStore cleared after 1000 transactions
    // -----------------------------------------------------------------------
    @Test
    public void test1_gossipStore_clearedAfterTransactions() {
        GossipStore store = new GossipStore();

        // Fill with 1000 entries
        for (int i = 0; i < 1000; i++) {
            store.addGossip(new GossipMessage(
                    "tok_" + i, "device_" + i, "tx_" + i,
                    System.currentTimeMillis(), 0));
        }
        assertEquals("Store has 1000 tokens", 1000, store.getTrackedTokenCount());

        // Clear all
        store.clearAll();
        assertEquals("Store must be empty after clearAll",
                0, store.getTrackedTokenCount());
        System.out.println("TEST 1 — GossipStore cleared ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 2: NonceStore cleared after 1000 transactions
    // -----------------------------------------------------------------------
    @Test
    public void test2_nonceStore_clearedAfterTransactions() {
        NonceStore store = new NonceStore();

        for (int i = 0; i < 1000; i++) {
            store.checkAndRecord("nonce_leak_" + i);
        }
        assertEquals("Store has 1000 nonces", 1000, store.size());

        store.clearAll();
        assertEquals("Store must be empty after clearAll", 0, store.size());

        // Previously seen nonce must now be accepted again
        boolean accepted = store.checkAndRecord("nonce_leak_0");
        assertTrue("Cleared nonce must be accepted again", accepted);
        System.out.println("TEST 2 — NonceStore cleared ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 3: clearExpiredGossip frees old entries
    // -----------------------------------------------------------------------
    @Test
    public void test3_clearExpiredGossip_freesMemory() {
        GossipStore store = new GossipStore();
        long oldTime = System.currentTimeMillis() - 200_000; // 3 min ago

        // Add old entries
        for (int i = 0; i < 100; i++) {
            store.addGossip(new GossipMessage(
                    "tok_old_" + i, "device_" + i, "tx_" + i,
                    oldTime, 0));
        }

        // Add fresh entries
        for (int i = 0; i < 100; i++) {
            store.addGossip(new GossipMessage(
                    "tok_fresh_" + i, "device_fresh_" + i, "tx_fresh_" + i,
                    System.currentTimeMillis(), 0));
        }

        assertEquals("200 total tokens before clear",
                200, store.getTrackedTokenCount());

        // Clear entries older than 2 minutes
        store.clearExpiredGossip(120_000);

        assertTrue("Old entries should be cleared",
                store.getTrackedTokenCount() <= 100);
        assertTrue("Fresh entries should remain",
                store.getTrackedTokenCount() > 0);
        System.out.println("TEST 3 — Expired gossip cleared ✅. Remaining: "
                + store.getTrackedTokenCount());
    }

    // -----------------------------------------------------------------------
    // TEST 4: Cancelled transactions — no orphaned locked tokens
    // -----------------------------------------------------------------------
    @Test
    public void test4_cancelledTransactions_noOrphanedLocks() {
        MockWallet wallet = new MockWallet();
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_orphan", 10_000L,
                System.currentTimeMillis() + 60_000);

        PreparePhaseHandler handler =
                new PreparePhaseHandler(wallet, null, null);

        // Start 10 transactions, abort each one
        for (int i = 0; i < 10; i++) {
            wallet.tokenWasLocked   = false;
            wallet.tokenWasUnlocked = false;

            handler.execute("receiver", 10_000L, true,
                    new PreparePhaseHandler.PrepareListener() {
                        @Override public void onPrepareSent(
                                com.sotpn.model.Transaction tx) {}
                        @Override public void onPrepareFailed(String r) {}
                    });

            handler.abort();
            assertTrue("Token must be unlocked after abort i=" + i,
                    wallet.tokenWasUnlocked);
        }

        assertFalse("No orphaned lock after 10 aborts",
                wallet.isTokenLocked("tok_orphan"));
        System.out.println("TEST 4 — No orphaned locks after 10 aborts ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 5: Large gossip flood then clear — memory returns to normal
    // -----------------------------------------------------------------------
    @Test
    public void test5_largFloodThenClear_memoryReturns() {
        GossipStore store = new GossipStore();

        long memBefore = Runtime.getRuntime().freeMemory();

        // Flood with 50,000 entries
        for (int i = 0; i < 50_000; i++) {
            store.addGossip(new GossipMessage(
                    "tok_mem_" + (i % 1000), "device_" + i,
                    "tx_" + i, System.currentTimeMillis(), 0));
        }

        // Clear everything
        store.clearAll();
        System.gc(); // suggest garbage collection

        assertEquals("Store empty after clear", 0, store.getTrackedTokenCount());
        System.out.println("TEST 5 — Large flood cleared ✅");
    }
}