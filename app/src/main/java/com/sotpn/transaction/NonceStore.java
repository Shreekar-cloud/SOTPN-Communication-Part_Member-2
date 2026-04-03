package com.sotpn.transaction;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : NonceStore.java
 * Package  : com.sotpn.transaction
 * Step     : Step 5 - Validation Phase Handler
 * Status   : Complete
 *
 * Depends on  : Nothing
 * Used by     : ValidationPhaseHandler
 *
 * -----------------------------------------------------------------------
 * Tracks all nonces seen in transactions to prevent REPLAY ATTACKS.
 *
 * WHAT IS A REPLAY ATTACK?
 *   An attacker captures a valid signed transaction and sends it again
 *   to a different receiver. Since the signature is valid, a naive
 *   validator would accept it — spending the token twice.
 *
 * HOW NONCES PREVENT THIS:
 *   Every transaction has a unique nonce (random value).
 *   Once we've seen a nonce, we NEVER accept it again.
 *   The attacker's replayed transaction has the same nonce → REJECTED.
 *
 * STORAGE:
 *   nonce → timestamp of when we first saw it
 *   We keep nonces for NONCE_TTL_MS (2 minutes) then clear them.
 *   Token expiry is 30–60 sec so 2 min is a safe cleanup window.
 *
 * Thread safety: All methods synchronized — called from multiple threads.
 */
public class NonceStore {

    private static final String TAG         = "NonceStore";
    private static final long   NONCE_TTL_MS = 120_000; // 2 minutes

    /** nonce string → timestamp when first seen (ms) */
    private final Map<String, Long> seenNonces = new HashMap<>();

    // -----------------------------------------------------------------------
    // Core operations
    // -----------------------------------------------------------------------

    /**
     * Check if a nonce has been seen before AND record it if it's new.
     *
     * This is ATOMIC — check + record happens in one synchronized call.
     * This prevents a race condition where two threads check the same
     * nonce before either records it.
     *
     * @param nonce The nonce to check.
     * @return true  → nonce is NEW, transaction may proceed
     *         false → nonce already seen, REJECT the transaction
     */
    public synchronized boolean checkAndRecord(String nonce) {
        if (seenNonces.containsKey(nonce)) {
            Log.w(TAG, "⚠️ Replay attack detected — nonce already seen: " + nonce);
            return false; // already seen → reject
        }
        seenNonces.put(nonce, System.currentTimeMillis());
        Log.d(TAG, "Nonce recorded: " + nonce
                + " (total stored: " + seenNonces.size() + ")");
        return true; // new nonce → allow
    }

    /**
     * Check if a nonce has been seen WITHOUT recording it.
     * Use this for read-only checks (e.g. during audit).
     *
     * @param nonce The nonce to check.
     * @return true if already seen, false if new.
     */
    public synchronized boolean hasSeenNonce(String nonce) {
        return seenNonces.containsKey(nonce);
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    /**
     * Remove nonces older than NONCE_TTL_MS.
     * Call this periodically — e.g. after each transaction or on sync.
     * Safe to call frequently; no-op if nothing to clear.
     */
    public synchronized void clearExpired() {
        long cutoff = System.currentTimeMillis() - NONCE_TTL_MS;
        int  before = seenNonces.size();
        seenNonces.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        int removed = before - seenNonces.size();
        if (removed > 0) {
            Log.d(TAG, "Cleared " + removed + " expired nonces. Remaining: "
                    + seenNonces.size());
        }
    }

    /** Wipe everything. Call after a full backend sync. */
    public synchronized void clearAll() {
        seenNonces.clear();
        Log.d(TAG, "NonceStore cleared");
    }

    public synchronized int size() { return seenNonces.size(); }
}
