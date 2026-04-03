package com.sotpn.ui;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sotpn.communication.BleDeviceInfo;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.TransactionPhase;
import com.sotpn.transaction.AdaptiveDelayCalculator;
import com.sotpn.transaction.TransactionManager;
import com.sotpn.transaction.TransactionProof;
import com.sotpn.wallet.WalletInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : MainViewModel.java
 * Package  : com.sotpn.ui
 * Step     : Step 9 - ViewModel + UI
 * Status   : Complete
 */
public class MainViewModel extends AndroidViewModel
        implements TransactionManager.TransactionListener {

    private static final String TAG = "MainViewModel";

    private TransactionManager transactionManager;

    private final MutableLiveData<TransactionPhase> currentPhase =
            new MutableLiveData<>(TransactionPhase.PREPARE);

    private final MutableLiveData<String> statusMessage =
            new MutableLiveData<>("Ready");

    private final MutableLiveData<List<BleDeviceInfo>> nearbyPeers =
            new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<Boolean> isPeerConnected =
            new MutableLiveData<>(false);

    private final MutableLiveData<String> connectedPeerMac =
            new MutableLiveData<>(null);

    private final MutableLiveData<Long> delayRemainingMs =
            new MutableLiveData<>(0L);

    private final MutableLiveData<Long> delayTotalMs =
            new MutableLiveData<>(0L);

    private final MutableLiveData<AdaptiveDelayCalculator.RiskLevel> riskLevel =
            new MutableLiveData<>(AdaptiveDelayCalculator.RiskLevel.MEDIUM);

    private final MutableLiveData<TransactionProof> completedProof =
            new MutableLiveData<>(null);

    private final MutableLiveData<String> errorMessage =
            new MutableLiveData<>(null);

    private final MutableLiveData<GossipStore.ConflictResult> conflictResult =
            new MutableLiveData<>(null);

    private final MutableLiveData<Boolean> isTransacting =
            new MutableLiveData<>(false);

    public MainViewModel(@NonNull Application application) {
        super(application);
        Log.i(TAG, "MainViewModel created");
    }

    public void init(WalletInterface wallet) {
        if (transactionManager == null) {
            transactionManager = new TransactionManager(
                    getApplication(), wallet, this);
            Log.i(TAG, "TransactionManager initialized");
        }
    }

    public void startDiscovery() {
        if (transactionManager == null) return;
        transactionManager.startDiscovery();
        statusMessage.postValue("Scanning for nearby devices...");
    }

    public void stopDiscovery() {
        if (transactionManager == null) return;
        transactionManager.stopDiscovery();
    }

    public void connectToPeer(BleDeviceInfo device, boolean useBle) {
        if (transactionManager == null) return;
        statusMessage.postValue("Connecting to " + device.getDeviceName() + "...");
        transactionManager.connectToPeer(device, useBle);
    }

    public void sendMoney(String receiverPublicKey, int amountRupees) {
        if (transactionManager == null) return;
        isTransacting.postValue(true);
        errorMessage.postValue(null);
        completedProof.postValue(null);
        long amountPaise = amountRupees * 100L;
        transactionManager.startSend(receiverPublicKey, amountPaise);
    }

    public void cancelTransaction() {
        if (transactionManager == null) return;
        transactionManager.abort();
        isTransacting.postValue(false);
        statusMessage.postValue("Transaction cancelled");
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public void onPhaseChanged(TransactionPhase phase, String message) {
        Log.i(TAG, "Phase → " + phase + ": " + message);
        currentPhase.postValue(phase);
        statusMessage.postValue(message);
    }

    @Override
    public void onDelayProgress(long remainingMs, long totalMs,
                                AdaptiveDelayCalculator.RiskLevel risk) {
        delayRemainingMs.postValue(remainingMs);
        delayTotalMs.postValue(totalMs);
        riskLevel.postValue(risk);
    }

    @Override
    public void onTransactionComplete(TransactionProof proof) {
        Log.i(TAG, "Transaction complete: " + proof.getTxId());
        isTransacting.postValue(false);
        completedProof.postValue(proof);
        currentPhase.postValue(TransactionPhase.FINALIZED);
        statusMessage.postValue(proof.getRole() == TransactionProof.Role.SENDER
                ? "Payment sent successfully! ✅"
                : "Payment received successfully! ✅");
    }

    @Override
    public void onTransactionFailed(String txId, String reason) {
        Log.e(TAG, "Transaction failed: " + reason);
        isTransacting.postValue(false);
        currentPhase.postValue(TransactionPhase.FAILED);
        errorMessage.postValue(reason);
        statusMessage.postValue("Transaction failed");
    }

    @Override
    public void onPeerDiscovered(BleDeviceInfo device) {
        Log.i(TAG, "Peer discovered: " + device.getDeviceName());
        List<BleDeviceInfo> current = nearbyPeers.getValue();
        if (current == null) current = new ArrayList<>();
        
        boolean exists = false;
        for (BleDeviceInfo d : current) {
            if (d.getMacAddress().equals(device.getMacAddress())) {
                exists = true;
                break;
            }
        }
        
        if (!exists) {
            List<BleDeviceInfo> updated = new ArrayList<>(current);
            updated.add(device);
            nearbyPeers.postValue(updated);
        }
    }

    @Override
    public void onPeerConnected(String peerMac) {
        Log.i(TAG, "Peer connected: " + peerMac);
        isPeerConnected.postValue(true);
        connectedPeerMac.postValue(peerMac);
        statusMessage.postValue("Connected — ready to transact");
    }

    @Override
    public void onConflictDetected(GossipStore.ConflictResult conflict) {
        Log.w(TAG, "Conflict detected: " + conflict);
        conflictResult.postValue(conflict);
        errorMessage.postValue("Double-spend conflict detected for token: "
                + conflict.tokenId);
    }

    public LiveData<TransactionPhase> getCurrentPhase()    { return currentPhase; }
    public LiveData<String> getStatusMessage()             { return statusMessage; }
    public LiveData<List<BleDeviceInfo>> getNearbyPeers()  { return nearbyPeers; }
    public LiveData<Boolean> getIsPeerConnected()          { return isPeerConnected; }
    public LiveData<String> getConnectedPeerMac()          { return connectedPeerMac; }
    public LiveData<Long> getDelayRemainingMs()            { return delayRemainingMs; }
    public LiveData<Long> getDelayTotalMs()                { return delayTotalMs; }
    public LiveData<AdaptiveDelayCalculator.RiskLevel> getRiskLevel() { return riskLevel; }
    public LiveData<TransactionProof> getCompletedProof()  { return completedProof; }
    public LiveData<String> getErrorMessage()              { return errorMessage; }
    public LiveData<GossipStore.ConflictResult> getConflictResult() { return conflictResult; }
    public LiveData<Boolean> getIsTransacting()            { return isTransacting; }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (transactionManager != null) {
            transactionManager.stopDiscovery();
        }
        Log.i(TAG, "MainViewModel cleared");
    }
}
