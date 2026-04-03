package com.sotpn.wallet;

import com.sotpn.model.Transaction;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : WalletInterface.java
 * Package  : com.sotpn.wallet
 * Step     : Step 4 - Prepare Phase Handler
 * Status   : Complete
 *
 * Depends on  : Transaction (model)
 * Used by     : PreparePhaseHandler, CommitPhaseHandler
 *
 * -----------------------------------------------------------------------
 * THIS IS THE CONTRACT BETWEEN MEMBER 2 AND MEMBER 1.
 *
 * Member 1 IMPLEMENTS this interface in their WalletManager.java.
 * Member 2 CALLS this interface in PreparePhaseHandler and CommitPhaseHandler.
 *
 * Neither side needs the other's full code to compile — just this interface.
 *
 * HOW TO INTEGRATE:
 *   Member 1 does:
 *     public class WalletManager implements WalletInterface { ... }
 *
 *   Member 2 does:
 *     WalletInterface wallet = new WalletManager(context);
 *     preparePhaseHandler = new PreparePhaseHandler(wallet, bleManager, wifiManager);
 * -----------------------------------------------------------------------
 */
public interface WalletInterface {

    /**
     * Returns the public key of this device's wallet.
     * Used as sender_public_key in transactions.
     *
     * @return Base64-encoded public key string.
     */
    String getPublicKey();

    /**
     * Returns the current spendable balance in the wallet (in paise/smallest unit).
     */
    long getBalance();

    /**
     * Find and return a token from the wallet that has at least the given value.
     * Returns null if no suitable token exists or balance is insufficient.
     *
     * @param requiredValue Minimum value needed (e.g. 50000 paise = ₹500).
     * @return A TokenInfo object, or null if unavailable.
     */
    TokenInfo getSpendableToken(long requiredValue);

    /**
     * Lock a token so it cannot be used in any other transaction.
     * Called IMMEDIATELY when sender starts Phase 1.
     * Must be atomic — prevent race conditions.
     *
     * @param tokenId The token to lock.
     * @return true if successfully locked, false if already locked or not found.
     */
    boolean lockToken(String tokenId);

    /**
     * Unlock a token (called if transaction fails or is aborted).
     * Reverses the lock so the token can be used again.
     *
     * @param tokenId The token to unlock.
     */
    void unlockToken(String tokenId);

    /**
     * Mark a token as permanently SPENT.
     * Called after successful Phase 4 (Commit) on the SENDER side.
     * This is irreversible — the token can never be used again.
     *
     * @param tokenId The token to mark spent.
     * @param proof   The signed ACK proof from the receiver.
     */
    void markTokenSpent(String tokenId, String proof);

    /**
     * Add a received token to this wallet.
     * Called after successful Phase 4 (Commit) on the RECEIVER side.
     *
     * @param tokenId        The token that was received.
     * @param senderPublicKey The sender's public key (for verification record).
     * @param proof          The signed transaction proof.
     */
    void receiveToken(String tokenId, String senderPublicKey, String proof);

    /**
     * Sign a transaction using this device's private key.
     *
     * The data to sign is:
     *   tokenId + receiverPublicKey + timestamp + nonce
     * concatenated as a single string.
     *
     * @param dataToSign The concatenated string to sign.
     * @return Base64-encoded signature string.
     */
    String signTransaction(String dataToSign);

    /**
     * Verify a signature against a public key.
     * Used by the RECEIVER in Phase 2 (Validation).
     *
     * @param data            The original data that was signed.
     * @param signature       The Base64-encoded signature to verify.
     * @param signerPublicKey The public key of the alleged signer.
     * @return true if the signature is valid.
     */
    boolean verifySignature(String data, String signature, String signerPublicKey);

    /**
     * Generate a unique nonce for this transaction.
     * Nonces prevent replay attacks.
     *
     * @return A unique nonce string (e.g. UUID or random hex).
     */
    String generateNonce();

    // -----------------------------------------------------------------------
    // Inner class: TokenInfo
    // Lightweight token data passed from Member 1 to Member 2.
    // Member 1 populates this; Member 2 reads it.
    // -----------------------------------------------------------------------

    class TokenInfo {
        public final String tokenId;
        public final long   value;         // in paise (₹1 = 100 paise)
        public final long   expiryTimeMs;  // Unix timestamp ms — token invalid after this

        public TokenInfo(String tokenId, long value, long expiryTimeMs) {
            this.tokenId      = tokenId;
            this.value        = value;
            this.expiryTimeMs = expiryTimeMs;
        }

        /** True if token is still within its validity window */
        public boolean isValid() {
            return System.currentTimeMillis() < expiryTimeMs;
        }

        @Override
        public String toString() {
            return "TokenInfo{id=" + tokenId + ", value=" + value
                    + ", valid=" + isValid() + "}";
        }
    }
}
