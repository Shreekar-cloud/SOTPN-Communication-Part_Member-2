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

    public void startBroadcasting(String tokenId, String txId, long delayMs) {
        if (isActive) return;
        isActive = true;

        GossipMessage myGossip = new GossipMessage(
                tokenId,
                myDeviceId,
                txId,
                System.currentTimeMillis(),
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
        handler.postDelayed(this::stopBroadcasting, delayMs);
    }

    public void stopBroadcasting() {
        isActive = false;
        if (broadcastRunnable != null) {
            handler.removeCallbacks(broadcastRunnable);
            broadcastRunnable = null;
        }
    }

    public void shutdown() {
        stopBroadcasting();
        gossipStore.clearAll();
    }

    public void handleIncomingGossip(String rawGossip) {
        GossipMessage message = GossipMessage.fromWireString(rawGossip);
        if (message == null) return;
        
        // 1. Ignore if from self
        if (message.getSenderDeviceId().equals(myDeviceId)) return;

        // 2. Ignore if already processed (fixes "Relayed Twice" bug)
        if (gossipStore.hasProcessed(message)) return;

        // 3. Add to store and check for conflict
        GossipStore.ConflictResult conflict = gossipStore.addGossip(message);

        if (conflict.isConflict) {
            stopBroadcasting();
            listener.onConflictDetected(conflict);
            return;
        }

        // 4. Notify listener
        listener.onGossipReceived(message);

        // 5. Relay if not expired
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
        bleManager.broadcastGossip(extractTokenId(gossipWireString));
        wifiManager.sendGossip(gossipWireString);
    }

    private String extractTokenId(String wireString) {
        String[] parts = wireString.split(GossipMessage.SEPARATOR);
        return parts.length > 1 ? parts[1] : wireString;
    }
}
