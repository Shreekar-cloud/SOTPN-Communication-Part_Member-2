package com.sotpn.transaction;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sotpn.communication.BleCallback;
import com.sotpn.communication.BleDeviceInfo;
import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectCallback;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.communication.WifiDirectBroadcastReceiver;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;

import java.util.List;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 */
public class TransactionManager implements BleCallback, WifiDirectCallback {

    private static final String TAG = "TransactionManager";

    private final WalletInterface        wallet;
    private final BleManager             bleManager;
    private final WifiDirectManager      wifiManager;
    private final PreparePhaseHandler    prepareHandler;
    private final ValidationPhaseHandler validationHandler;
    private final AdaptiveDelayHandler   delayHandler;
    private final CommitPhaseHandler     commitHandler;
    private final GossipEngine           gossipEngine;
    private final Handler                mainHandler;
    private final TransactionListener    listener;

    private Transaction activeTransaction = null;
    private boolean usingBle = true;

    private final Object transactionLock = new Object();

    public interface TransactionListener {
        void onPhaseChanged(TransactionPhase phase, String message);
        void onDelayProgress(long remainingMs, long totalMs,
                             AdaptiveDelayCalculator.RiskLevel riskLevel);
        void onTransactionComplete(TransactionProof proof);
        void onTransactionFailed(String txId, String reason);
        void onPeerDiscovered(BleDeviceInfo device);
        void onPeerConnected(String peerMac);
        void onConflictDetected(GossipStore.ConflictResult conflict);
    }

    public TransactionManager(Context context,
                              WalletInterface wallet,
                              TransactionListener listener) {
        this.wallet    = wallet;
        this.listener  = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.bleManager  = new BleManager(context, this);
        this.wifiManager = new WifiDirectManager(context, this);

        NonceStore nonceStore = new NonceStore();
        GossipStore gossipStore = new GossipStore();
        this.gossipEngine = new GossipEngine(
                bleManager, wifiManager, gossipStore,
                new GossipEngine.GossipListener() {
                    @Override
                    public void onConflictDetected(GossipStore.ConflictResult result) {
                        handleConflict(result);
                    }
                    @Override
                    public void onGossipReceived(GossipMessage message) {
                        Log.d(TAG, "Gossip received: " + message);
                    }
                },
                wallet.getPublicKey()
        );

        AdaptiveDelayCalculator calculator = new AdaptiveDelayCalculator();

        this.prepareHandler    = new PreparePhaseHandler(wallet, bleManager, wifiManager);
        this.validationHandler = new ValidationPhaseHandler(wallet, nonceStore, gossipEngine);
        this.delayHandler      = new AdaptiveDelayHandler(calculator, gossipEngine, bleManager);
        this.commitHandler     = new CommitPhaseHandler(wallet, bleManager, wifiManager);

        Log.i(TAG, "TransactionManager initialized");
    }

    public void startDiscovery() {
        String deviceId = wallet.getPublicKey().substring(0, 8);
        bleManager.startDiscovery(deviceId);
        wifiManager.discoverPeers();
    }

    public void stopDiscovery() {
        bleManager.stopDiscovery();
        wifiManager.stopDiscovery();
    }

    public void connectToPeer(BleDeviceInfo device, boolean useBle) {
        this.usingBle = useBle;
        if (useBle) {
            bleManager.connectToPeer(device);
        } else {
            wifiManager.discoverPeers();
        }
    }

    public void startSend(String receiverPublicKey, long amountPaise) {
        synchronized (transactionLock) {
            if (activeTransaction != null) {
                Log.w(TAG, "Cannot start new send while transaction is active");
                return;
            }
            notifyPhaseChanged(TransactionPhase.PREPARE, "Preparing transaction...");

            prepareHandler.execute(
                    receiverPublicKey,
                    amountPaise,
                    usingBle,
                    new PreparePhaseHandler.PrepareListener() {
                        @Override
                        public void onPrepareSent(Transaction transaction) {
                            activeTransaction = transaction;
                            gossipEngine.recordLocalSighting(transaction.getTokenId(), transaction.getTxId(), transaction.getSignature());
                            notifyPhaseChanged(TransactionPhase.VALIDATING,
                                    "Transaction sent — awaiting confirmation...");
                        }

                        @Override
                        public void onPrepareFailed(String reason) {
                            notifyFailed(null, reason);
                        }
                    }
            );
        }
    }

    public void abort() {
        synchronized (transactionLock) {
            if (activeTransaction != null) {
                String txId = activeTransaction.getTxId();
                activeTransaction.setPhase(TransactionPhase.FAILED);
                prepareHandler.abort();
                delayHandler.abort();
                activeTransaction = null;
                notifyFailed(txId, "Transaction aborted");
            }
        }
    }

    public Transaction getActiveTransaction() {
        return activeTransaction;
    }

    public WifiDirectBroadcastReceiver getWifiDirectReceiver() {
        return wifiManager.getReceiver();
    }

    @Override
    public void onTransactionSent(String txId) {
        Log.i(TAG, "Transaction bytes sent for txId: " + txId);
    }

    @Override
    public void onTransactionSendFailed(String txId, String reason) {
        synchronized (transactionLock) {
            if (activeTransaction != null) {
                activeTransaction.setPhase(TransactionPhase.FAILED);
            }
            prepareHandler.abort();
            delayHandler.abort();
            activeTransaction = null;
            notifyFailed(txId, reason);
        }
    }

    @Override
    public void onAckReceived(TransactionAck ack) {
        if (ack == null) return;
        Log.i(TAG, "ACK received for txId: " + ack.getTxId());

        if (!ack.isAccepted()) {
            synchronized (transactionLock) {
                if (activeTransaction != null) {
                    activeTransaction.setPhase(TransactionPhase.FAILED);
                }
                prepareHandler.abort();
                delayHandler.abort();
                activeTransaction = null;
                notifyFailed(ack.getTxId(), "Transaction rejected by receiver");
            }
            return;
        }

        if (activeTransaction == null) {
            Log.w(TAG, "ACK received but no active transaction — ignoring");
            return;
        }

        // SOTPN SECURITY FIX: Verify TokenID match in ACK
        if (!ack.getTokenId().equals(activeTransaction.getTokenId())) {
            Log.e(TAG, "ACK tokenId mismatch");
            synchronized (transactionLock) {
                activeTransaction.setPhase(TransactionPhase.FAILED);
                prepareHandler.abort();
                delayHandler.abort();
                activeTransaction = null;
                notifyFailed(ack.getTxId(), "ACK tokenId mismatch");
            }
            return;
        }

        runSenderCommit(activeTransaction, ack);
    }

    @Override
    public void onAckSendFailed(String txId, String reason) {
        notifyFailed(txId, reason);
    }

    @Override
    public void onGossipReceived(String gossipMessage) {
        gossipEngine.handleIncomingGossip(gossipMessage);
    }

    @Override
    public void onDeviceDiscovered(BleDeviceInfo device) {
        mainHandler.post(() -> listener.onPeerDiscovered(device));
    }

    @Override
    public void onDeviceLost(String macAddress) {}

    @Override
    public void onConnected(String macAddress, int negotiatedMtu) {
        mainHandler.post(() -> listener.onPeerConnected(macAddress));
    }

    @Override
    public void onDisconnected(String macAddress, String reason) {
        synchronized (transactionLock) {
            if (activeTransaction != null && activeTransaction.getPhase() != TransactionPhase.FINALIZED) {
                Log.w(TAG, "Network lost mid-flow. Rolling back.");
                String txId = activeTransaction.getTxId();
                activeTransaction.setPhase(TransactionPhase.FAILED);
                prepareHandler.abort();
                delayHandler.abort();
                activeTransaction = null;
                notifyFailed(txId, "Disconnected: " + reason);
            }
        }
    }

    @Override
    public void onTransactionReceived(Transaction transaction, String senderMac) {
        synchronized (transactionLock) {
            if (activeTransaction != null) {
                if (activeTransaction.getTokenId().equals(transaction.getTokenId())) {
                    GossipStore.ConflictResult conflict = new GossipStore.ConflictResult(
                            true, transaction.getTokenId(), activeTransaction.getTxId(),
                            transaction.getTxId(), "BLE", "LOCAL_ACTIVE");
                    handleConflict(conflict);
                }
                return;
            }
            // SOTPN SECURITY FIX: Ignore transactions intended for other recipients
            if (!transaction.getReceiverPublicKey().equals(wallet.getPublicKey())) {
                Log.d(TAG, "Ignoring transaction meant for a different public key");
                return;
            }
            activeTransaction = transaction;
            gossipEngine.recordLocalSighting(transaction.getTokenId(), transaction.getTxId(), transaction.getSignature());
            runValidationPhase(transaction);
        }
    }

    @Override
    public void onGossipReceived(String gossipMessage, String fromMac) {
        onGossipReceived(gossipMessage);
    }

    @Override
    public void onBleError(String reason) {
        notifyFailed(activeTransaction != null ? activeTransaction.getTxId() : null, reason);
    }

    @Override
    public void onDiscoveryStarted() {}

    @Override
    public void onPeersAvailable(List<WifiP2pDevice> peers) {}

    @Override
    public void onDiscoveryFailed(String reason) {}

    @Override
    public void onConnected(WifiP2pInfo info) {
        mainHandler.post(() -> listener.onPeerConnected(info.groupOwnerAddress.getHostAddress()));
    }

    @Override
    public void onDisconnected() {
        synchronized (transactionLock) {
            if (activeTransaction != null && activeTransaction.getPhase() != TransactionPhase.FINALIZED) {
                String txId = activeTransaction.getTxId();
                activeTransaction.setPhase(TransactionPhase.FAILED);
                prepareHandler.abort();
                delayHandler.abort();
                activeTransaction = null;
                notifyFailed(txId, "WiFi Direct disconnected");
            }
        }
    }

    @Override
    public void onConnectionFailed(String reason) {
        notifyFailed(null, reason);
    }

    @Override
    public void onTransactionReceived(Transaction transaction) {
        synchronized (transactionLock) {
            if (activeTransaction != null) {
                if (activeTransaction.getTokenId().equals(transaction.getTokenId())) {
                    GossipStore.ConflictResult conflict = new GossipStore.ConflictResult(
                            true, transaction.getTokenId(), activeTransaction.getTxId(),
                            transaction.getTxId(), "WIFI", "LOCAL_ACTIVE");
                    handleConflict(conflict);
                }
                return;
            }
            // SOTPN SECURITY FIX: Ignore transactions intended for other recipients
            if (!transaction.getReceiverPublicKey().equals(wallet.getPublicKey())) {
                Log.d(TAG, "Ignoring transaction meant for a different public key");
                return;
            }
            activeTransaction = transaction;
            gossipEngine.recordLocalSighting(transaction.getTokenId(), transaction.getTxId(), transaction.getSignature());
            runValidationPhase(transaction);
        }
    }

    @Override
    public void onWiFiDirectError(String reason) {
        notifyFailed(activeTransaction != null ? activeTransaction.getTxId() : null, reason);
    }

    private void runValidationPhase(Transaction transaction) {
        notifyPhaseChanged(TransactionPhase.VALIDATING, "Validating...");
        validationHandler.execute(transaction, new ValidationPhaseHandler.ValidationListener() {
            @Override
            public void onValidationPassed(Transaction tx) {
                runDelayPhase(tx);
            }

            @Override
            public void onValidationFailed(Transaction tx, ValidationResult result) {
                synchronized (transactionLock) {
                    commitHandler.sendRejection(tx, result.getFailureMessage(), usingBle);
                    activeTransaction = null;
                    notifyFailed(tx.getTxId(), result.getFailureMessage());
                }
            }
        });
    }

    private void runDelayPhase(Transaction transaction) {
        notifyPhaseChanged(TransactionPhase.DELAYING, "Verifying...");
        delayHandler.execute(transaction, new AdaptiveDelayHandler.DelayListener() {
            @Override
            public void onDelayComplete(Transaction tx) {
                runReceiverCommit(tx);
            }

            @Override
            public void onDelayAborted(Transaction tx, GossipStore.ConflictResult conflict) {
                synchronized (transactionLock) {
                    commitHandler.sendRejection(tx, "Conflict", usingBle);
                    activeTransaction = null;
                    notifyFailed(tx.getTxId(), "Conflict detected");
                    mainHandler.post(() -> listener.onConflictDetected(conflict));
                }
            }

            @Override
            public void onDelayProgress(long remainingMs, long totalMs, AdaptiveDelayCalculator.RiskLevel risk) {
                mainHandler.post(() -> listener.onDelayProgress(remainingMs, totalMs, risk));
            }
        });
    }

    private void runReceiverCommit(Transaction transaction) {
        notifyPhaseChanged(TransactionPhase.COMMITTING, "Finalizing...");
        commitHandler.executeReceiverCommit(transaction, usingBle, new CommitPhaseHandler.CommitListener() {
            @Override
            public void onReceiverCommitComplete(TransactionProof proof) {
                synchronized (transactionLock) {
                    activeTransaction = null;
                    gossipEngine.stopBroadcasting();
                    notifyPhaseChanged(TransactionPhase.FINALIZED, "Success!");
                    mainHandler.post(() -> listener.onTransactionComplete(proof));
                }
            }

            @Override
            public void onSenderCommitComplete(TransactionProof proof) {}

            @Override
            public void onCommitFailed(String txId, String reason) {
                synchronized (transactionLock) {
                    activeTransaction = null;
                    notifyFailed(txId, reason);
                }
            }
        });
    }

    private void runSenderCommit(Transaction transaction, TransactionAck ack) {
        notifyPhaseChanged(TransactionPhase.COMMITTING, "Finalizing...");
        commitHandler.executeSenderCommit(transaction, ack, new CommitPhaseHandler.CommitListener() {
            @Override
            public void onSenderCommitComplete(TransactionProof proof) {
                synchronized (transactionLock) {
                    activeTransaction = null;
                    gossipEngine.stopBroadcasting();
                    notifyPhaseChanged(TransactionPhase.FINALIZED, "Success!");
                    mainHandler.post(() -> listener.onTransactionComplete(proof));
                }
            }

            @Override
            public void onReceiverCommitComplete(TransactionProof proof) {}

            @Override
            public void onCommitFailed(String txId, String reason) {
                synchronized (transactionLock) {
                    activeTransaction = null;
                    notifyFailed(txId, reason);
                }
            }
        });
    }

    private void handleConflict(GossipStore.ConflictResult result) {
        synchronized (transactionLock) {
            if (activeTransaction != null) {
                Log.e(TAG, "ABORTING TRANSACTION: Gossip Conflict Found for " + result.tokenId);
                activeTransaction.setPhase(TransactionPhase.FAILED);
                delayHandler.abort();
                prepareHandler.abort();
                activeTransaction = null;
            }
        }
        mainHandler.post(() -> listener.onConflictDetected(result));
    }

    private void notifyPhaseChanged(TransactionPhase phase, String message) {
        mainHandler.post(() -> listener.onPhaseChanged(phase, message));
    }

    private void notifyFailed(String txId, String reason) {
        mainHandler.post(() -> listener.onTransactionFailed(txId, reason));
    }
}
