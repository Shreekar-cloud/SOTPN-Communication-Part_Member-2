package com.sotpn.communication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Receives Android system broadcasts for Wi-Fi Direct (P2P) events.
 */
public class WifiDirectBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiDirectReceiver";

    private final WifiP2pManager    manager;
    private final WifiP2pManager.Channel channel;
    private final WifiDirectCallback callback;

    public WifiDirectBroadcastReceiver(WifiP2pManager manager,
                                       WifiP2pManager.Channel channel,
                                       WifiDirectCallback callback) {
        this.manager  = manager;
        this.channel  = channel;
        this.callback = callback;
    }

    public static android.content.IntentFilter buildIntentFilter() {
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                handleStateChanged(intent);
                break;
            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                handlePeersChanged();
                break;
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                handleConnectionChanged(intent);
                break;
            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                handleThisDeviceChanged(intent);
                break;
            default:
                Log.w(TAG, "Unhandled action: " + action);
        }
    }

    private void handleStateChanged(Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            Log.i(TAG, "Wi-Fi Direct is ENABLED");
        } else {
            Log.w(TAG, "Wi-Fi Direct is DISABLED");
            callback.onWiFiDirectError("Wi-Fi Direct is disabled on this device");
        }
    }

    private void handlePeersChanged() {
        Log.d(TAG, "Peer list changed — requesting updated peers");
        manager.requestPeers(channel, peerListListener);
    }

    private void handleConnectionChanged(Intent intent) {
        NetworkInfo networkInfo =
                intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

        if (networkInfo != null && networkInfo.isConnected()) {
            Log.i(TAG, "P2P network connected — requesting connection info");
            manager.requestConnectionInfo(channel, connectionInfoListener);
        } else {
            Log.i(TAG, "P2P network disconnected");
            callback.onDisconnected();
        }
    }

    private void handleThisDeviceChanged(Intent intent) {
        WifiP2pDevice device =
                intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        if (device != null) {
            Log.d(TAG, "This device changed: name=" + device.deviceName);
        }
    }

    private final WifiP2pManager.PeerListListener peerListListener =
            new WifiP2pManager.PeerListListener() {
                @Override
                public void onPeersAvailable(WifiP2pDeviceList deviceList) {
                    List<WifiP2pDevice> peers = new ArrayList<>(deviceList.getDeviceList());
                    callback.onPeersAvailable(peers);
                }
            };

    private final WifiP2pManager.ConnectionInfoListener connectionInfoListener =
            new WifiP2pManager.ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(WifiP2pInfo info) {
                    callback.onConnected(info);
                }
            };
}
