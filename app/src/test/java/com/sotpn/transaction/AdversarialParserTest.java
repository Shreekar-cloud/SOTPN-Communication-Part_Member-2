package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * ADVERSARIAL PARSER SECURITY TESTS
 * Verifies that the system is immune to malformed or malicious JSON data 
 * sent over the air.
 */
public class AdversarialParserTest {

    // -----------------------------------------------------------------------
    // TEST 1: Type Mismatch Attack
    // Attacker sends a string in the 'timestamp' field (which should be long).
    // -----------------------------------------------------------------------
    @Test
    public void testParser_TypeMismatch_DoesNotCrash() throws Exception {
        JSONObject maliciousJson = new JSONObject();
        maliciousJson.put("tx_id", "tx_123");
        maliciousJson.put("token_id", "tok_abc");
        maliciousJson.put("sender", "sender_key");
        maliciousJson.put("receiver", "receiver_key");
        maliciousJson.put("timestamp", "NOT_A_NUMBER"); // Injection
        maliciousJson.put("nonce", "nonce_123");
        maliciousJson.put("signature", "sig_123");
        
        try {
            // Should throw JSONException but NOT crash the app process
            Transaction.fromJson(maliciousJson);
            fail("Parser should have rejected non-numeric timestamp");
        } catch (org.json.JSONException e) {
            // Expected behavior: catch error and abort
            assertNotNull(e);
        }
    }

    // -----------------------------------------------------------------------
    // TEST 2: Overflow / Large Payload
    // Attacker sends a massive 1MB string in a field to cause memory pressure.
    // -----------------------------------------------------------------------
    @Test
    public void testParser_LargeStringInjection_HandledSafe() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100000; i++) sb.append("DATA"); // ~400KB string
        
        JSONObject bigJson = new JSONObject();
        bigJson.put("tx_id", "tx_1");
        bigJson.put("token_id", "tok_1");
        bigJson.put("sender", "sender_1");
        bigJson.put("receiver", "receiver_1");
        bigJson.put("timestamp", System.currentTimeMillis());
        bigJson.put("nonce", "nonce_1");
        bigJson.put("signature", sb.toString());
        
        try {
            Transaction tx = Transaction.fromJson(bigJson);
            assertEquals("tx_1", tx.getTxId());
            assertTrue(tx.getSignature().length() > 1000);
        } catch (Exception e) {
            fail("App crashed on large string - possible vulnerability: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 3: Unknown Field Injection
    // Attacker adds "ghost" fields to the JSON to confuse the model.
    // -----------------------------------------------------------------------
    @Test
    public void testParser_UnknownFields_AreIgnored() throws Exception {
        JSONObject json = new JSONObject();
        json.put("tx_id", "tx_ok");
        json.put("token_id", "tok_ok");
        json.put("sender", "s");
        json.put("receiver", "r");
        json.put("timestamp", 12345L);
        json.put("nonce", "n");
        json.put("signature", "s");
        json.put("EXTRANEOUS_FIELD_ATTACK", "some_data");

        Transaction tx = Transaction.fromJson(json);
        assertEquals("tx_ok", tx.getTxId());
        // Integrity check: model should still be valid
    }
}
