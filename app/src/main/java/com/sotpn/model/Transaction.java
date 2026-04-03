package com.sotpn.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : Transaction.java
 * Package  : com.sotpn.model
 * Step     : Step 2 - Transaction Model
 * Status   : Complete
 *
 * -----------------------------------------------------------------------
 * The core data object representing an offline payment.
 *
 * FIELDS (from SOTPN protocol):
 *   - txId        : Unique ID for this transaction attempt.
 *   - tokenId     : The ID of the specific token being spent.
 *   - sender      : Sender's public key (Base64).
 *   - receiver    : Receiver's public key (Base64).
 *   - timestamp   : Unix timestamp (ms) when transaction was created.
 *   - nonce       : Unique random string to prevent replay attacks.
 *   - signature   : Sign(sender_priv_key, tokenId+receiver+timestamp+nonce).
 *
 * This object is converted to JSON for transmission over BLE or Wi-Fi Direct.
 * -----------------------------------------------------------------------
 */
public class Transaction {

    private String txId;
    private String tokenId;
    private String senderPublicKey;
    private String receiverPublicKey;
    private long timestamp;
    private String nonce;
    private String signature;

    // Local state (not transmitted)
    private TransactionPhase phase;

    public Transaction() {
        this.phase = TransactionPhase.PREPARE;
    }

    public Transaction(String txId, String tokenId, String sender, String receiver,
                       long timestamp, String nonce, String signature) {
        this.txId = txId;
        this.tokenId = tokenId;
        this.senderPublicKey = sender;
        this.receiverPublicKey = receiver;
        this.timestamp = timestamp;
        this.nonce = nonce;
        this.signature = signature;
        this.phase = TransactionPhase.PREPARE;
    }

    // -----------------------------------------------------------------------
    // JSON Serialization
    // -----------------------------------------------------------------------

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("tx_id", txId);
        json.put("token_id", tokenId);
        json.put("sender", senderPublicKey);
        json.put("receiver", receiverPublicKey);
        json.put("timestamp", timestamp);
        json.put("nonce", nonce);
        json.put("signature", signature);
        return json;
    }

    public static Transaction fromJson(JSONObject json) throws JSONException {
        Transaction tx = new Transaction();
        tx.txId = json.getString("tx_id");
        tx.tokenId = json.getString("token_id");
        tx.senderPublicKey = json.getString("sender");
        tx.receiverPublicKey = json.getString("receiver");
        tx.timestamp = json.getLong("timestamp");
        tx.nonce = json.getString("nonce");
        tx.signature = json.getString("signature");
        return tx;
    }

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public String getTxId() {
        return txId;
    }

    public void setTxId(String id) {
        this.txId = id;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String id) {
        this.tokenId = id;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public void setSenderPublicKey(String key) {
        this.senderPublicKey = key;
    }

    public String getReceiverPublicKey() {
        return receiverPublicKey;
    }

    public void setReceiverPublicKey(String key) {
        this.receiverPublicKey = key;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long ts) {
        this.timestamp = ts;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String n) {
        this.nonce = n;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String sig) {
        this.signature = sig;
    }

    public TransactionPhase getPhase() {
        return phase;
    }

    public void setPhase(TransactionPhase p) {
        this.phase = p;
    }

    @Override
    public String toString() {
        return "Transaction{txId=" + txId + ", tokenId=" + tokenId +
                ", phase=" + phase + ", timestamp=" + timestamp + "}";
    }
}
