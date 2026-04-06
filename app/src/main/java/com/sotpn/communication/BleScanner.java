package com.sotpn.communication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans for nearby SOTPN devices advertising SERVICE_UUID.
 *
 * Key responsibilities:
 *  - Filter scan results to SOTPN devices only (via SERVICE_UUID)
 *  - Track how many SOTPN devices are nearby (used by adaptive delay logic)
 *  - Fire onDeviceDiscovered / onDeviceLost through BleCallback
 *  - Auto-stop after SCAN_DURATION_MS (can also be stopped manually)
 *
 * Usage:
 *   bleScanner.startScan();           // discover peers
 *   int count = bleScanner.getNearbyDeviceCount();  // for adaptive delay
 *   bleScanner.stopScan();
 */
public class BleScanner {

    private static final String TAG = "BleScanner";

    private final BluetoothAdapter adapter;
    private final BleCallback       callback;
    private final Handler           mainHandler;

    private BluetoothLeScanner      leScanner;
    private boolean                 isScanning = false;

    /**
     * All currently visible SOTPN devices, keyed by MAC address.
     * Entries are added in onScanResult and removed when signal is lost.
     */
    private final Map<String, BleDeviceInfo> discoveredDevices = new HashMap<>();

    public BleScanner(BluetoothAdapter adapter, BleCallback callback) {
        this.adapter     = adapter;
        this.callback    = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Start scanning for SOTPN peers.
     * Automatically stops after BleConstants.SCAN_DURATION_MS.
     */
    public void startScan() {
        if (isScanning) {
            Log.w(TAG, "Already scanning");
            return;
        }
        if (adapter == null || !adapter.isEnabled()) {
            callback.onBleError("Bluetooth is disabled");
            return;
        }

        leScanner = adapter.getBluetoothLeScanner();
        if (leScanner == null) {
            callback.onBleError("BLE scanning not supported on this device");
            return;
        }

        // --- Filter: only SOTPN devices ---
        ScanFilter sotpnFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(sotpnFilter);

        // --- Settings: LOW_LATENCY for fast discovery during an active transaction ---
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0) // deliver results immediately, not batched
                .build();

        leScanner.startScan(filters, settings, scanCallback);
        isScanning = true;
        Log.i(TAG, "BLE scan started with filter for SERVICE_UUID: " + BleConstants.SERVICE_UUID);

        // Auto-stop removed to ensure discovery continues while screen is open.
        // mainHandler.postDelayed(this::stopScan, BleConstants.SCAN_DURATION_MS);
    }

    /**
     * Stop scanning immediately.
     */
    public void stopScan() {
        if (leScanner != null && isScanning) {
            leScanner.stopScan(scanCallback);
            isScanning = false;
            Log.d(TAG, "BLE scan stopped. Found " + discoveredDevices.size() + " devices.");
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Returns the number of SOTPN devices currently visible.
     * Used by adaptive delay logic:
     *   > 5 devices → 3 sec delay
     *   < 2 devices → 8–10 sec delay
     */
    public int getNearbyDeviceCount() {
        return discoveredDevices.size();
    }

    public List<BleDeviceInfo> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices.values());
    }

    public boolean isScanning() { return isScanning; }

    // -----------------------------------------------------------------------
    // ScanCallback
    // -----------------------------------------------------------------------

    private final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String mac  = result.getDevice().getAddress();
            int    rssi = result.getRssi();

            // Extract the device name safely (may be null if not advertised)
            String name = result.getScanRecord() != null
                    ? result.getScanRecord().getDeviceName()
                    : null;

            // SOTPN IMPROVEMENT: If name is missing (shrunken packet), parse ID from Manufacturer Data
            if (name == null && result.getScanRecord() != null) {
                byte[] manufData = result.getScanRecord().getManufacturerSpecificData(0xFFFF);
                if (manufData != null && manufData.length >= 4) {
                    // prefix "SOT" (3 bytes) + ID (>= 1 byte)
                    String id = new String(manufData, 3, manufData.length - 3);
                    name = BleConstants.DEVICE_NAME_PREFIX + id;
                }
            }

            boolean isNew = !discoveredDevices.containsKey(mac);

            BleDeviceInfo info = new BleDeviceInfo(
                    result.getDevice(),
                    name,
                    rssi,
                    System.currentTimeMillis()
            );
            discoveredDevices.put(mac, info);

            if (isNew) {
                Log.i(TAG, "FOUND SOTPN PEER! Device: " + info.getDeviceName() + " [" + mac + "] RSSI: " + rssi);
                mainHandler.post(() -> callback.onDeviceDiscovered(info));
            } else {
                Log.v(TAG, "Periodic scan update for " + mac + " RSSI: " + rssi);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // Called only if setReportDelay > 0; we use 0 so this is unused.
            // Included for completeness.
            for (ScanResult result : results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            isScanning = false;
            String reason = scanErrorToString(errorCode);
            Log.e(TAG, "BLE scan failed: " + reason);
            callback.onBleError("BLE scan failed: " + reason);
        }
    };

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    /**
     * Called by BleManager when a connected device disconnects or is no longer
     * reachable — removes it from the discovered map.
     */
    public void markDeviceLost(String macAddress) {
        if (discoveredDevices.remove(macAddress) != null) {
            Log.d(TAG, "Device lost: " + macAddress);
            mainHandler.post(() -> callback.onDeviceLost(macAddress));
        }
    }

    private String scanErrorToString(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:       return "Scan already started";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED: return "App registration failed";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:   return "BLE scanning not supported";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:         return "Internal BLE error";
            default:                                              return "Unknown error: " + errorCode;
        }
    }
}
