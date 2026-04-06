package com.sotpn;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.sotpn.communication.PermissionManager;
import com.sotpn.ui.SendActivity;
import com.sotpn.ui.ReceiveActivity;
import com.sotpn.ui.TransactionStatusActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check and request permissions at startup
        if (!PermissionManager.hasAllPermissions(this)) {
            PermissionManager.requestPermissions(this);
        }

        findViewById(R.id.btnGoSend).setOnClickListener(v -> {
            if (checkPermissionsAndServices()) {
                startActivity(new Intent(this, SendActivity.class));
            }
        });

        findViewById(R.id.btnGoReceive).setOnClickListener(v -> {
            if (checkPermissionsAndServices()) {
                startActivity(new Intent(this, ReceiveActivity.class));
            }
        });

        findViewById(R.id.btnGoStatus).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionStatusActivity.class)));
    }

    private boolean checkPermissionsAndServices() {
        if (!PermissionManager.hasAllPermissions(this)) {
            Toast.makeText(this, "Permissions required for discovery", Toast.LENGTH_SHORT).show();
            PermissionManager.requestPermissions(this);
            return false;
        }

        if (!PermissionManager.isBluetoothEnabled(this)) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
            return false;
        }

        if (!PermissionManager.isLocationEnabled(this)) {
            Toast.makeText(this, "Please enable Location (required for scanning)", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_CODE_PERMISSIONS) {
            if (PermissionManager.hasAllPermissions(this)) {
                Toast.makeText(this, "Permissions granted! Ready to scan.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Discovery may not work without all permissions.", Toast.LENGTH_LONG).show();
            }
        }
    }
}