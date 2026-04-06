package com.sotpn.communication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

/**
 * Broadcasts this device as a discoverable SOTPN node via BLE advertising.
 *
 * Any device running BleScanner will pick up this advertisement and
 * recognise it as a SOTPN peer via SERVICE_UUID.
 *
 * Usage:
 *   bleAdvertiser.startAdvertising(myDeviceId);
 *   // ... when done
 *   bleAdvertiser.stopAdvertising();
 */
public class BleAdvertiser {

    private static final String TAG = "BleAdvertiser";

    private final BluetoothAdapter  adapter;
    private final Context           context;
    private final BleCallback        callback;
    private BluetoothLeAdvertiser    advertiser;
    private boolean                  isAdvertising = false;

    public BleAdvertiser(BluetoothAdapter adapter, Context context, BleCallback callback) {
        this.adapter  = adapter;
        this.context  = context;
        this.callback = callback;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Start advertising. The deviceId is appended to the name so the receiver
     * can later associate a peer's public key with its MAC address.
     *
     * @param deviceId Short unique ID for this device (e.g. first 8 chars of public key)
     */
    public void startAdvertising(String deviceId) {
        if (isAdvertising) {
            Log.w(TAG, "Already advertising — call stopAdvertising() first");
            return;
        }
        if (adapter == null || !adapter.isEnabled()) {
            callback.onBleError("Bluetooth is not enabled");
            return;
        }

        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Log.e(TAG, "FAILED: BluetoothLeAdvertiser is null. BLE Advertising not supported or Bluetooth OFF.");
            callback.onBleError("Device does not support BLE advertising");
            return;
        }

        // Set the adapter name so scanners can see "SOTPN_xxxx" even if packet parsing fails
        String adName = BleConstants.DEVICE_NAME_PREFIX + deviceId;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            adapter.setName(adName);
        }

        // --- Settings: low latency for fast peer discovery during a transaction ---
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)   // must be true — peers need to connect for data transfer
                .setTimeout(0)          // advertise indefinitely until stopAdvertising()
                .build();

        // --- Data: include SERVICE_UUID so scanners can filter SOTPN devices ---
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)    // Try including name for better visibility
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .addManufacturerData(0x004C,   // arbitrary manufacturer ID
                        buildManufacturerPayload(deviceId))
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
        Log.d(TAG, "BLE advertising started for device: " + deviceId);
    }

    /**
     * Stop advertising. Call this when the transaction is complete or the app
     * goes to background.
     */
    public void stopAdvertising() {
        if (advertiser != null && isAdvertising) {
            advertiser.stopAdvertising(advertiseCallback);
            isAdvertising = false;
            Log.d(TAG, "BLE advertising stopped");
        }
    }

    public boolean isAdvertising() { return isAdvertising; }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Encode deviceId into manufacturer-specific payload.
     * Format: [0x53, 0x4F, 0x54, ...deviceId bytes (up to 24 chars)]
     *          S     O     T    (SOTPN prefix)
     */
    private byte[] buildManufacturerPayload(String deviceId) {
        byte[] prefix = {0x53, 0x4F, 0x54}; // "SOT"
        byte[] idBytes = deviceId.getBytes();
        int idLen = Math.min(idBytes.length, 24); // BLE ad packet is limited
        byte[] payload = new byte[prefix.length + idLen];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(idBytes, 0, payload, prefix.length, idLen);
        return payload;
    }

    // -----------------------------------------------------------------------
    // AdvertiseCallback
    // -----------------------------------------------------------------------

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            isAdvertising = true;
            Log.i(TAG, "Advertising started successfully. Mode=" +
                    settingsInEffect.getMode());
        }

        @Override
        public void onStartFailure(int errorCode) {
            isAdvertising = false;
            String reason = advertiseErrorToString(errorCode);
            Log.e(TAG, "Advertising failed: " + reason);
            callback.onBleError("Advertising failed: " + reason);
        }
    };

    private String advertiseErrorToString(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "Already started";
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "Advertise data too large";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "BLE advertising not supported on this device";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "Internal BLE error";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "Too many concurrent advertisers";
            default:
                return "Unknown error code: " + errorCode;
        }
    }
}
