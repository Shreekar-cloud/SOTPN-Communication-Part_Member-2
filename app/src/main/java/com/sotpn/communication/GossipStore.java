package com.sotpn.communication;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : GossipStore.java
 * Package  : com.sotpn.communication
 * Step     : Step 3 - Gossip Broadcast Engine
 * Status   : Complete
 *
 * Depends on  : GossipMessage
 * Used by     : GossipEngine, ValidationPhaseHandler (Step 5)
 *
 * -----------------------------------------------------------------------
 * In-memory store of all TOKEN_SEEN gossip messages received during
 * Phase 3 (Adaptive Delay).
 *
 * KEY FUNCTION — Double-spend detection:
 * If we receive two gossip messages for the SAME tokenId but from
 * DIFFERENT senderDeviceIds, that is a CONFLICT — the token may
 * be getting double-spent.
 *
 * Example conflict:
 *   Gossip 1: TOKEN_SEEN:tok_abc:device_A:tx_111:...
 *   Gossip 2: TOKEN_SEEN:tok_abc:device_B:tx_222:...
 *   → Same token tok_abc seen in two different transactions!
 *   → CONFLICT detected — alert ValidationPhaseHandler
 *
 * Thread safety: All methods are synchronized — GossipEngine
 * calls this from multiple threads (BLE + WiFi).
 */
public class GossipStore {

    private static final String TAG = "GossipStore";

    /**
     * tokenId → list of all gossip messages seen for that token.
     * A list size > 1 with different txIds means a potential double-spend.
     */
    private final Map<String, List<GossipMessage>> tokenGossipMap = new HashMap<>();

    /**
     * Tracks which gossip messages we've already processed
     * (by senderDeviceId + tokenId) to avoid reprocessing duplicates.
     */
    private final Map<String, Long> processedKeys = new HashMap<>();

    // -----------------------------------------------------------------------
    // Store a gossip message
    // -----------------------------------------------------------------------

    /**
     * Add a received gossip message to the store.
     *
     * @param message The gossip message to store.
     * @return ConflictResult indicating whether a conflict was detected.
     */
    public synchronized ConflictResult addGossip(GossipMessage message) {
        String tokenId  = message.getTokenId();
        String dedupKey = tokenId + "_" + message.getSenderDeviceId();

        // Skip exact duplicates (same token + same sender already seen)
        if (processedKeys.containsKey(dedupKey)) {
            Log.d(TAG, "Duplicate gossip ignored: " + dedupKey);
            return ConflictResult.NO_CONFLICT;
        }

        processedKeys.put(dedupKey, message.getTimestampMs());

        // Add to the token's gossip list
        if (!tokenGossipMap.containsKey(tokenId)) {
            tokenGossipMap.put(tokenId, new ArrayList<>());
        }
        tokenGossipMap.get(tokenId).add(message);

        Log.d(TAG, "Gossip stored for token " + tokenId
                + " (total sightings: " + tokenGossipMap.get(tokenId).size() + ")");

        // Check for conflict after adding
        return checkConflict(tokenId);
    }

    // -----------------------------------------------------------------------
    // Conflict detection
    // -----------------------------------------------------------------------

    /**
     * Check if a token has been seen in MORE THAN ONE transaction.
     * That means multiple devices are claiming to use the same token.
     *
     * @param tokenId The token to check.
     * @return ConflictResult with details if conflict found.
     */
    public synchronized ConflictResult checkConflict(String tokenId) {
        List<GossipMessage> sightings = tokenGossipMap.get(tokenId);
        if (sightings == null || sightings.size() <= 1) {
            return ConflictResult.NO_CONFLICT;
        }

        // Look for sightings with different txIds
        String firstTxId   = sightings.get(0).getTxId();
        String firstSender = sightings.get(0).getSenderDeviceId();

        for (int i = 1; i < sightings.size(); i++) {
            GossipMessage other = sightings.get(i);
            if (!other.getTxId().equals(firstTxId)) {
                // Different tx for the same token → CONFLICT
                Log.w(TAG, "⚠️ DOUBLE-SPEND CONFLICT detected for token: " + tokenId
                        + " | tx1=" + firstTxId + " (from " + firstSender + ")"
                        + " | tx2=" + other.getTxId() + " (from " + other.getSenderDeviceId() + ")");

                return new ConflictResult(
                        true,
                        tokenId,
                        firstTxId,
                        other.getTxId(),
                        firstSender,
                        other.getSenderDeviceId()
                );
            }
        }

        return ConflictResult.NO_CONFLICT;
    }

    // -----------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------

    /** Returns true if we have at least one gossip sighting for this token */
    public synchronized boolean hasSeenToken(String tokenId) {
        return tokenGossipMap.containsKey(tokenId)
                && !tokenGossipMap.get(tokenId).isEmpty();
    }

    /** Returns all gossip messages received for a given token */
    public synchronized List<GossipMessage> getGossipForToken(String tokenId) {
        List<GossipMessage> list = tokenGossipMap.get(tokenId);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    /** Total number of unique tokens we have gossip for */
    public synchronized int getTrackedTokenCount() {
        return tokenGossipMap.size();
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    /**
     * Clear all gossip data older than maxAgeMs.
     * Call this after sync or periodically to save memory.
     * Token expiry is 30–60 sec per SOTPN spec, so clearing after 60s is safe.
     */
    public synchronized void clearExpiredGossip(long maxAgeMs) {
        long now        = System.currentTimeMillis();
        long cutoff     = now - maxAgeMs;
        int  removed    = 0;

        List<String> emptyTokens = new ArrayList<>();

        for (Map.Entry<String, List<GossipMessage>> entry : tokenGossipMap.entrySet()) {
            entry.getValue().removeIf(m -> m.getTimestampMs() < cutoff);
            if (entry.getValue().isEmpty()) {
                emptyTokens.add(entry.getKey());
            }
            removed++;
        }

        for (String key : emptyTokens) {
            tokenGossipMap.remove(key);
        }

        // Also clear dedup keys for expired timestamps
        processedKeys.entrySet().removeIf(e -> e.getValue() < cutoff);

        Log.d(TAG, "Expired gossip cleared. Tokens remaining: " + tokenGossipMap.size());
    }

    /** Wipe everything — call after a full sync with backend */
    public synchronized void clearAll() {
        tokenGossipMap.clear();
        processedKeys.clear();
        Log.d(TAG, "GossipStore cleared");
    }

    // -----------------------------------------------------------------------
    // ConflictResult inner class
    // -----------------------------------------------------------------------

    /**
     * Result of a conflict check.
     * If isConflict is true, the token has been seen in two different
     * transactions — possible double-spend attempt.
     */
    public static class ConflictResult {

        public static final ConflictResult NO_CONFLICT =
                new ConflictResult(false, null, null, null, null, null);

        public final boolean isConflict;
        public final String  tokenId;
        public final String  txId1;       // first transaction using this token
        public final String  txId2;       // second (conflicting) transaction
        public final String  senderDeviceId1;
        public final String  senderDeviceId2;

        public ConflictResult(boolean isConflict, String tokenId,
                              String txId1, String txId2,
                              String senderDeviceId1, String senderDeviceId2) {
            this.isConflict       = isConflict;
            this.tokenId          = tokenId;
            this.txId1            = txId1;
            this.txId2            = txId2;
            this.senderDeviceId1  = senderDeviceId1;
            this.senderDeviceId2  = senderDeviceId2;
        }

        @Override
        public String toString() {
            if (!isConflict) return "No conflict";
            return "CONFLICT{token=" + tokenId
                    + ", tx1=" + txId1 + " vs tx2=" + txId2 + "}";
        }
    }
}
