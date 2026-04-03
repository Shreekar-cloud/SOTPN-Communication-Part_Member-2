package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
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
public class DeviceScalabilityTest {

    private static final int TIMEOUT_SECONDS  = 120;
    private static final int THREAD_POOL_SIZE = 200;

    @Before
    public void setUp() {}

    // -----------------------------------------------------------------------
    // TEST 1: 1,000 devices — all complete full transaction flow
    // -----------------------------------------------------------------------
    @Test
    public void test1_thousandDevices_allCompleteSuccessfully()
            throws InterruptedException {

        int deviceCount = 1_000;
        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch     = new CountDownLatch(deviceCount);
        AtomicInteger   succeeded = new AtomicInteger(0);
        AtomicInteger   failed    = new AtomicInteger(0);

        for (int i = 0; i < deviceCount; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    boolean result = runSingleTransaction(deviceId);
                    if (result) succeeded.incrementAndGet();
                    else        failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 1,000 devices should complete transactions",
                deviceCount, succeeded.get());
        assertEquals("Zero failures", 0, failed.get());
    }

    // -----------------------------------------------------------------------
    // TEST 2: 5,000 devices — all complete full transaction flow
    // -----------------------------------------------------------------------
    @Test
    public void test2_fiveThousandDevices_allCompleteSuccessfully()
            throws InterruptedException {

        int deviceCount = 5_000;
        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch     = new CountDownLatch(deviceCount);
        AtomicInteger   succeeded = new AtomicInteger(0);
        AtomicInteger   failed    = new AtomicInteger(0);

        for (int i = 0; i < deviceCount; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    boolean result = runSingleTransaction(deviceId);
                    if (result) succeeded.incrementAndGet();
                    else        failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 5,000 devices should complete",
                deviceCount, succeeded.get());
        assertEquals("Zero failures", 0, failed.get());
    }

    // -----------------------------------------------------------------------
    // TEST 3: 10,000 devices sending gossip — mesh propagation
    // -----------------------------------------------------------------------
    @Test
    public void test3_tenThousandDevices_gossipMesh_noDataLoss()
            throws InterruptedException {

        int deviceCount = 10_000;
        GossipStore sharedStore = new GossipStore();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(deviceCount);

        for (int i = 0; i < deviceCount; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    sharedStore.addGossip(new GossipMessage(
                            "tok_mesh_" + (deviceId % 500), // 500 unique tokens
                            "device_" + deviceId,
                            "tx_" + deviceId,
                            System.currentTimeMillis(),
                            0
                    ));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("Should track tokens from 10k devices",
                sharedStore.getTrackedTokenCount() > 0);
        assertTrue("Token count should not exceed unique tokens",
                sharedStore.getTrackedTokenCount() <= 500);
    }

    // -----------------------------------------------------------------------
    // TEST 4: 50,000 nonces — extreme scale test
    // -----------------------------------------------------------------------
    @Test
    public void test4_fiftyThousandNonces_handledCorrectly()
            throws InterruptedException {

        int nonceCount = 50_000;
        NonceStore store = new NonceStore();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(nonceCount);
        AtomicInteger   accepted = new AtomicInteger(0);

        for (int i = 0; i < nonceCount; i++) {
            final String nonce = "extreme_nonce_" + i;
            executor.submit(() -> {
                try {
                    if (store.checkAndRecord(nonce)) accepted.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 50,000 unique nonces should be accepted",
                nonceCount, accepted.get());
        assertEquals("Store size should match accepted count",
                nonceCount, store.size());
    }

    // -----------------------------------------------------------------------
    // TEST 5: 100 devices in same mesh — double-spend detected correctly
    // -----------------------------------------------------------------------
    @Test
    public void test5_hundredDevicesInMesh_doubleSpendDetected()
            throws InterruptedException {

        GossipStore store = new GossipStore();
        int deviceCount   = 100;

        // First add the two conflicting sightings
        store.addGossip(new GossipMessage(
                "tok_doublespend_mesh", "device_origin_A",
                "tx_ORIGINAL", System.currentTimeMillis(), 0));
        store.addGossip(new GossipMessage(
                "tok_doublespend_mesh", "device_origin_B",
                "tx_DUPLICATE", System.currentTimeMillis(), 0));

        ExecutorService executor      = Executors.newFixedThreadPool(50);
        CountDownLatch  latch         = new CountDownLatch(deviceCount);
        AtomicInteger   conflictsSeen = new AtomicInteger(0);

        // 100 devices all check for conflict simultaneously
        for (int i = 0; i < deviceCount; i++) {
            executor.submit(() -> {
                try {
                    GossipStore.ConflictResult result =
                            store.checkConflict("tok_doublespend_mesh");
                    if (result.isConflict) conflictsSeen.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 100 devices must detect the double-spend",
                deviceCount, conflictsSeen.get());
    }

    // -----------------------------------------------------------------------
    // TEST 6: 1,000 wallets created simultaneously
    // -----------------------------------------------------------------------
    @Test
    public void test6_thousandWallets_createdSimultaneously()
            throws InterruptedException {

        int walletCount  = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(walletCount);
        AtomicInteger   created  = new AtomicInteger(0);

        for (int i = 0; i < walletCount; i++) {
            final int walletId = i;
            executor.submit(() -> {
                try {
                    MockWallet w = new MockWallet();
                    w.tokenToReturn = new WalletInterface.TokenInfo(
                            "tok_wallet_" + walletId, 10_000L,
                            System.currentTimeMillis() + 60_000);
                    if (w.getPublicKey() != null
                            && w.getBalance() > 0
                            && w.generateNonce() != null) {
                        created.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("All 1,000 wallets should be created successfully",
                walletCount, created.get());
    }

    // -----------------------------------------------------------------------
    // TEST 7: 10,000 transactions created and serialized
    // -----------------------------------------------------------------------
    @Test
    public void test7_tenThousandTransactions_serializedSuccessfully() {
        int count    = 10_000;
        int errors   = 0;
        long startMs = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            try {
                Transaction tx = new Transaction(
                        "tx_scale_" + i, "tok_scale_" + i,
                        "sender_" + i, "receiver_" + i,
                        System.currentTimeMillis(),
                        "nonce_scale_" + i, "sig_scale_" + i);
                String json = tx.toJson().toString();
                if (json == null || json.isEmpty()) errors++;
            } catch (Exception e) {
                errors++;
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        assertEquals("Zero serialization errors", 0, errors);
        assertTrue("Should complete under 10 seconds", elapsed < 10_000);
    }

    // -----------------------------------------------------------------------
    // TEST 8: Burst of 500 prepare attempts in under 2 seconds
    // -----------------------------------------------------------------------
    @Test
    public void test8_burstOf500PrepareAttempts_completedInTime()
            throws InterruptedException {

        int burstCount = 500;
        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch     = new CountDownLatch(burstCount);
        AtomicInteger   completed = new AtomicInteger(0);

        long startMs = System.currentTimeMillis();

        for (int i = 0; i < burstCount; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    MockWallet wallet = new MockWallet();
                    wallet.tokenToReturn = new WalletInterface.TokenInfo(
                            "tok_burst_" + deviceId, 10_000L,
                            System.currentTimeMillis() + 60_000);

                    PreparePhaseHandler handler =
                            new PreparePhaseHandler(wallet, null, null);

                    handler.execute("receiver_key", 10_000L, true,
                            new PreparePhaseHandler.PrepareListener() {
                                @Override
                                public void onPrepareSent(Transaction tx) {
                                    completed.incrementAndGet();
                                }
                                @Override
                                public void onPrepareFailed(String reason) {}
                            });
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        long elapsed = System.currentTimeMillis() - startMs;
        assertTrue("At least 490/500 burst prepares should complete (98%)",
                completed.get() >= 490);
        assertTrue("Burst should complete under 30 seconds", elapsed < 30_000);
    }

    // -----------------------------------------------------------------------
    // TEST 9: 10,000 validation checks in parallel
    // -----------------------------------------------------------------------
    @Test
    public void test9_tenThousandValidations_inParallel()
            throws InterruptedException {

        int validationCount = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(validationCount);
        AtomicInteger   passed   = new AtomicInteger(0);
        AtomicInteger   failed   = new AtomicInteger(0);

        MockWallet wallet = new MockWallet();
        NonceStore sharedNonceStore = new NonceStore();
        GossipStore sharedGossipStore = new GossipStore();

        com.sotpn.communication.GossipEngine gossipEngine =
                new com.sotpn.communication.GossipEngine(
                        mock(com.sotpn.communication.BleManager.class), 
                        mock(com.sotpn.communication.WifiDirectManager.class), 
                        sharedGossipStore,
                        mock(com.sotpn.communication.GossipEngine.GossipListener.class), "test");

        ValidationPhaseHandler handler =
                new ValidationPhaseHandler(wallet, sharedNonceStore, gossipEngine);

        for (int i = 0; i < validationCount; i++) {
            final int txIndex = i;
            executor.submit(() -> {
                try {
                    String tokenId = "tok_val_" + txIndex;
                    String senderKey = "sender_" + txIndex;
                    String receiverKey = "receiver_key";
                    long timestamp = System.currentTimeMillis();
                    String nonce = "nonce_val_" + txIndex;
                    
                    // Construct signature manually to match MockWallet logic
                    String data = tokenId + receiverKey + timestamp + nonce;
                    String sig = "sig:" + senderKey + ":" + data.hashCode();

                    Transaction tx = new Transaction(
                            "tx_val_" + txIndex,
                            tokenId,
                            senderKey,
                            receiverKey,
                            timestamp,
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

        assertEquals("All 10,000 validations should pass",
                validationCount, passed.get());
        assertEquals("Zero unexpected failures", 0, failed.get());
    }

    // -----------------------------------------------------------------------
    // TEST 10: Memory check — 100,000 objects without OutOfMemory
    // -----------------------------------------------------------------------
    @Test
    public void test10_hundredThousandObjects_noOutOfMemory() {
        int objectCount = 100_000;
        long startMs    = System.currentTimeMillis();

        List<Transaction>      txList    = new ArrayList<>(objectCount);
        List<TransactionProof> proofList = new ArrayList<>(objectCount);

        for (int i = 0; i < objectCount; i++) {
            txList.add(new Transaction(
                    "tx_" + i, "tok_" + i,
                    "sender", "receiver",
                    System.currentTimeMillis(),
                    "nonce_" + i, "sig_" + i));

            proofList.add(new TransactionProof(
                    "tx_" + i, "tok_" + i,
                    "sender", "receiver",
                    System.currentTimeMillis(),
                    "nonce_" + i, "tx_sig_" + i, "ack_sig_" + i,
                    System.currentTimeMillis(),
                    TransactionProof.Role.SENDER));
        }

        long elapsed = System.currentTimeMillis() - startMs;
        long usedMem = (Runtime.getRuntime().totalMemory()
                - Runtime.getRuntime().freeMemory()) / 1024 / 1024;

        assertEquals("All transactions created", objectCount, txList.size());
        assertEquals("All proofs created",       objectCount, proofList.size());
        assertTrue("Should complete under 30 seconds", elapsed < 30_000);
        assertTrue("Memory usage should be under 512MB", usedMem < 512);
    }

    // -----------------------------------------------------------------------
    // Helper — run a complete single transaction for one device
    // -----------------------------------------------------------------------
    private boolean runSingleTransaction(int deviceId) {
        MockWallet sender   = new MockWallet();
        MockWallet receiver = new MockWallet();

        sender.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_device_" + deviceId, 10_000L,
                System.currentTimeMillis() + 60_000);

        NonceStore  ns = new NonceStore();
        GossipStore gs = new GossipStore();

        com.sotpn.communication.GossipEngine ge =
                new com.sotpn.communication.GossipEngine(
                        mock(com.sotpn.communication.BleManager.class), 
                        mock(com.sotpn.communication.WifiDirectManager.class), 
                        gs,
                        mock(com.sotpn.communication.GossipEngine.GossipListener.class), 
                        receiver.getPublicKey());

        PreparePhaseHandler    prepare   = new PreparePhaseHandler(sender, null, null);
        ValidationPhaseHandler validate  = new ValidationPhaseHandler(receiver, ns, ge);
        CommitPhaseHandler     recCommit = new CommitPhaseHandler(receiver, null, null);
        CommitPhaseHandler     sndCommit = new CommitPhaseHandler(sender, null, null);

        final Transaction[]      sentTx = {null};
        final Transaction[]      validTx = {null};
        final TransactionProof[] proof   = {null};

        prepare.execute(receiver.getPublicKey(), 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                    @Override public void onPrepareFailed(String r) {}
                });

        if (sentTx[0] == null) return false;

        validate.execute(sentTx[0],
                new ValidationPhaseHandler.ValidationListener() {
                    @Override public void onValidationPassed(Transaction tx) { validTx[0] = tx; }
                    @Override public void onValidationFailed(Transaction tx, ValidationResult r) {}
                });

        if (validTx[0] == null) return false;

        recCommit.executeReceiverCommit(validTx[0], true,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        String ackData = "Received:" + validTx[0].getTxId() + ":" + validTx[0].getTokenId();
        String ackSig = receiver.signTransaction(ackData);

        com.sotpn.model.TransactionAck ack =
                new com.sotpn.model.TransactionAck(
                        validTx[0].getTxId(), validTx[0].getTokenId(),
                        receiver.getPublicKey(),
                        System.currentTimeMillis(), ackSig, true);

        sndCommit.executeSenderCommit(sentTx[0], ack,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) { proof[0] = p; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        return proof[0] != null;
    }
}
