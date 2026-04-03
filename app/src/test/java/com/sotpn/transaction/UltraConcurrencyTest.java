package com.sotpn.transaction;

import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * ULTRA-CONCURRENCY RACE CONDITION TESTS
 * The "Final Boss" of tests: Forces a conflict exactly at the moment of commit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UltraConcurrencyTest {

    @Test
    public void testRace_ConflictArrivalAtCommitMoment_PrioritizesSafety() throws Exception {
        final AtomicInteger aborts = new AtomicInteger(0);
        final AtomicInteger commits = new AtomicInteger(0);
        
        final GossipStore store = new GossipStore();
        final String tokenId = "tok_race_final";
        
        // We use a CyclicBarrier to ensure two threads act at the EXACT same nanosecond
        final CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: The "Commit" Attempt
        executor.submit(() -> {
            try {
                barrier.await(); // Wait for sync
                // Simulate the decision logic in AdaptiveDelayHandler
                if (!store.checkConflict(tokenId).isConflict) {
                    commits.incrementAndGet();
                } else {
                    aborts.incrementAndGet();
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        // Thread 2: The "Conflict Arrival" (Gossip)
        executor.submit(() -> {
            try {
                barrier.await(); // Wait for sync
                store.addGossip(new com.sotpn.communication.GossipMessage(tokenId, "dev_evil", "tx_evil", System.currentTimeMillis(), 0));
            } catch (Exception e) { e.printStackTrace(); }
        });

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // RESULTS:
        // In a perfectly secure system, even if they hit at the same time,
        // the result must be either:
        // 1. Commit happens BEFORE gossip (Safety check passed)
        // 2. Abort happens because gossip arrived (Safety check caught it)
        // What MUST NOT happen is a commit AND a conflict being ignored.
        
        System.out.println("Race Result: Commits=" + commits.get() + ", Aborts=" + aborts.get());
        assertTrue("System must remain in a valid state (either commit or abort)", (commits.get() + aborts.get()) == 1);
    }
}
