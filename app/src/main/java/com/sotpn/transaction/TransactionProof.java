package com.sotpn.transaction;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : TransactionProof.java
 * Package  : com.sotpn.transaction
 * Step     : Step 7 - Commit Phase Handler
 * Status   : Complete
 *
 * Depends on  : Nothing
 * Used by     : CommitPhaseHandler, TransactionManager, SyncPayload (Member 3)
 *
 * -----------------------------------------------------------------------
 * Immutable record stored by BOTH sender and receiver after a successful
 * Phase 4 commit. This is the permanent evidence that a transaction
 * happened.
 *
 * SENDER stores:
 *   - Full transaction details
 *   - Receiver's signed ACK (proves receiver accepted)
 *   - Role = SENDER
 *
 * RECEIVER stores:
 *   - Full transaction details
 *   - Their own signed ACK (proves they accepted)
 *   - Role = RECEIVER
 *
 * This proof is later sent to Member 3 (Sync Engine) during online sync
 * as part of SyncPayload. Member 4 uses it for final ledger settlement.
 *
 * KEY DESIGN PRINCIPLE FROM SOTPN SPEC:
 *   "Do not trust local deletion — proof-based system"
 *   Once a proof exists, it cannot be deleted. The backend uses it
 *   for final conflict resolution.
 */
public class TransactionProof {

    // -----------------------------------------------------------------------
    // Role
    // -----------------------------------------------------------------------

    public enum Role {
        SENDER,   // this device sent the token
        RECEIVER  // this device received the token
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String txId;
    private final String tokenId;
    private final String senderPublicKey;
    private final String receiverPublicKey;
    private final long   timestamp;
    private final String nonce;
    private final String txSignature;     // sender's signature on the transaction
    private final String ackSignature;    // receiver's signature on the ACK
    private final long   committedAtMs;   // when Phase 4 completed on this device
    private final Role   role;

    public TransactionProof(String txId, String tokenId,
                            String senderPublicKey, String receiverPublicKey,
                            long timestamp, String nonce,
                            String txSignature, String ackSignature,
                            long committedAtMs, Role role) {
        this.txId              = txId;
        this.tokenId           = tokenId;
        this.senderPublicKey   = senderPublicKey;
        this.receiverPublicKey = receiverPublicKey;
        this.timestamp         = timestamp;
        this.nonce             = nonce;
        this.txSignature       = txSignature;
        this.ackSignature      = ackSignature;
        this.committedAtMs     = committedAtMs;
        this.role              = role;
    }

    // -----------------------------------------------------------------------
    // Serialization — for sync with Member 3's backend
    // -----------------------------------------------------------------------

    /**
     * Serialize to JSON for inclusion in SyncPayload sent to Member 3.
     * This matches the SOTPN integration contract format.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("tx_id",               txId);
        obj.put("token_id",            tokenId);
        obj.put("sender",              senderPublicKey);
        obj.put("receiver",            receiverPublicKey);
        obj.put("timestamp",           timestamp);
        obj.put("nonce",               nonce);
        obj.put("signature",           txSignature);
        obj.put("ack_signature",       ackSignature);
        obj.put("committed_at",        committedAtMs);
        obj.put("role",                role.name());
        return obj;
    }

    public static TransactionProof fromJson(JSONObject obj) throws JSONException {
        return new TransactionProof(
                obj.getString("tx_id"),
                obj.getString("token_id"),
                obj.getString("sender"),
                obj.getString("receiver"),
                obj.getLong("timestamp"),
                obj.getString("nonce"),
                obj.getString("signature"),
                obj.getString("ack_signature"),
                obj.getLong("committed_at"),
                Role.valueOf(obj.getString("role"))
        );
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public String getTxId()              { return txId; }
    public String getTokenId()           { return tokenId; }
    public String getSenderPublicKey()   { return senderPublicKey; }
    public String getReceiverPublicKey() { return receiverPublicKey; }
    public long   getTimestamp()         { return timestamp; }
    public String getNonce()             { return nonce; }
    public String getTxSignature()       { return txSignature; }
    public String getAckSignature()      { return ackSignature; }
    public long   getCommittedAtMs()     { return committedAtMs; }
    public Role   getRole()              { return role; }

    @Override
    public String toString() {
        return "TransactionProof{txId=" + txId
                + ", tokenId=" + tokenId
                + ", role=" + role
                + ", committedAt=" + committedAtMs + "}";
    }
}
