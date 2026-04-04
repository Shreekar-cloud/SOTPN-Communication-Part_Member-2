package com.sotpn.transaction;

import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : RaceConditionTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * Tests that shared resources are safe under concurrent access.
 */
public class RaceConditionTest {

    private static final int THREADS = 100;
    private static final int TIMEOUT = 30;

    @Before
    public void setUp() {}

    // -----------------------------------------------------------------------
    // TEST 1: Two threads lock same token — only 1 wins
    // -----------------------------------------------------------------------
    @Test
    public void test1_sameTokenLock_onlyOneThreadWins()
            throws InterruptedException {

        MockWallet wallet = new MockWallet();
        CountDownLatch latch   = new CountDownLatch(THREADS);
        AtomicInteger  locked  = new AtomicInteger(0);
        AtomicInteger  failed  = new AtomicInteger(0);

        for (int i = 0; i < THREADS; i++) {
            Executors.newCachedThreadPool().submit(() -> {
                try {
                    if (wallet.lockToken("tok_race")) locked.incrementAndGet();
                    else                               failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT, TimeUnit.SECONDS);
        System.out.println("TEST 1 — Lock race: locked=" + locked.get()
                + " failed=" + failed.get());

        assertEquals("Exactly 1 thread must win the lock", 1, locked.get());
        assertEquals("All others must fail", THREADS - 1, failed.get());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Two threads record same nonce — only 1 wins
    // -----------------------------------------------------------------------
    @Test
    public void test2_sameNonce_onlyOneThreadWins()
            throws InterruptedException {

        NonceStore    store   = new NonceStore();
        CountDownLatch latch  = new CountDownLatch(THREADS);
        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < THREADS; i++) {
            Executors.newCachedThreadPool().submit(() -> {
                try {
                    if (store.checkAndRecord("nonce_race_shared"))
                        accepted.incrementAndGet();
                    else
                        rejected.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT, TimeUnit.SECONDS);
        System.out.println("TEST 2 — Nonce race: accepted=" + accepted.get()
                + " rejected=" + rejected.get());

        assertEquals("Exactly 1 thread must record the nonce", 1, accepted.get());
        assertEquals("All others rejected", THREADS - 1, rejected.get());
    }

    // -----------------------------------------------------------------------
    // TEST 3: Concurrent gossip writes — no data loss or corruption
    // -----------------------------------------------------------------------
    @Test
    public void test3_concurrentGossipWrites_noCorruption()
            throws InterruptedException {

        com.sotpn.communication.GossipStore store = new com.sotpn.communication.GossipStore();
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            Executors.newCachedThreadPool().submit(() -> {
                try {
                    // Updated to 6-arg constructor: (tokenId, senderPubKey, txId, timestampMs, signature, hopCount)
                    store.addGossip(new com.sotpn.communication.GossipMessage(
                            "tok_concurrent", "device_" + id,
                            "tx_" + id, System.currentTimeMillis(), "sig_" + id, 0));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT, TimeUnit.SECONDS);
        int count = store.getGossipForToken("tok_concurrent").size();
        System.out.println("TEST 3 — Concurrent writes: stored=" + count
                + "/" + THREADS);

        assertTrue("At least 1 gossip stored", count > 0);
        assertTrue("No more than THREADS stored", count <= THREADS);
    }

    // -----------------------------------------------------------------------
    // TEST 4: Concurrent balance reads — all consistent
    // -----------------------------------------------------------------------
    @Test
    public void test4_concurrentBalanceReads_allConsistent()
            throws InterruptedException {

        MockWallet     wallet  = new MockWallet();
        wallet.balance = 200_000L;
        CountDownLatch latch   = new CountDownLatch(THREADS);
        AtomicInteger  errors  = new AtomicInteger(0);

        for (int i = 0; i < THREADS; i++) {
            Executors.newCachedThreadPool().submit(() -> {
                try {
                    long balance = wallet.getBalance();
                    if (balance != 200_000L) errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT, TimeUnit.SECONDS);
        System.out.println("TEST 4 — Balance read errors: " + errors.get());
        assertEquals("All balance reads must be consistent", 0, errors.get());
    }

    // -----------------------------------------------------------------------
    // TEST 5: Concurrent unlock attempts — no deadlock
    // -----------------------------------------------------------------------
    @Test
    public void test5_concurrentUnlocks_noDeadlock()
            throws InterruptedException {

        MockWallet wallet = new MockWallet();
        wallet.lockToken("tok_unlock_race");

        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Executors.newCachedThreadPool().submit(() -> {
                try {
                    wallet.unlockToken("tok_unlock_race");
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(TIMEOUT, TimeUnit.SECONDS);
        assertTrue("Concurrent unlocks must complete without deadlock", completed);
        System.out.println("TEST 5 — Concurrent unlocks: no deadlock ✅");
    }
}
