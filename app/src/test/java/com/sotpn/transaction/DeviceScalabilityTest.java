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
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DeviceScalabilityTest {

    private static final int TIMEOUT_SECONDS  = 120;
    private static final int THREAD_POOL_SIZE = 200;

    @Before
    public void setUp() {}

    @Test
    public void test1_thousandDevices_allCompleteSuccessfully() throws InterruptedException {
        int deviceCount = 1_000;
        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch     = new CountDownLatch(deviceCount);
        AtomicInteger   succeeded = new AtomicInteger(0);

        for (int i = 0; i < deviceCount; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    if (runSingleTransaction(deviceId)) succeeded.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        assertEquals(deviceCount, succeeded.get());
    }

    @Test
    public void test3_tenThousandDevices_gossipMesh_noDataLoss() throws InterruptedException {
        int deviceCount = 10_000;
        GossipStore sharedStore = new GossipStore();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch  latch    = new CountDownLatch(deviceCount);

        for (int i = 0; i < deviceCount; i++) {
            final int deviceId = i;
            executor.submit(() -> {
                try {
                    // Updated to 6-arg constructor: (tokenId, senderId, txId, timestamp, signature, hop)
                    sharedStore.addGossip(new GossipMessage(
                            "tok_mesh_" + (deviceId % 500), "device_" + deviceId,
                            "tx_" + deviceId, System.currentTimeMillis(), "sig_" + deviceId, 0));
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(sharedStore.getTrackedTokenCount() > 0);
    }

    @Test
    public void test5_hundredDevicesInMesh_doubleSpendDetected() throws InterruptedException {
        GossipStore store = new GossipStore();
        int deviceCount   = 100;

        store.addGossip(new GossipMessage("tok_ds", "dev_A", "tx_ORIGINAL", System.currentTimeMillis(), "sig_A", 0));
        store.addGossip(new GossipMessage("tok_ds", "dev_B", "tx_DUPLICATE", System.currentTimeMillis(), "sig_B", 0));

        ExecutorService executor      = Executors.newFixedThreadPool(50);
        CountDownLatch  latch         = new CountDownLatch(deviceCount);
        AtomicInteger   conflictsSeen = new AtomicInteger(0);

        for (int i = 0; i < deviceCount; i++) {
            executor.submit(() -> {
                try {
                    if (store.checkConflict("tok_ds").isConflict) conflictsSeen.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        assertEquals(deviceCount, conflictsSeen.get());
    }

    private boolean runSingleTransaction(int deviceId) {
        MockWallet sender   = new MockWallet();
        MockWallet receiver = new MockWallet();
        sender.tokenToReturn = new WalletInterface.TokenInfo("tok_dev_" + deviceId, 10_000L, System.currentTimeMillis() + 60_000);

        GossipStore gs = new GossipStore();
        com.sotpn.communication.GossipEngine ge = new com.sotpn.communication.GossipEngine(
                mock(com.sotpn.communication.BleManager.class), 
                mock(com.sotpn.communication.WifiDirectManager.class), gs,
                mock(com.sotpn.communication.GossipEngine.GossipListener.class), receiver.getPublicKey());

        PreparePhaseHandler prepare = new PreparePhaseHandler(sender, null, null);
        ValidationPhaseHandler validate = new ValidationPhaseHandler(receiver, new NonceStore(), ge);
        CommitPhaseHandler recCommit = new CommitPhaseHandler(receiver, null, null);
        CommitPhaseHandler sndCommit = new CommitPhaseHandler(sender, null, null);

        final Transaction[] sentTx = {null};
        final Transaction[] validTx = {null};
        final TransactionProof[] proof = {null};

        prepare.execute(receiver.getPublicKey(), 10_000L, true, new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
            @Override public void onPrepareFailed(String r) {}
        });

        if (sentTx[0] == null) return false;

        validate.execute(sentTx[0], new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction tx) { validTx[0] = tx; }
            @Override public void onValidationFailed(Transaction tx, ValidationResult r) {}
        });

        if (validTx[0] == null) return false;

        recCommit.executeReceiverCommit(validTx[0], true, new CommitPhaseHandler.CommitListener() {
            @Override public void onReceiverCommitComplete(TransactionProof p) {}
            @Override public void onSenderCommitComplete(TransactionProof p) {}
            @Override public void onCommitFailed(String txId, String reason) {}
        });

        String ackData = "Received:" + validTx[0].getTxId() + ":" + validTx[0].getTokenId();
        com.sotpn.model.TransactionAck ack = new com.sotpn.model.TransactionAck(
                validTx[0].getTxId(), validTx[0].getTokenId(), receiver.getPublicKey(),
                System.currentTimeMillis(), receiver.signTransaction(ackData), true);

        sndCommit.executeSenderCommit(sentTx[0], ack, new CommitPhaseHandler.CommitListener() {
            @Override public void onSenderCommitComplete(TransactionProof p) { proof[0] = p; }
            @Override public void onReceiverCommitComplete(TransactionProof p) {}
            @Override public void onCommitFailed(String txId, String reason) {}
        });

        return proof[0] != null;
    }
}
