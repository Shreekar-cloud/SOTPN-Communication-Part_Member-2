package com.sotpn.communication;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 */
public class GossipStore {

    private static final String TAG = "GossipStore";
    private static final long MAX_FUTURE_SKEW_MS = 10_000; // 10 seconds allowance

    private final Map<String, List<GossipMessage>> tokenGossipMap = new HashMap<>();
    private final Map<String, Long> processedKeys = new HashMap<>();

    /**
     * Add a received gossip message to the store with DoS protection.
     */
    public synchronized ConflictResult addGossip(GossipMessage message) {
        // 1. Deduplication
        if (hasProcessed(message)) {
            return ConflictResult.NO_CONFLICT;
        }

        // 2. DoS Protection: Reject messages from the future to prevent RAM bloat
        long now = System.currentTimeMillis();
        if (message.getTimestampMs() > now + MAX_FUTURE_SKEW_MS) {
            Log.w(TAG, "Rejected gossip from the future: " + message.getTimestampMs() + " (now=" + now + ")");
            return ConflictResult.NO_CONFLICT;
        }

        String tokenId = message.getTokenId();
        processedKeys.put(getDedupKey(message), message.getTimestampMs());

        if (!tokenGossipMap.containsKey(tokenId)) {
            tokenGossipMap.put(tokenId, new ArrayList<>());
        }
        tokenGossipMap.get(tokenId).add(message);

        return checkConflict(tokenId);
    }

    public synchronized boolean hasProcessed(GossipMessage message) {
        return processedKeys.containsKey(getDedupKey(message));
    }

    private String getDedupKey(GossipMessage message) {
        return message.getTokenId() + "_" + message.getSenderDeviceId() + "_" + message.getTxId();
    }

    public synchronized ConflictResult checkConflict(String tokenId) {
        List<GossipMessage> sightings = tokenGossipMap.get(tokenId);
        if (sightings == null || sightings.size() <= 1) {
            return ConflictResult.NO_CONFLICT;
        }

        String firstTxId   = sightings.get(0).getTxId();
        String firstSender = sightings.get(0).getSenderDeviceId();

        for (int i = 1; i < sightings.size(); i++) {
            GossipMessage other = sightings.get(i);
            if (!other.getTxId().equals(firstTxId)) {
                return new ConflictResult(true, tokenId, firstTxId, other.getTxId(), 
                                         firstSender, other.getSenderDeviceId());
            }
        }

        return ConflictResult.NO_CONFLICT;
    }

    public synchronized boolean hasSeenToken(String tokenId) {
        return tokenGossipMap.containsKey(tokenId) && !tokenGossipMap.get(tokenId).isEmpty();
    }

    public synchronized List<GossipMessage> getGossipForToken(String tokenId) {
        List<GossipMessage> list = tokenGossipMap.get(tokenId);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    public synchronized int getTrackedTokenCount() {
        return tokenGossipMap.size();
    }

    public synchronized void clearExpiredGossip(long maxAgeMs) {
        long now = System.currentTimeMillis();
        long cutoff = now - maxAgeMs;
        tokenGossipMap.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(m -> m.getTimestampMs() < cutoff);
            return entry.getValue().isEmpty();
        });
        processedKeys.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    public synchronized void clearAll() {
        tokenGossipMap.clear();
        processedKeys.clear();
    }

    public static class ConflictResult {
        public static final ConflictResult NO_CONFLICT = new ConflictResult(false, null, null, null, null, null);
        public final boolean isConflict;
        public final String  tokenId;
        public final String  txId1;
        public final String  txId2;
        public final String  senderDeviceId1;
        public final String  senderDeviceId2;

        public ConflictResult(boolean isConflict, String tokenId, String txId1, String txId2,
                              String senderDeviceId1, String senderDeviceId2) {
            this.isConflict = isConflict;
            this.tokenId = tokenId;
            this.txId1 = txId1;
            this.txId2 = txId2;
            this.senderDeviceId1 = senderDeviceId1;
            this.senderDeviceId2 = senderDeviceId2;
        }
    }
}
