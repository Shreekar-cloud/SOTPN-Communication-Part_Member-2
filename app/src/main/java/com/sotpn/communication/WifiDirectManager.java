package com.sotpn.communication;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import java.util.List;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
public class WifiDirectManager implements WifiDirectCallback {

    private static final String TAG = "WifiDirectManager";

    private final Context                context;
    private final WifiDirectCallback     externalCallback;
    private final Handler                mainHandler;

    private final WifiP2pManager         p2pManager;
    private final WifiP2pManager.Channel p2pChannel;
    private final WifiDirectBroadcastReceiver receiver;
    private final WifiDirectTransfer     transfer;

    private WifiP2pInfo connectionInfo;

    public WifiDirectManager(Context context, WifiDirectCallback externalCallback) {
        this.context          = context;
        this.externalCallback = externalCallback;
        this.mainHandler      = new Handler(Looper.getMainLooper());

        this.p2pManager = (WifiP2pManager)
                context.getSystemService(Context.WIFI_P2P_SERVICE);
        
        if (p2pManager != null) {
            this.p2pChannel = p2pManager.initialize(context, Looper.getMainLooper(), null);
        } else {
            this.p2pChannel = null;
        }

        this.receiver = new WifiDirectBroadcastReceiver(p2pManager, p2pChannel, this);
        this.transfer = new WifiDirectTransfer(this);
    }

    public WifiDirectBroadcastReceiver getReceiver() { return receiver; }

    public void discoverPeers() {
        if (p2pManager == null || p2pChannel == null) {
            externalCallback.onWiFiDirectError("Wi-Fi Direct not supported");
            return;
        }

        p2pManager.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Peer discovery started");
                externalCallback.onDiscoveryStarted();
            }

            @Override
            public void onFailure(int reason) {
                String msg = "Discovery failed: " + reason;
                Log.e(TAG, msg);
                externalCallback.onDiscoveryFailed(msg);
            }
        });
    }

    public void stopDiscovery() {
        if (p2pManager == null || p2pChannel == null) return;
        p2pManager.stopPeerDiscovery(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { Log.d(TAG, "Discovery stopped"); }
            @Override public void onFailure(int reason) { }
        });
    }

    public void connectToPeer(WifiP2pDevice device) {
        if (p2pManager == null || p2pChannel == null) return;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        Log.i(TAG, "Connecting to peer: " + device.deviceName);

        p2pManager.connect(p2pChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connection request sent");
            }

            @Override
            public void onFailure(int reason) {
                String msg = "Connection failed: " + reason;
                Log.e(TAG, msg);
                externalCallback.onConnectionFailed(msg);
            }
        });
    }

    public void disconnect() {
        if (p2pManager != null && p2pChannel != null) {
            p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { Log.d(TAG, "P2P group removed"); }
                @Override public void onFailure(int r) { Log.w(TAG, "removeGroup failed: " + r); }
            });
        }
        transfer.stop();
    }

    public void sendTransaction(Transaction transaction) {
        transfer.sendTransaction(transaction);
    }

    public void sendAck(TransactionAck ack) {
        transfer.sendAck(ack);
    }

    public void sendGossip(String gossipMessage) {
        transfer.sendGossip(gossipMessage);
    }

    @Override
    public void onDiscoveryStarted() {
        externalCallback.onDiscoveryStarted();
    }

    @Override
    public void onPeersAvailable(List<WifiP2pDevice> peers) {
        externalCallback.onPeersAvailable(peers);
    }

    @Override
    public void onDiscoveryFailed(String reason) {
        externalCallback.onDiscoveryFailed(reason);
    }

    @Override
    public void onConnected(WifiP2pInfo info) {
        this.connectionInfo = info;
        if (info.isGroupOwner) {
            transfer.startServer();
        } else {
            transfer.connectToServer(info.groupOwnerAddress);
        }
        externalCallback.onConnected(info);
    }

    @Override
    public void onDisconnected() {
        transfer.stop();
        externalCallback.onDisconnected();
    }

    @Override
    public void onConnectionFailed(String reason) {
        externalCallback.onConnectionFailed(reason);
    }

    @Override
    public void onTransactionSent(String txId) {
        externalCallback.onTransactionSent(txId);
    }

    @Override
    public void onTransactionSendFailed(String txId, String reason) {
        externalCallback.onTransactionSendFailed(txId, reason);
    }

    @Override
    public void onTransactionReceived(Transaction transaction) {
        externalCallback.onTransactionReceived(transaction);
    }

    @Override
    public void onAckReceived(TransactionAck ack) {
        externalCallback.onAckReceived(ack);
    }

    @Override
    public void onAckSendFailed(String txId, String reason) {
        externalCallback.onAckSendFailed(txId, reason);
    }

    @Override
    public void onGossipReceived(String gossipMessage) {
        externalCallback.onGossipReceived(gossipMessage);
    }

    @Override
    public void onWiFiDirectError(String reason) {
        externalCallback.onWiFiDirectError(reason);
    }
}
