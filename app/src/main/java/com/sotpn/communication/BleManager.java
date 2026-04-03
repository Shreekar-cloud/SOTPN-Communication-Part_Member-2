package com.sotpn.communication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import java.util.List;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 */
public class BleManager {

    private static final String TAG = "BleManager";

    private final Context        context;
    private final BleCallback    callback;
    private final BluetoothAdapter adapter;

    private final BleAdvertiser  advertiser;
    private final BleScanner     scanner;
    private final BleDataTransfer dataTransfer;

    private String myDeviceId;

    public BleManager(Context context, BleCallback callback) {
        this.context  = context;
        this.callback = callback;

        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = (bm != null) ? bm.getAdapter() : null;

        this.advertiser   = new BleAdvertiser(adapter, callback);
        this.scanner      = new BleScanner(adapter, callback);
        this.dataTransfer = new BleDataTransfer(context, callback);
    }

    public boolean isBluetoothAvailable() {
        return adapter != null && adapter.isEnabled();
    }

    public void startDiscovery(String deviceId) {
        this.myDeviceId = deviceId;
        advertiser.startAdvertising(deviceId);
        scanner.startScan();
        Log.i(TAG, "BLE discovery started for device: " + deviceId);
    }

    public void stopDiscovery() {
        advertiser.stopAdvertising();
        scanner.stopScan();
        Log.i(TAG, "BLE discovery stopped");
    }

    public void stopAll() {
        stopDiscovery();
        dataTransfer.disconnect();
        Log.i(TAG, "BLE fully stopped");
    }

    public void connectToPeer(BleDeviceInfo device) {
        dataTransfer.connect(device.getBluetoothDevice());
    }

    public void disconnectFromPeer() {
        dataTransfer.disconnect();
    }

    public void sendTransaction(Transaction transaction) {
        dataTransfer.sendTransaction(transaction);
    }

    public void sendAck(TransactionAck ack) {
        dataTransfer.sendAck(ack);
    }

    /**
     * Broadcasts a gossip protocol string to nearby peers.
     * FIX: No longer rebuilds the message header internally to prevent corruption.
     */
    public void broadcastGossip(String gossipWireString) {
        Log.d(TAG, "Broadcasting gossip payload: " + gossipWireString);
        // In a full implementation, we iterate all active GATT connections.
        // For now, we utilize the primary data transfer channel.
        dataTransfer.sendGossip(gossipWireString);
    }

    public int getNearbyDeviceCount() {
        return scanner.getNearbyDeviceCount();
    }

    public List<BleDeviceInfo> getNearbyDevices() {
        return scanner.getDiscoveredDevices();
    }
}
