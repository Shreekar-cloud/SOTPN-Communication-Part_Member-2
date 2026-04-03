package com.sotpn.model;

import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : TransactionAckTest.java
 * Package  : com.sotpn.model (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/model/
 *   Right-click → Run 'TransactionAckTest'
 *   No phone needed.
 */
public class TransactionAckTest {

    private TransactionAck acceptedAck;
    private TransactionAck rejectedAck;

    private static final String TX_ID        = "tx_abc123";
    private static final String TOKEN_ID     = "tok_xyz789";
    private static final String RECEIVER_KEY = "receiver_pubkey_abc";
    private static final long   TIMESTAMP    = 1712345678900L;
    private static final String SIGNATURE    = "ack_sig_base64";

    @Before
    public void setUp() {
        acceptedAck = new TransactionAck(
                TX_ID, TOKEN_ID, RECEIVER_KEY,
                TIMESTAMP, SIGNATURE, true);

        rejectedAck = new TransactionAck(
                TX_ID, TOKEN_ID, RECEIVER_KEY,
                TIMESTAMP, SIGNATURE, false);
    }

    // -----------------------------------------------------------------------
    // Test 1: Accepted ACK fields stored correctly
    // -----------------------------------------------------------------------
    @Test
    public void testAcceptedAck_fieldsStoredCorrectly() {
        assertEquals("txId must match",        TX_ID,        acceptedAck.getTxId());
        assertEquals("tokenId must match",     TOKEN_ID,     acceptedAck.getTokenId());
        assertEquals("receiverKey must match", RECEIVER_KEY, acceptedAck.getReceiverPublicKey());
        assertEquals("timestamp must match",   TIMESTAMP,    acceptedAck.getAckTimestamp());
        assertEquals("signature must match",   SIGNATURE,    acceptedAck.getAckSignature());
        assertTrue("Accepted ACK should return isAccepted=true", acceptedAck.isAccepted());
    }

    // -----------------------------------------------------------------------
    // Test 2: Rejected ACK isAccepted returns false
    // -----------------------------------------------------------------------
    @Test
    public void testRejectedAck_isAcceptedFalse() {
        assertFalse("Rejected ACK should return isAccepted=false",
                rejectedAck.isAccepted());
    }

    // -----------------------------------------------------------------------
    // Test 3: toJson contains all required fields
    // -----------------------------------------------------------------------
    @Test
    public void testToJson_containsAllFields() {
        try {
            String json = acceptedAck.toJson().toString();
            assertTrue("JSON must have tx_id",              json.contains("tx_id"));
            assertTrue("JSON must have token_id",           json.contains("token_id"));
            assertTrue("JSON must have receiver_public_key",json.contains("receiver_public_key"));
            assertTrue("JSON must have ack_timestamp",      json.contains("ack_timestamp"));
            assertTrue("JSON must have ack_signature",      json.contains("ack_signature"));
            assertTrue("JSON must have accepted",           json.contains("accepted"));
        } catch (Exception e) {
            fail("toJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 4: toJson accepted flag is correct
    // -----------------------------------------------------------------------
    @Test
    public void testToJson_acceptedFlagCorrect() {
        try {
            String acceptedJson = acceptedAck.toJson().toString();
            String rejectedJson = rejectedAck.toJson().toString();
            assertTrue("Accepted JSON should contain true",  acceptedJson.contains("true"));
            assertTrue("Rejected JSON should contain false", rejectedJson.contains("false"));
        } catch (Exception e) {
            fail("toJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 5: fromJson round-trip for accepted ACK
    // -----------------------------------------------------------------------
    @Test
    public void testFromJson_roundTrip_acceptedAck() {
        try {
            JSONObject json     = acceptedAck.toJson();
            TransactionAck ack2 = TransactionAck.fromJson(json);
            assertEquals("txId must match",    TX_ID,    ack2.getTxId());
            assertEquals("tokenId must match", TOKEN_ID, ack2.getTokenId());
            assertEquals("sig must match",     SIGNATURE,ack2.getAckSignature());
            assertTrue("isAccepted must match", ack2.isAccepted());
        } catch (Exception e) {
            fail("fromJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Test 6: fromJson round-trip for rejected ACK
    // -----------------------------------------------------------------------
    @Test
    public void testFromJson_roundTrip_rejectedAck() {
        try {
            JSONObject json     = rejectedAck.toJson();
            TransactionAck ack2 = TransactionAck.fromJson(json);
            assertFalse("Rejected ACK isAccepted must be false", ack2.isAccepted());
            assertEquals("txId must match", TX_ID, ack2.getTxId());
        } catch (Exception e) {
            fail("fromJson() should not throw: " + e.getMessage());
        }
    }
}