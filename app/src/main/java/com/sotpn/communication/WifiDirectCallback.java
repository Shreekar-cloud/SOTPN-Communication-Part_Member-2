package com.sotpn.communication;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import java.util.List;

/**
 * Event interface for Wi-Fi Direct layer.
 */
public interface WifiDirectCallback {

    void onDiscoveryStarted();

    void onPeersAvailable(List<WifiP2pDevice> peers);

    void onDiscoveryFailed(String reason);

    void onConnected(WifiP2pInfo info);

    void onDisconnected();

    void onConnectionFailed(String reason);

    void onTransactionSent(String txId);

    void onTransactionSendFailed(String txId, String reason);

    void onTransactionReceived(Transaction transaction);

    void onAckReceived(TransactionAck ack);

    void onAckSendFailed(String txId, String reason);

    void onGossipReceived(String gossipMessage);

    void onWiFiDirectError(String reason);
}
