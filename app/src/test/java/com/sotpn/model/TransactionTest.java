package com.sotpn.model;

import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : TransactionTest.java
 * Package  : com.sotpn.model (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/model/
 *   Right-click → Run 'TransactionTest'
 *   No phone needed.
 */
public class TransactionTest {

    private Transaction transaction;

    private static final String TX_ID      = "tx_abc123";
    private static final String TOKEN_ID   = "tok_xyz789";
    private static final String SENDER     = "sender_pubkey_abc";
    private static final String RECEIVER   = "receiver_pubkey_def";
    private static final long   TIMESTAMP  = 1712345678900L;
    private static final String NONCE      = "nonce_unique_001";
    private static final String SIGNATURE  = "sig_base64_encoded";

    @Before
    public void setUp() {
        transaction = new Transaction(
                TX_ID, TOKEN_ID, SENDER, RECEIVER,
                TIMESTAMP, NONCE, SIGNATURE);
    }

    // -----------------------------------------------------------------------
    // Test 1: All fields stored correctly
    // -----------------------------------------------------------------------
    @Test
    public void testFields_storedCorrectly() {
        assertEquals("txId must match",     TX_ID,     transaction.getTxId());
        assertEquals("tokenId must match",  TOKEN_ID,  transaction.getTokenId());
        assertEquals("sender must match",   SENDER,    transaction.getSenderPublicKey());
        assertEquals("receiver must match", RECEIVER,  transaction.getReceiverPublicKey());
        assertEquals("timestamp must match",TIMESTAMP, transaction.getTimestamp());
        assertEquals("nonce must match",    NONCE,     transaction.getNonce());
        assertEquals("signature must match",SIGNATURE, transaction.getSignature());
    }

    // -----------------------------------------------------------------------
    // Test 2: Default phase is PREPARE
    // -----------------------------------------------------------------------
    @Test
    public void testDefaultPhase_isPrepare() {
        assertEquals("Default phase should be PREPARE",
                TransactionPhase.PREPARE, transaction.getPhase());
    }

    // -----------------------------------------------------------------------
    // Test 3: Phase can be updated
    // -----------------------------------------------------------------------
    @Test
    public void testPhase_canBeUpdated() {
        transaction.setPhase(TransactionPhase.VALIDATING);
        assertEquals("Phase should be updated to VALIDATING",
                TransactionPhase.VALIDATING, transaction.getPhase());

        transaction.setPhase(TransactionPhase.FINALIZED);
        assertEquals("Phase should be updated to FINALIZED",
                TransactionPhase.FINALIZED, transaction.getPhase());
    }

    // -----------------------------------------------------------------------
    // Test 4: toJson contains all required fields
    // -----------------------------------------------------------------------
    @Test
    public void testToJson_containsAllFields() {
        try {
            String json = transaction.toJson().toString();
            assertTrue("JSON must have tx_id",     json.contains("tx_id"));
            assertTrue("JSON must have token_id",  json.contains("token_id"));
            assertTrue("JSON must have sender",    json.contains("sender"));
            assertTrue("JSON must have receiver",  json.contains("receiver"));
            assertTrue("JSON must have timestamp", json.contains("timestamp"));
            assertTrue("JSON must have nonce",     json.contains("nonce"));
            assertTrue("JSON must have signature", json.contains("signature"));
        } catch (Exception e) {
            fail("toJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 5: toJson values are correct
    // -----------------------------------------------------------------------
    @Test
    public void testToJson_valuesAreCorrect() {
        try {
            String json = transaction.toJson().toString();
            assertTrue("tx_id value must be in JSON",     json.contains(TX_ID));
            assertTrue("token_id value must be in JSON",  json.contains(TOKEN_ID));
            assertTrue("sender value must be in JSON",    json.contains(SENDER));
            assertTrue("receiver value must be in JSON",  json.contains(RECEIVER));
            assertTrue("nonce value must be in JSON",     json.contains(NONCE));
            assertTrue("signature value must be in JSON", json.contains(SIGNATURE));
        } catch (Exception e) {
            fail("toJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 6: fromJson deserializes correctly
    // -----------------------------------------------------------------------
    @Test
    public void testFromJson_deserializesCorrectly() {
        try {
            JSONObject json = transaction.toJson();
            Transaction t2  = Transaction.fromJson(json);
            assertEquals("txId must match",     TX_ID,      t2.getTxId());
            assertEquals("tokenId must match",  TOKEN_ID,   t2.getTokenId());
            assertEquals("nonce must match",    NONCE,      t2.getNonce());
            assertEquals("signature must match",SIGNATURE,  t2.getSignature());
        } catch (Exception e) {
            fail("fromJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 7: Round-trip serialization preserves all data
    // -----------------------------------------------------------------------
    @Test
    public void testRoundTrip_preservesAllData() {
        try {
            String jsonStr = transaction.toJson().toString();
            Transaction t2 = Transaction.fromJson(new JSONObject(jsonStr));
            assertEquals("Round-trip txId",    TX_ID,    t2.getTxId());
            assertEquals("Round-trip tokenId", TOKEN_ID, t2.getTokenId());
            assertEquals("Round-trip nonce",   NONCE,    t2.getNonce());
            assertEquals("Round-trip sig",     SIGNATURE,t2.getSignature());
        } catch (Exception e) {
            fail("Round-trip should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 8: toString contains txId and phase
    // -----------------------------------------------------------------------
    @Test
    public void testToString_containsTxIdAndPhase() {
        String str = transaction.toString();
        assertTrue("toString must contain txId",  str.contains(TX_ID));
        assertTrue("toString must contain phase", str.contains("PREPARE"));
    }
}