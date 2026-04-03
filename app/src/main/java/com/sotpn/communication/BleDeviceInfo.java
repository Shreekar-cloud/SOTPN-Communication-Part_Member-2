package com.sotpn.communication;

import android.bluetooth.BluetoothDevice;

/**
 * Represents a nearby SOTPN-compatible device discovered during BLE scan.
 *
 * Passed to BleCallback.onDeviceDiscovered() and stored by BleManager
 * so the TransactionEngine can pick a target device.
 */
public class BleDeviceInfo {

    private final BluetoothDevice bluetoothDevice;
    private final String          deviceName;
    private final String          macAddress;
    private final int             rssi;           // signal strength (dBm, higher = closer)
    private final long            discoveredAtMs; // System.currentTimeMillis()

    public BleDeviceInfo(BluetoothDevice bluetoothDevice, String deviceName,
                         int rssi, long discoveredAtMs) {
        this.bluetoothDevice = bluetoothDevice;
        this.deviceName      = deviceName != null ? deviceName : BleConstants.DEVICE_NAME_PREFIX + "Unknown";
        this.macAddress      = bluetoothDevice.getAddress();
        this.rssi            = rssi;
        this.discoveredAtMs  = discoveredAtMs;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Estimate rough distance from RSSI.
     * Formula: d = 10 ^ ((TxPower - RSSI) / (10 * n))
     * We use TxPower = -59 dBm (typical 1m reference), n = 2.0 (free space).
     * Result is rough — useful only for adaptive delay decisions.
     */
    public double estimateDistanceMeters() {
        if (rssi == 0) return -1.0;
        double ratio = rssi * 1.0 / -59;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            return 0.89976 * Math.pow(ratio, 7.7095) + 0.111;
        }
    }

    /** True if RSSI suggests the device is within ~10 metres */
    public boolean isNearby() {
        return rssi > -80; // -80 dBm ≈ ~10m in open space
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public BluetoothDevice getBluetoothDevice() { return bluetoothDevice; }
    public String          getDeviceName()       { return deviceName; }
    public String          getMacAddress()       { return macAddress; }
    public int             getRssi()             { return rssi; }
    public long            getDiscoveredAtMs()   { return discoveredAtMs; }

    @Override
    public String toString() {
        return "BleDeviceInfo{name=" + deviceName + ", mac=" + macAddress
                + ", rssi=" + rssi + "dBm, ~" + String.format("%.1f", estimateDistanceMeters()) + "m}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleDeviceInfo)) return false;
        return macAddress.equals(((BleDeviceInfo) o).macAddress);
    }

    @Override
    public int hashCode() { return macAddress.hashCode(); }
}