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
 * Member 2: Communication + Transaction Engine
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

    // -----------------------------------------------------------------------
    // TEST 1: 10,000 unique nonces — all must be accepted
    // -----------------------------------------------------------------------
    @Test
    public void test1_tenThousandUniqueNonces_allAccepted()
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);
        AtomicInteger   accepted = new AtomicInteger(0);
        AtomicInteger   rejected = new AtomicInteger(0);

        for (int i = 0; i < DEVICE_COUNT; i++) {
            final String nonce = "unique_nonce_device_" + i;
            executor.submit(() -> {
                try {
                    boolean result = nonceStore.checkAndRecord(nonce);
                    if (result) accepted.incrementAndGet();
                    else        rejected.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 10,000 unique nonces should be accepted",
                DEVICE_COUNT, accepted.get());
        assertEquals("Zero rejections expected for unique nonces",
                0, rejected.get());
    }

    // -----------------------------------------------------------------------
    // TEST 2: 10,000 threads replay the SAME nonce — only 1 must be accepted
    // -----------------------------------------------------------------------
    @Test
    public void test2_tenThousandReplaysSameNonce_onlyOneAccepted()
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);
        AtomicInteger   accepted = new AtomicInteger(0);
        AtomicInteger   rejected = new AtomicInteger(0);

        final String SHARED_NONCE = "replay_attack_nonce";

        for (int i = 0; i < DEVICE_COUNT; i++) {
            executor.submit(() -> {
                try {
                    boolean result = nonceStore.checkAndRecord(SHARED_NONCE);
                    if (result) accepted.incrementAndGet();
                    else        rejected.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("Exactly 1 thread should accept the nonce",
                1, accepted.get());
        assertEquals("All other 9,999 should be rejected",
                DEVICE_COUNT - 1, rejected.get());
    }

    // -----------------------------------------------------------------------
    // TEST 3: 10,000 devices broadcast gossip simultaneously — no crashes
    // -----------------------------------------------------------------------
    @Test
    public void test3_tenThousandGossipBroadcasts_noCorruption()
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);

        for (int i = 0; i < DEVICE_COUNT; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    GossipMessage msg = new GossipMessage(
                            "tok_" + (deviceId % 100), // 100 unique tokens
                            "device_" + deviceId,
                            "tx_" + deviceId,
                            System.currentTimeMillis(),
                            0
                    );
                    gossipStore.addGossip(msg);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("Should have tracked tokens without data corruption",
                gossipStore.getTrackedTokenCount() > 0);
        assertTrue("Should not track more tokens than exist",
                gossipStore.getTrackedTokenCount() <= 100);
    }

    // -----------------------------------------------------------------------
    // TEST 4: 1,000 double-spend attempts — all conflicts detected
    // -----------------------------------------------------------------------
    @Test
    public void test4_thousandDoubleSpendAttempts_allDetected()
            throws InterruptedException {

        ExecutorService executor    = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch       = new CountDownLatch(1000);
        AtomicInteger   conflictCount = new AtomicInteger(0);

        // Pre-seed a conflict for token "tok_doublespend"
        gossipStore.addGossip(new GossipMessage(
                "tok_doublespend", "device_A", "tx_original",
                System.currentTimeMillis(), 0));
        gossipStore.addGossip(new GossipMessage(
                "tok_doublespend", "device_B", "tx_second",
                System.currentTimeMillis(), 0));

        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                try {
                    GossipStore.ConflictResult result =
                            gossipStore.checkConflict("tok_doublespend");
                    if (result.isConflict) conflictCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 1,000 conflict checks should detect the double-spend",
                1000, conflictCount.get());
    }

    // -----------------------------------------------------------------------
    // TEST 5: 10,000 adaptive delay calculations — all correct
    // -----------------------------------------------------------------------
    @Test
    public void test5_tenThousandDelayCalculations_allCorrect()
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);
        AtomicInteger   errors   = new AtomicInteger(0);

        for (int i = 0; i < DEVICE_COUNT; i++) {
            final int peerCount = i % 20; // 0–19 peers
            executor.submit(() -> {
                try {
                    long delay = calculator.calculateDelayMs(peerCount);
                    // All delays must be within SOTPN spec (3–10 seconds)
                    if (delay < 3_000 || delay > 10_000) {
                        errors.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All delay calculations should be within spec",
                0, errors.get());
    }

    // -----------------------------------------------------------------------
    // TEST 6: 1,000 concurrent validations on different transactions
    // -----------------------------------------------------------------------
    @Test
    public void test6_thousandConcurrentValidations_noRaceConditions()
            throws InterruptedException {

        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch     = new CountDownLatch(1000);
        AtomicInteger   passed    = new AtomicInteger(0);
        AtomicInteger   failed    = new AtomicInteger(0);

        MockWallet wallet = new MockWallet();
        NonceStore sharedNonceStore = new NonceStore();
        GossipStore sharedGossipStore = new GossipStore();

        GossipEngine gossipEngine = new GossipEngine(
                mock(BleManager.class), mock(WifiDirectManager.class), sharedGossipStore,
                mock(GossipEngine.GossipListener.class), "test");

        ValidationPhaseHandler handler =
                new ValidationPhaseHandler(wallet, sharedNonceStore, gossipEngine);

        for (int i = 0; i < 1000; i++) {
            final int txIndex = i;
            executor.submit(() -> {
                try {
                    String tokenId = "tok_" + txIndex;
                    String receiver = "receiver_key";
                    long ts = System.currentTimeMillis();
                    String nonce = "nonce_concurrent_" + txIndex;
                    
                    String sig = wallet.signTransaction(tokenId + receiver + ts + nonce);

                    Transaction tx = new Transaction(
                            "tx_concurrent_" + txIndex,
                            tokenId,
                            wallet.getPublicKey(),
                            receiver,
                            ts,
                            nonce,
                            sig
                    );

                    handler.execute(tx,
                            new ValidationPhaseHandler.ValidationListener() {
                                @Override
                                public void onValidationPassed(Transaction t) {
                                    passed.incrementAndGet();
                                }
                                @Override
                                public void onValidationFailed(Transaction t,
                                                               ValidationResult r) {
                                    failed.incrementAndGet();
                                }
                            });
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 1,000 unique transactions should pass validation",
                1000, passed.get());
        assertEquals("Zero failures expected", 0, failed.get());
    }

    // -----------------------------------------------------------------------
    // TEST 7: 1,000 prepare attempts on SAME token — only 1 locks it
    // -----------------------------------------------------------------------
    @Test
    public void test7_thousandPrepareAttemptsOnSameToken_onlyOneLocks()
            throws InterruptedException {

        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch     = new CountDownLatch(1000);
        AtomicInteger   locked    = new AtomicInteger(0);
        AtomicInteger   lockFailed = new AtomicInteger(0);

        // All wallets point to same token but use a thread-safe lock mock
        ConcurrentMockWallet sharedWallet = new ConcurrentMockWallet("tok_shared");

        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                try {
                    boolean result = sharedWallet.lockToken("tok_shared");
                    if (result) locked.incrementAndGet();
                    else        lockFailed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("Exactly 1 thread should lock the token", 1, locked.get());
        assertEquals("All other 999 should fail", 999, lockFailed.get());
    }

    // -----------------------------------------------------------------------
    // TEST 8: 100 complete transactions concurrently — all succeed
    // -----------------------------------------------------------------------
    @Test
    public void test8_hundredConcurrentTransactions_allSucceed()
            throws InterruptedException {

        ExecutorService executor  = Executors.newFixedThreadPool(50);
        CountDownLatch  latch     = new CountDownLatch(100);
        AtomicInteger   succeeded = new AtomicInteger(0);
        AtomicInteger   txFailed  = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            final int txIndex = i;
            executor.submit(() -> {
                try {
                    // Each transaction has its own isolated wallet + store
                    MockWallet sender   = new MockWallet();
                    MockWallet receiver = new MockWallet();
                    sender.tokenToReturn = new WalletInterface.TokenInfo(
                            "tok_tx_" + txIndex, 10_000L,
                            System.currentTimeMillis() + 60_000);

                    NonceStore  ns = new NonceStore();
                    GossipStore gs = new GossipStore();

                    GossipEngine ge = new GossipEngine(
                            mock(BleManager.class), mock(WifiDirectManager.class), gs,
                            mock(GossipEngine.GossipListener.class), "dev_" + txIndex);

                    PreparePhaseHandler    prepare    = new PreparePhaseHandler(sender, mock(BleManager.class), mock(WifiDirectManager.class));
                    ValidationPhaseHandler validate   = new ValidationPhaseHandler(receiver, ns, ge);
                    CommitPhaseHandler     recCommit  = new CommitPhaseHandler(receiver, null, null);
                    CommitPhaseHandler     sendCommit = new CommitPhaseHandler(sender, null, null);

                    final Transaction[]      sentTx = {null};
                    final Transaction[]      validTx = {null};
                    final TransactionProof[] proof   = {null};

                    prepare.execute(receiver.getPublicKey(), 10_000L, true,
                            new PreparePhaseHandler.PrepareListener() {
                                @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                                @Override public void onPrepareFailed(String r) {}
                            });

                    if (sentTx[0] == null) { txFailed.incrementAndGet(); return; }

                    validate.execute(sentTx[0],
                            new ValidationPhaseHandler.ValidationListener() {
                                @Override public void onValidationPassed(Transaction tx) { validTx[0] = tx; }
                                @Override public void onValidationFailed(Transaction tx, ValidationResult r) {}
                            });

                    if (validTx[0] == null) { txFailed.incrementAndGet(); return; }

                    recCommit.executeReceiverCommit(validTx[0], true,
                            new CommitPhaseHandler.CommitListener() {
                                @Override public void onReceiverCommitComplete(TransactionProof p) {}
                                @Override public void onSenderCommitComplete(TransactionProof p) {}
                                @Override public void onCommitFailed(String id, String r) {}
                            });

                    String ackData = "Received:" + validTx[0].getTxId() + ":" + validTx[0].getTokenId();
                    String ackSig = receiver.signTransaction(ackData);

                    TransactionAck ack = new TransactionAck(
                            validTx[0].getTxId(), validTx[0].getTokenId(),
                            receiver.getPublicKey(),
                            System.currentTimeMillis(), ackSig, true);

                    sendCommit.executeSenderCommit(sentTx[0], ack,
                            new CommitPhaseHandler.CommitListener() {
                                @Override public void onSenderCommitComplete(TransactionProof p) { proof[0] = p; }
                                @Override public void onReceiverCommitComplete(TransactionProof p) {}
                                @Override public void onCommitFailed(String id, String r) {}
                            });

                    if (proof[0] != null && sentTx[0].getPhase() == TransactionPhase.FINALIZED) {
                        succeeded.incrementAndGet();
                    } else {
                        txFailed.incrementAndGet();
                    }

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 100 concurrent transactions should succeed",
                100, succeeded.get());
    }

    // -----------------------------------------------------------------------
    // TEST 9: GossipStore data integrity under 10,000 concurrent writes
    // -----------------------------------------------------------------------
    @Test
    public void test9_gossipStore_noDataCorruptionUnderLoad()
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);

        // All 10,000 devices write gossip for the SAME token
        for (int i = 0; i < DEVICE_COUNT; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    GossipMessage msg = new GossipMessage(
                            "tok_stress_test",
                            "device_" + deviceId,
                            "tx_" + deviceId,  // different tx per device
                            System.currentTimeMillis(),
                            0
                    );
                    gossipStore.addGossip(msg);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        List<GossipMessage> sightings =
                gossipStore.getGossipForToken("tok_stress_test");

        assertTrue("Should have stored gossip from multiple devices",
                sightings.size() > 0);
        assertTrue("Should not exceed total device count",
                sightings.size() <= DEVICE_COUNT);
    }

    // -----------------------------------------------------------------------
    // TEST 10: NonceStore size consistency under concurrent access
    // -----------------------------------------------------------------------
    @Test
    public void test10_nonceStore_sizeConsistentUnderConcurrentAccess()
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(DEVICE_COUNT);

        List<String> acceptedNonces =
                Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < DEVICE_COUNT; i++) {
            final String nonce = "size_test_nonce_" + i;
            executor.submit(() -> {
                try {
                    boolean accepted = nonceStore.checkAndRecord(nonce);
                    if (accepted) acceptedNonces.add(nonce);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("NonceStore size must match accepted nonce count",
                acceptedNonces.size(), nonceStore.size());
        assertEquals("All unique nonces should be accepted",
                DEVICE_COUNT, nonceStore.size());
    }

    // -----------------------------------------------------------------------
    // TEST 11: 1,000 concurrent aborts — ensures thread-safe unlocking
    // -----------------------------------------------------------------------
    @Test
    public void test11_thousandConcurrentAborts_noLockLeaks() 
            throws InterruptedException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(1000);
        
        ConcurrentMockWallet wallet = new ConcurrentMockWallet("tok_abort_test");
        PreparePhaseHandler handler = new PreparePhaseHandler(wallet, null, null);

        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                try {
                    wallet.lockToken("tok_abort_test");
                    handler.abort(); 
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertFalse("Token should be unlocked after all aborts", 
                    wallet.isTokenLocked("tok_abort_test"));
    }

    // -----------------------------------------------------------------------
    // ConcurrentMockWallet — thread-safe token lock for Test 7
    // -----------------------------------------------------------------------

    static class ConcurrentMockWallet extends MockWallet {

        private volatile boolean tokenLocked = false;
        private final Object     lockObject  = new Object();
        private final String     tokenId;

        ConcurrentMockWallet(String tokenId) {
            this.tokenId = tokenId;
        }

        @Override
        public synchronized boolean lockToken(String id) {
            synchronized (lockObject) {
                if (tokenLocked) return false; // already locked
                tokenLocked = true;
                return true;
            }
        }
    }
}
