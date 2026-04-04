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
    private static final long MAX_FUTURE_SKEW_MS = 10_000;

    private final Map<String, List<GossipMessage>> tokenGossipMap = new HashMap<>();
    private final Map<String, Long> processedKeys = new HashMap<>();
    private final Map<String, Integer> bestHopSeen = new HashMap<>();

    public synchronized ConflictResult addGossip(GossipMessage message) {
        if (hasProcessed(message)) {
            return ConflictResult.NO_CONFLICT;
        }

        long now = System.currentTimeMillis();
        if (message.getTimestampMs() > now + MAX_FUTURE_SKEW_MS) {
            return ConflictResult.NO_CONFLICT;
        }

        String key = getDedupKey(message);
        processedKeys.put(key, message.getTimestampMs());
        bestHopSeen.put(key, message.getHopCount());

        String tokenId = message.getTokenId();
        if (!tokenGossipMap.containsKey(tokenId)) {
            tokenGossipMap.put(tokenId, new ArrayList<>());
        }
        
        // SOTPN Logic: If we are replacing a stale sighting for the same txId, remove the old one.
        // This ensures the store doesn't grow with duplicate transaction sightings and prioritizes fresh info.
        List<GossipMessage> sightings = tokenGossipMap.get(tokenId);
        sightings.removeIf(m -> m.getTxId().equals(message.getTxId()) && m.getSenderDeviceId().equals(message.getSenderDeviceId()));
        sightings.add(message);

        return checkConflict(tokenId);
    }

    public synchronized boolean hasProcessed(GossipMessage message) {
        String key = getDedupKey(message);
        if (!processedKeys.containsKey(key)) return false;
        
        Integer best = bestHopSeen.get(key);
        // Only consider it processed if the new message doesn't have a better (lower) hop count.
        // This prevents "Gossip Shadowing" where a high-hop message blocks a low-hop alert.
        return best != null && message.getHopCount() >= best;
    }

    private String getDedupKey(GossipMessage message) {
        return message.getTokenId() + "_" + message.getSenderDeviceId() + "_" + message.getTxId();
    }

    public synchronized ConflictResult checkConflict(String tokenId) {
        List<GossipMessage> sightings = tokenGossipMap.get(tokenId);
        if (sightings == null || sightings.size() <= 1) return ConflictResult.NO_CONFLICT;

        GossipMessage first = sightings.get(0);

        for (int i = 1; i < sightings.size(); i++) {
            GossipMessage other = sightings.get(i);
            // SOTPN SECURITY FIX: Conflict if DIFFERENT TxId OR DIFFERENT Sender for same TxId (Identity spoofing)
            if (!other.getTxId().equals(first.getTxId()) || !other.getSenderDeviceId().equals(first.getSenderDeviceId())) {
                return new ConflictResult(true, tokenId, first.getTxId(), other.getTxId(), 
                                         first.getSenderDeviceId(), other.getSenderDeviceId());
            }
        }
        return ConflictResult.NO_CONFLICT;
    }

    public synchronized boolean hasSeenToken(String tokenId) {
        return tokenGossipMap.containsKey(tokenId) && !tokenGossipMap.get(tokenId).isEmpty();
    }

    public synchronized List<GossipMessage> getGossipForToken(String tokenId) {
        return tokenGossipMap.containsKey(tokenId) ? new ArrayList<>(tokenGossipMap.get(tokenId)) : new ArrayList<>();
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
        bestHopSeen.keySet().removeIf(k -> !processedKeys.containsKey(k));
    }

    public synchronized void clearAll() {
        tokenGossipMap.clear();
        processedKeys.clear();
        bestHopSeen.clear();
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
