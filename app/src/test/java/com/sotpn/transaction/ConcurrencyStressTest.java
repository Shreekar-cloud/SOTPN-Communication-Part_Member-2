package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConcurrencyStressTest {

    private static final int DEVICE_COUNT      = 10_000;
    private static final int THREAD_POOL_SIZE  = 100;
    private static final int TIMEOUT_SECONDS   = 60;

    private NonceStore             nonceStore;
    private GossipStore            gossipStore;
    private AdaptiveDelayCalculator calculator;

    @Before
    public void setUp() {
        nonceStore  = new NonceStore();
        gossipStore = new GossipStore();
        calculator  = new AdaptiveDelayCalculator();
    }

    @Test
    public void test1_tenThousandUniqueNonces_allAccepted() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);
        AtomicInteger   accepted = new AtomicInteger(0);

        for (int i = 0; i < DEVICE_COUNT; i++) {
            final String nonce = "unique_nonce_device_" + i;
            executor.submit(() -> {
                try {
                    if (nonceStore.checkAndRecord(nonce)) accepted.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        assertEquals(DEVICE_COUNT, accepted.get());
    }

    @Test
    public void test3_tenThousandGossipBroadcasts_noCorruption() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);

        for (int i = 0; i < DEVICE_COUNT; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    // Updated to 6-arg constructor
                    GossipMessage msg = new GossipMessage("tok_" + (deviceId % 100), "device_" + deviceId, "tx_" + deviceId, System.currentTimeMillis(), "sig_" + deviceId, 0);
                    gossipStore.addGossip(msg);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(gossipStore.getTrackedTokenCount() > 0);
    }

    @Test
    public void test4_thousandDoubleSpendAttempts_allDetected() throws InterruptedException {
        ExecutorService executor    = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch       = new CountDownLatch(1000);
        AtomicInteger   conflictCount = new AtomicInteger(0);

        gossipStore.addGossip(new GossipMessage("tok_ds", "dev_A", "tx_1", System.currentTimeMillis(), "sig_1", 0));
        gossipStore.addGossip(new GossipMessage("tok_ds", "dev_B", "tx_2", System.currentTimeMillis(), "sig_2", 0));

        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                try {
                    if (gossipStore.checkConflict("tok_ds").isConflict) conflictCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        assertEquals(1000, conflictCount.get());
    }
}
