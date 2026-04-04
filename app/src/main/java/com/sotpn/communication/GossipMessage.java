package com.sotpn.communication;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * Wire format (hardened):
 *   TOKEN_SEEN:<tokenId>:<senderPubKey>:<txId>:<timestampMs>:<signature>:<hopCount>
 */
public class GossipMessage {

    public static final String PREFIX   = "TOKEN_SEEN";
    public static final String SEPARATOR = ":";
    public static final int    MAX_HOPS  = 3;

    private final String tokenId;
    private final String senderPublicKey; // The wallet public key that signed the transaction
    private final String txId;
    private final long   timestampMs;
    private final String signature;       // The transaction signature
    private final int    hopCount;

    public GossipMessage(String tokenId, String senderPublicKey, String txId, 
                         long timestampMs, String signature, int hopCount) {
        this.tokenId         = tokenId;
        this.senderPublicKey = senderPublicKey;
        this.txId            = txId;
        this.timestampMs     = timestampMs;
        this.signature       = signature;
        this.hopCount        = hopCount;
    }

    public String toWireString() {
        return PREFIX + SEPARATOR
                + tokenId         + SEPARATOR
                + senderPublicKey + SEPARATOR
                + txId            + SEPARATOR
                + timestampMs     + SEPARATOR
                + signature       + SEPARATOR
                + hopCount;
    }

    public static GossipMessage fromWireString(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) return null;

        String[] parts = raw.split(SEPARATOR);
        // Expected: TOKEN_SEEN : tokenId : senderPubKey : txId : timestamp : signature : hopCount
        if (parts.length < 7) return null;

        try {
            String tokenId        = parts[1];
            String senderPubKey   = parts[2];
            String txId           = parts[3];
            long   timestampMs    = Long.parseLong(parts[4]);
            String signature      = parts[5];
            int    hopCount       = Integer.parseInt(parts[6]);

            return new GossipMessage(tokenId, senderPubKey, txId, timestampMs, signature, hopCount);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public GossipMessage withIncrementedHop() {
        return new GossipMessage(tokenId, senderPublicKey, txId, timestampMs, signature, hopCount + 1);
    }

    /** Compatibility alias for getSenderPublicKey */
    public String getSenderDeviceId() {
        return senderPublicKey;
    }

    public boolean isAboutToken(String targetTokenId) {
        return tokenId != null && tokenId.equals(targetTokenId);
    }

    public boolean isExpired() { return hopCount >= MAX_HOPS; }

    public String getTokenId()        { return tokenId; }
    public String getSenderPublicKey() { return senderPublicKey; }
    public String getTxId()           { return txId; }
    public long   getTimestampMs()    { return timestampMs; }
    public String getSignature()      { return signature; }
    public int    getHopCount()       { return hopCount; }

    /** Returns the data string that the transaction signature was generated from */
    public String getSignedData() {
        return tokenId; // Minimal placeholder for now
    }
}
