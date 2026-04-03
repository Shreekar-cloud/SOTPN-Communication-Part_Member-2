package com.sotpn.ui;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.sotpn.R;
import com.sotpn.communication.WifiDirectBroadcastReceiver;
import com.sotpn.model.TransactionPhase;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : ReceiveActivity.java
 * Package  : com.sotpn.ui.theme
 * Step     : Step 9 - ViewModel + UI
 */
public class ReceiveActivity extends AppCompatActivity {

    private TextView tvReceiveStatus;
    private TextView tvReceiveSubStatus;
    private TextView tvDeviceId;
    private Button   btnCancelReceive;

    private MainViewModel viewModel;

    private WifiDirectBroadcastReceiver wifiDirectReceiver;
    private boolean receiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);

        bindViews();
        setupViewModel();
        setupCancelButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.startDiscovery();

        if (viewModel.getTransactionManager() != null) {
            wifiDirectReceiver = viewModel.getTransactionManager().getWifiDirectReceiver();
            if (wifiDirectReceiver != null && !receiverRegistered) {
                IntentFilter filter = WifiDirectBroadcastReceiver.buildIntentFilter();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(wifiDirectReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(wifiDirectReceiver, filter);
                }
                receiverRegistered = true;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.stopDiscovery();

        if (receiverRegistered && wifiDirectReceiver != null) {
            unregisterReceiver(wifiDirectReceiver);
            receiverRegistered = false;
        }
    }

    private void bindViews() {
        tvReceiveStatus    = findViewById(R.id.tvReceiveStatus);
        tvReceiveSubStatus = findViewById(R.id.tvReceiveSubStatus);
        tvDeviceId         = findViewById(R.id.tvDeviceId);
        btnCancelReceive   = findViewById(R.id.btnCancelReceive);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        tvDeviceId.setText("Advertising as SOTPN device\nBluetooth: enabled");

        viewModel.getCurrentPhase().observe(this, this::updateUIForPhase);

        viewModel.getStatusMessage().observe(this, msg ->
                tvReceiveSubStatus.setText(msg));

        viewModel.getCurrentPhase().observe(this, phase -> {
            if (phase == TransactionPhase.VALIDATING
                    || phase == TransactionPhase.DELAYING
                    || phase == TransactionPhase.COMMITTING) {
                goToStatusScreen();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                resetToWaiting();
            }
        });

        viewModel.getCompletedProof().observe(this, proof -> {
            if (proof != null) {
                goToStatusScreen();
            }
        });
    }

    private void setupCancelButton() {
        btnCancelReceive.setOnClickListener(v -> {
            viewModel.cancelTransaction();
            finish();
        });
    }

    private void updateUIForPhase(TransactionPhase phase) {
        if (phase == null) return;
        
        switch (phase) {
            case PREPARE:

                tvReceiveStatus.setText("Waiting for sender...");
                tvReceiveSubStatus.setText("Make sure Bluetooth is enabled");
                break;
            case VALIDATING:
                tvReceiveStatus.setText("Transaction incoming!");
                tvReceiveSubStatus.setText("Validating transaction...");
                break;
            case DELAYING:
                tvReceiveStatus.setText("Verifying payment...");
                tvReceiveSubStatus.setText("Checking for conflicts...");
                break;
            case COMMITTING:
                tvReceiveStatus.setText("Completing payment...");
                tvReceiveSubStatus.setText("Finalising...");
                break;
            case FINALIZED:
                tvReceiveStatus.setText("Payment Received! ✅");
                tvReceiveSubStatus.setText("Transaction complete");
                break;
            case FAILED:
                tvReceiveStatus.setText("Transaction Failed");
                tvReceiveSubStatus.setText("Please try again");
                break;
            default:
                break;
        }
    }

    private void resetToWaiting() {
        tvReceiveStatus.setText("Waiting for sender...");
        tvReceiveSubStatus.setText("Make sure Bluetooth is enabled");
    }

    private void goToStatusScreen() {
        Intent intent = new Intent(this, TransactionStatusActivity.class);
        startActivity(intent);
    }
}
