package com.sotpn.transaction;

import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : TransactionProofTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/transaction/
 *   Right-click → Run 'TransactionProofTest'
 *   No phone needed.
 */
public class TransactionProofTest {

    private TransactionProof senderProof;
    private TransactionProof receiverProof;

    private static final String TX_ID        = "tx_abc123";
    private static final String TOKEN_ID     = "tok_xyz789";
    private static final String SENDER_KEY   = "sender_pubkey";
    private static final String RECEIVER_KEY = "receiver_pubkey";
    private static final long   TIMESTAMP    = 1712345678900L;
    private static final String NONCE        = "nonce_001";
    private static final String TX_SIG       = "tx_signature";
    private static final String ACK_SIG      = "ack_signature";
    private static final long   COMMITTED_AT = 1712345679000L;

    @Before
    public void setUp() {
        senderProof = new TransactionProof(
                TX_ID, TOKEN_ID, SENDER_KEY, RECEIVER_KEY,
                TIMESTAMP, NONCE, TX_SIG, ACK_SIG,
                COMMITTED_AT, TransactionProof.Role.SENDER);

        receiverProof = new TransactionProof(
                TX_ID, TOKEN_ID, SENDER_KEY, RECEIVER_KEY,
                TIMESTAMP, NONCE, TX_SIG, ACK_SIG,
                COMMITTED_AT, TransactionProof.Role.RECEIVER);
    }

    // -----------------------------------------------------------------------
    // Test 1: Sender proof fields stored correctly
    // -----------------------------------------------------------------------
    @Test
    public void testSenderProof_fieldsStoredCorrectly() {
        assertEquals("txId must match",        TX_ID,        senderProof.getTxId());
        assertEquals("tokenId must match",     TOKEN_ID,     senderProof.getTokenId());
        assertEquals("senderKey must match",   SENDER_KEY,   senderProof.getSenderPublicKey());
        assertEquals("receiverKey must match", RECEIVER_KEY, senderProof.getReceiverPublicKey());
        assertEquals("timestamp must match",   TIMESTAMP,    senderProof.getTimestamp());
        assertEquals("nonce must match",       NONCE,        senderProof.getNonce());
        assertEquals("txSig must match",       TX_SIG,       senderProof.getTxSignature());
        assertEquals("ackSig must match",      ACK_SIG,      senderProof.getAckSignature());
        assertEquals("committedAt must match", COMMITTED_AT, senderProof.getCommittedAtMs());
        assertEquals("role must be SENDER",
                TransactionProof.Role.SENDER, senderProof.getRole());
    }

    // -----------------------------------------------------------------------
    // Test 2: Receiver proof has correct role
    // -----------------------------------------------------------------------
    @Test
    public void testReceiverProof_hasCorrectRole() {
        assertEquals("Role must be RECEIVER",
                TransactionProof.Role.RECEIVER, receiverProof.getRole());
    }

    // -----------------------------------------------------------------------
    // Test 3: Sender and receiver roles are different
    // -----------------------------------------------------------------------
    @Test
    public void testRoles_areDifferent() {
        assertNotEquals("SENDER and RECEIVER roles must be different",
                senderProof.getRole(), receiverProof.getRole());
    }

    // -----------------------------------------------------------------------
    // Test 4: toJson contains all required fields
    // -----------------------------------------------------------------------
    @Test
    public void testToJson_containsAllFields() {
        try {
            String json = senderProof.toJson().toString();
            assertTrue("JSON must have tx_id",       json.contains("tx_id"));
            assertTrue("JSON must have token_id",    json.contains("token_id"));
            assertTrue("JSON must have sender",      json.contains("sender"));
            assertTrue("JSON must have receiver",    json.contains("receiver"));
            assertTrue("JSON must have timestamp",   json.contains("timestamp"));
            assertTrue("JSON must have nonce",       json.contains("nonce"));
            assertTrue("JSON must have signature",   json.contains("signature"));
            assertTrue("JSON must have ack_sig",     json.contains("ack_signature"));
            assertTrue("JSON must have committed_at",json.contains("committed_at"));
            assertTrue("JSON must have role",        json.contains("role"));
        } catch (Exception e) {
            fail("toJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 5: fromJson round-trip for sender proof
    // -----------------------------------------------------------------------
    @Test
    public void testFromJson_roundTrip_senderProof() {
        try {
            JSONObject json        = senderProof.toJson();
            TransactionProof proof = TransactionProof.fromJson(json);
            assertEquals("txId must match",  TX_ID,   proof.getTxId());
            assertEquals("tokenId must match",TOKEN_ID,proof.getTokenId());
            assertEquals("role must match",
                    TransactionProof.Role.SENDER, proof.getRole());
            assertEquals("ackSig must match", ACK_SIG, proof.getAckSignature());
        } catch (Exception e) {
            fail("fromJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 6: fromJson round-trip for receiver proof
    // -----------------------------------------------------------------------
    @Test
    public void testFromJson_roundTrip_receiverProof() {
        try {
            JSONObject json        = receiverProof.toJson();
            TransactionProof proof = TransactionProof.fromJson(json);
            assertEquals("role must be RECEIVER",
                    TransactionProof.Role.RECEIVER, proof.getRole());
            assertEquals("txId must match", TX_ID, proof.getTxId());
        } catch (Exception e) {
            fail("fromJson() should not throw: " + e.getMessage());
        }
    }
}