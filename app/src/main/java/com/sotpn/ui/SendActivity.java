package com.sotpn.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.sotpn.R;
import com.sotpn.communication.BleDeviceInfo;
import com.sotpn.wallet.SimpleWallet;

import java.util.ArrayList;
import java.util.List;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : SendActivity.java
 * Package  : com.sotpn.ui
 * Step     : Step 9 - ViewModel + UI
 * Status   : Complete
 */
public class SendActivity extends AppCompatActivity {

    private static final String TAG = "SendActivity";

    // Views
    private EditText    etAmount;
    private ListView    listPeers;
    private TextView    tvNoPeers;
    private TextView    tvStatus;
    private Button      btnSend;
    private ProgressBar scanProgress;

    // ViewModel
    private MainViewModel viewModel;

    // State
    private BleDeviceInfo selectedPeer = null;
    private final List<BleDeviceInfo> peerList = new ArrayList<>();
    private ArrayAdapter<String>      peerAdapter;
    private final List<String>        peerDisplayNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        bindViews();
        setupViewModel();
        setupPeersList();
        setupSendButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.startDiscovery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.stopDiscovery();
    }

    private void bindViews() {
        etAmount    = findViewById(R.id.etAmount);
        listPeers   = findViewById(R.id.listPeers);
        tvNoPeers   = findViewById(R.id.tvNoPeers);
        tvStatus    = findViewById(R.id.tvStatus);
        btnSend     = findViewById(R.id.btnSend);
        scanProgress = findViewById(R.id.scanProgress);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.init(new SimpleWallet(this));

        // Observe nearby peers
        viewModel.getNearbyPeers().observe(this, peers -> {
            peerList.clear();
            peerDisplayNames.clear();
            peerList.addAll(peers);
            for (BleDeviceInfo d : peers) {
                peerDisplayNames.add(d.getDeviceName()
                        + "  (" + d.getRssi() + " dBm)");
            }
            peerAdapter.notifyDataSetChanged();

            if (peers.isEmpty()) {
                tvNoPeers.setVisibility(View.VISIBLE);
                listPeers.setVisibility(View.GONE);
            } else {
                tvNoPeers.setVisibility(View.GONE);
                listPeers.setVisibility(View.VISIBLE);
            }
        });

        // Observe connection state
        viewModel.getIsPeerConnected().observe(this, connected -> {
            updateSendButtonState(connected);
            if (connected) {
                scanProgress.setVisibility(View.GONE);
            }
        });

        // Observe status messages
        viewModel.getStatusMessage().observe(this, msg ->
                tvStatus.setText(msg));

        // Observe transaction completion
        viewModel.getCompletedProof().observe(this, proof -> {
            if (proof != null) {
                goToStatusScreen();
            }
        });

        // Observe errors
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupPeersList() {
        peerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice,
                peerDisplayNames);
        listPeers.setAdapter(peerAdapter);
        listPeers.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        listPeers.setOnItemClickListener((parent, view, position, id) -> {
            selectedPeer = peerList.get(position);
            tvStatus.setText("Connecting to " + selectedPeer.getDeviceName() + "...");
            viewModel.connectToPeer(selectedPeer, true);
        });
    }

    private void setupSendButton() {
        btnSend.setEnabled(false);
        btnSend.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            if (TextUtils.isEmpty(amountStr)) {
                etAmount.setError("Enter an amount");
                return;
            }

            int amount;
            try {
                amount = Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                etAmount.setError("Invalid amount");
                return;
            }

            if (amount <= 0 || amount > 500) {
                etAmount.setError("Amount must be between ₹1 and ₹500");
                return;
            }

            if (selectedPeer == null) {
                Toast.makeText(this, "Please select a device first",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String receiverPublicKey = selectedPeer.getMacAddress();
            viewModel.sendMoney(receiverPublicKey, amount);

            goToStatusScreen();
        });
    }

    private void updateSendButtonState(boolean peerConnected) {
        btnSend.setEnabled(peerConnected && selectedPeer != null);
    }

    private void goToStatusScreen() {
        Intent intent = new Intent(this, TransactionStatusActivity.class);
        startActivity(intent);
    }
}
