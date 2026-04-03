package com.sotpn.communication;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : GossipMessage.java
 * Package  : com.sotpn.communication
 * Step     : Step 3 - Gossip Broadcast Engine
 * Status   : Complete
 *
 * Depends on  : Nothing
 * Used by     : GossipEngine, GossipStore
 *
 * -----------------------------------------------------------------------
 * Represents a single "TOKEN_SEEN" gossip broadcast sent between devices
 * during Phase 3 (Adaptive Delay) of the 2-phase commit protocol.
 *
 * Wire format (plain string for easy BLE/WiFi transmission):
 *   TOKEN_SEEN:<tokenId>:<senderDeviceId>:<txId>:<timestampMs>:<hopCount>
 *
 * Example:
 *   TOKEN_SEEN:tok_abc123:device_XYZ:tx_999:1712345678900:0
 *
 * hopCount tracks how many devices have relayed this gossip.
 * We discard messages with hopCount >= MAX_HOPS to prevent infinite loops.
 */
public class GossipMessage {

    public static final String PREFIX   = "TOKEN_SEEN";
    public static final String SEPARATOR = ":";
    public static final int    MAX_HOPS  = 3; // propagate max 3 hops away

    private final String tokenId;
    private final String senderDeviceId; // device that ORIGINATED the gossip
    private final String txId;           // transaction this token is part of
    private final long   timestampMs;    // when the token was first seen
    private final int    hopCount;       // 0 = original, 1 = relayed once, etc.

    public GossipMessage(String tokenId, String senderDeviceId,
                         String txId, long timestampMs, int hopCount) {
        this.tokenId        = tokenId;
        this.senderDeviceId = senderDeviceId;
        this.txId           = txId;
        this.timestampMs    = timestampMs;
        this.hopCount       = hopCount;
    }

    // -----------------------------------------------------------------------
    // Serialization
    // -----------------------------------------------------------------------

    /**
     * Serialize to wire format for transmission over BLE or Wi-Fi Direct.
     */
    public String toWireString() {
        return PREFIX + SEPARATOR
                + tokenId        + SEPARATOR
                + senderDeviceId + SEPARATOR
                + txId           + SEPARATOR
                + timestampMs    + SEPARATOR
                + hopCount;
    }

    /**
     * Parse a gossip wire string back into a GossipMessage.
     * Returns null if the string is not a valid TOKEN_SEEN message.
     */
    public static GossipMessage fromWireString(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) return null;

        String[] parts = raw.split(SEPARATOR);
        // Expected: TOKEN_SEEN : tokenId : senderDeviceId : txId : timestamp : hopCount
        if (parts.length < 6) return null;

        try {
            String tokenId        = parts[1];
            String senderDeviceId = parts[2];
            String txId           = parts[3];
            long   timestampMs    = Long.parseLong(parts[4]);
            int    hopCount       = Integer.parseInt(parts[5]);

            return new GossipMessage(tokenId, senderDeviceId, txId,
                    timestampMs, hopCount);
        } catch (NumberFormatException e) {
            return null; // malformed message
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns a copy of this message with hopCount incremented by 1.
     * Used when relaying gossip received from another device.
     */
    public GossipMessage withIncrementedHop() {
        return new GossipMessage(tokenId, senderDeviceId, txId,
                timestampMs, hopCount + 1);
    }

    /** True if this message has exceeded the maximum relay depth */
    public boolean isExpired() {
        return hopCount >= MAX_HOPS;
    }

    /** True if this gossip is for a specific token ID */
    public boolean isAboutToken(String targetTokenId) {
        return tokenId.equals(targetTokenId);
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public String getTokenId()        { return tokenId; }
    public String getSenderDeviceId() { return senderDeviceId; }
    public String getTxId()           { return txId; }
    public long   getTimestampMs()    { return timestampMs; }
    public int    getHopCount()       { return hopCount; }

    @Override
    public String toString() {
        return "GossipMessage{tokenId=" + tokenId
                + ", sender=" + senderDeviceId
                + ", txId=" + txId
                + ", hops=" + hopCount + "}";
    }
}