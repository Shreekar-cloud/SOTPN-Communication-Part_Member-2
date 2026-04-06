package com.sotpn.communication;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * Utility to handle complex runtime permissions for Bluetooth and Wi-Fi Direct
 * across different Android versions (API 26 - 34).
 */
public class PermissionManager {

    public static final int REQUEST_CODE_PERMISSIONS = 1001;

    /**
     * Get the list of permissions required based on the Android version.
     */
    public static String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Android 12 (API 31) and above: New Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            // Android 11 and below: Legacy Bluetooth + Location for scanning
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Android 13 (API 33) and above: New Nearby Wi-Fi Devices permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        // Common Wi-Fi permissions (not runtime but good to have)
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE);

        return permissions.toArray(new String[0]);
    }

    /**
     * Check if all required permissions are granted.
     */
    public static boolean hasAllPermissions(Context context) {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Request all required permissions from the user.
     */
    public static void requestPermissions(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                getRequiredPermissions(),
                REQUEST_CODE_PERMISSIONS
        );
    }

    /**
     * Check if Bluetooth is enabled.
     */
    public static boolean isBluetoothEnabled(Context context) {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Check if Location services are enabled (required for BLE scanning on many devices).
     */
    public static boolean isLocationEnabled(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }
}
