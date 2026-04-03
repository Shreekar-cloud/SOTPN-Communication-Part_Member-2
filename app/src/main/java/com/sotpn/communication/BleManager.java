package com.sotpn.communication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import java.util.List;

/**
 * Top-level BLE orchestrator for SOTPN Member 2.
 *
 * This is the ONLY class the TransactionEngine talks to for BLE.
 * It wires together:
 *   - BleAdvertiser  → makes this device discoverable
 *   - BleScanner     → finds nearby SOTPN peers
 *   - BleDataTransfer → sends/receives transactions + ACKs (chunked GATT)
 *
 * -----------------------------------------------------------------------
 * TYPICAL SENDER FLOW:
 *   manager.startAdvertising(myDeviceId);
 *   manager.startScan();
 *   // wait for onDeviceDiscovered → pick target device
 *   manager.connectToPeer(device);
 *   // wait for onConnected
 *   manager.sendTransaction(transaction);
 *   // wait for onTransactionSent → then onAckReceived
 *   manager.stopAll();
 *
 * TYPICAL RECEIVER FLOW:
 *   manager.startAdvertising(myDeviceId);
 *   // wait for onTransactionReceived (peer connected to us via GATT server)
 *   // validate... then:
 *   manager.sendAck(ack);
 *   manager.stopAll();
 * -----------------------------------------------------------------------
 *
 * NOTE: Permissions required in AndroidManifest.xml:
 *   <uses-permission android:name="android.permission.BLUETOOTH" />
 *   <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 *   <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />        (API 31+)
 *   <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />     (API 31+)
 *   <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />   (API 31+)
 *   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />  (API < 31)
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

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Check whether Bluetooth is available and enabled on this device.
     * Call this before any other method.
     */
    public boolean isBluetoothAvailable() {
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Start advertising + scanning simultaneously.
     * Call this as soon as the user opens the Send or Receive screen.
     *
     * @param deviceId Short identifier for this device (e.g. first 8 chars of public key)
     */
    public void startDiscovery(String deviceId) {
        this.myDeviceId = deviceId;
        advertiser.startAdvertising(deviceId);
        scanner.startScan();
        Log.i(TAG, "BLE discovery started for device: " + deviceId);
    }

    /**
     * Stop advertising and scanning. Call when transaction is done
     * or the screen is closed.
     */
    public void stopDiscovery() {
        advertiser.stopAdvertising();
        scanner.stopScan();
        Log.i(TAG, "BLE discovery stopped");
    }

    /**
     * Stop everything and close any open GATT connection.
     */
    public void stopAll() {
        stopDiscovery();
        dataTransfer.disconnect();
        Log.i(TAG, "BLE fully stopped");
    }

    // -----------------------------------------------------------------------
    // Peer connection
    // -----------------------------------------------------------------------

    /**
     * Connect to a specific SOTPN peer discovered via scanning.
     * On success, BleCallback.onConnected() fires and you can call sendTransaction().
     *
     * @param device The discovered peer to connect to.
     */
    public void connectToPeer(BleDeviceInfo device) {
        Log.i(TAG, "Connecting to peer: " + device.getMacAddress());
        dataTransfer.connect(device.getBluetoothDevice());
    }

    /**
     * Disconnect from current peer.
     */
    public void disconnectFromPeer() {
        dataTransfer.disconnect();
    }

    // -----------------------------------------------------------------------
    // Send
    // -----------------------------------------------------------------------

    /**
     * Send a signed transaction to the connected peer.
     * Fires BleCallback.onTransactionSent() when all chunks are delivered.
     */
    public void sendTransaction(Transaction transaction) {
        Log.i(TAG, "Sending transaction: " + transaction.getTxId());
        dataTransfer.sendTransaction(transaction);
    }

    /**
     * Send a signed ACK back to the sender.
     * Called by receiver after Phase 3 (adaptive delay) completes.
     */
    public void sendAck(TransactionAck ack) {
        Log.i(TAG, "Sending ACK for txId: " + ack.getTxId());
        dataTransfer.sendAck(ack);
    }

    /**
     * Broadcast a gossip message to all connected SOTPN peers.
     * Format: "TOKEN_SEEN:<tokenId>:<senderMac>:<timestamp>"
     *
     * This is called during Phase 3 (adaptive delay) to propagate
     * "token seen" alerts so other devices can detect double-spend attempts.
     */
    public void broadcastGossip(String tokenId) {
        String gossip = buildGossipMessage(tokenId);
        Log.d(TAG, "Broadcasting gossip: " + gossip);
        for (BleDeviceInfo peer : scanner.getDiscoveredDevices()) {
            // In a real implementation we'd maintain multiple GATT connections.
            // For now, we use the single active connection.
            dataTransfer.sendGossip(gossip);
            break; // TODO: iterate all connected peers in Phase 2 implementation
        }
    }

    // -----------------------------------------------------------------------
    // Adaptive delay input
    // -----------------------------------------------------------------------

    /**
     * Returns the number of nearby SOTPN devices.
     * Used by AdaptiveDelayCalculator to decide delay duration:
     *   > 5 devices → 3 sec
     *   < 2 devices → 8–10 sec
     */
    public int getNearbyDeviceCount() {
        return scanner.getNearbyDeviceCount();
    }

    public List<BleDeviceInfo> getNearbyDevices() {
        return scanner.getDiscoveredDevices();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private String buildGossipMessage(String tokenId) {
        return "TOKEN_SEEN"
                + ":" + tokenId
                + ":" + myDeviceId
                + ":" + System.currentTimeMillis();
    }
}
