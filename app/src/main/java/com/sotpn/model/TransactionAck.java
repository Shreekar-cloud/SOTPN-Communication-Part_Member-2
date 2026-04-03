package com.sotpn.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;

public class TransactionAck implements Serializable {

    private String  txId;
    private String  tokenId;
    private String  receiverPublicKey;
    private long    ackTimestamp;
    private String  ackSignature;
    private boolean accepted;

    public TransactionAck(String txId, String tokenId,
                          String receiverPublicKey,
                          long ackTimestamp,
                          String ackSignature,
                          boolean accepted) {
        this.txId              = txId;
        this.tokenId           = tokenId;
        this.receiverPublicKey = receiverPublicKey;
        this.ackTimestamp      = ackTimestamp;
        this.ackSignature      = ackSignature;
        this.accepted          = accepted;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("tx_id", txId);
        obj.put("token_id", tokenId);
        obj.put("receiver_public_key", receiverPublicKey);
        obj.put("ack_timestamp", ackTimestamp);
        obj.put("ack_signature", ackSignature);
        obj.put("accepted", accepted);
        return obj;
    }

    public static TransactionAck fromJson(JSONObject obj) throws JSONException {
        return new TransactionAck(
                obj.getString("tx_id"),
                obj.getString("token_id"),
                obj.getString("receiver_public_key"),
                obj.getLong("ack_timestamp"),
                obj.getString("ack_signature"),
                obj.getBoolean("accepted")
        );
    }

    public String getTxId() {
        return txId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getReceiverPublicKey() {
        return receiverPublicKey;
    }

    public long getAckTimestamp() {
        return ackTimestamp;
    }

    public String getAckSignature() {
        return ackSignature;
    }

    public boolean isAccepted() {
        return accepted;
    }

    @Override
    public String toString() {
        return "TransactionAck{txId=" + txId
                + ", tokenId=" + tokenId
                + ", accepted=" + accepted + "}";
    }
}