package com.sotpn.communication;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 */
public class GossipEngine {

    private static final String TAG = "GossipEngine";
    private static final long BROADCAST_INTERVAL_MS = 1_500;

    private final BleManager         bleManager;
    private final WifiDirectManager  wifiManager;
    private final GossipStore        gossipStore;
    private final GossipListener     listener;
    private final Handler            handler;

    private String  myDeviceId;
    private boolean isActive = false;
    private Runnable broadcastRunnable;
    private Runnable stopTimerRunnable;

    public interface GossipListener {
        void onConflictDetected(GossipStore.ConflictResult result);
        void onGossipReceived(GossipMessage message);
    }

    public GossipEngine(BleManager bleManager,
                        WifiDirectManager wifiManager,
                        GossipStore gossipStore,
                        GossipListener listener,
                        String myDeviceId) {
        this.bleManager   = bleManager;
        this.wifiManager  = wifiManager;
        this.gossipStore  = gossipStore;
        this.listener     = listener;
        this.myDeviceId   = myDeviceId;
        this.handler      = new Handler(Looper.getMainLooper());
    }

    /**
     * Records a local transaction in the gossip store so conflicts can be detected
     * against it if other devices broadcast the same token.
     */
    public void recordLocalSighting(String tokenId, String txId, String signature) {
        GossipMessage localMsg = new GossipMessage(
                tokenId, myDeviceId, txId, System.currentTimeMillis(), signature, 0);
        gossipStore.addGossip(localMsg);
    }

    public void startBroadcasting(String tokenId, String txId, String signature, long delayMs) {
        if (isActive) return;
        isActive = true;

        GossipMessage myGossip = new GossipMessage(
                tokenId,
                myDeviceId,
                txId,
                System.currentTimeMillis(),
                signature,
                0
        );

        broadcastRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isActive) return;
                broadcastToAll(myGossip.toWireString());
                handler.postDelayed(this, BROADCAST_INTERVAL_MS);
            }
        };
        handler.post(broadcastRunnable);

        // Store the stop timer as a runnable so it can be cancelled
        stopTimerRunnable = this::stopBroadcasting;
        handler.postDelayed(stopTimerRunnable, delayMs);
    }

    public void stopBroadcasting() {
        isActive = false;
        if (broadcastRunnable != null) {
            handler.removeCallbacks(broadcastRunnable);
            broadcastRunnable = null;
        }
        if (stopTimerRunnable != null) {
            handler.removeCallbacks(stopTimerRunnable);
            stopTimerRunnable = null;
        }
    }

    public void shutdown() {
        stopBroadcasting();
        // SOTPN Logic: Do not wipe the store on shutdown. 
        // Sightings must persist for other concurrent checks.
    }

    public void handleIncomingGossip(String rawGossip) {
        GossipMessage message = GossipMessage.fromWireString(rawGossip);
        if (message == null) return;
        
        if (message.getSenderPublicKey().equals(myDeviceId)) return;
        if (gossipStore.hasProcessed(message)) return;

        GossipStore.ConflictResult conflict = gossipStore.addGossip(message);

        if (conflict.isConflict) {
            stopBroadcasting();
            listener.onConflictDetected(conflict);
            return;
        }

        listener.onGossipReceived(message);

        if (!message.isExpired()) {
            GossipMessage relayed = message.withIncrementedHop();
            broadcastToAll(relayed.toWireString());
        }
    }

    public GossipStore.ConflictResult checkConflict(String tokenId) {
        return gossipStore.checkConflict(tokenId);
    }

    public boolean hasSeenToken(String tokenId) {
        return gossipStore.hasSeenToken(tokenId);
    }

    public List<GossipMessage> getGossipForToken(String tokenId) {
        return gossipStore.getGossipForToken(tokenId);
    }

    private void broadcastToAll(String gossipWireString) {
        try {
            if (bleManager != null) {
                bleManager.broadcastGossip(gossipWireString);
            }
        } catch (Exception e) {
            Log.e(TAG, "BLE gossip broadcast failed", e);
        }

        try {
            if (wifiManager != null) {
                wifiManager.sendGossip(gossipWireString);
            }
        } catch (Exception e) {
            Log.e(TAG, "WiFi Direct gossip broadcast failed", e);
        }
    }

    private String extractTokenId(String wireString) {
        String[] parts = wireString.split(GossipMessage.SEPARATOR);
        return parts.length > 1 ? parts[1] : wireString;
    }
}
