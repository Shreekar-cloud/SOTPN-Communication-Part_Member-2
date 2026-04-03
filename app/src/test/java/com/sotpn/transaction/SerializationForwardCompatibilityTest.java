package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * SERIALIZATION FORWARD COMPATIBILITY TESTS
 * Verifies that the system handles legacy or corrupted data structures 
 * without crashing.
 */
public class SerializationForwardCompatibilityTest {

    // -----------------------------------------------------------------------
    // TEST 1: Missing Legacy Fields
    // Simulates a Proof from an older version that is missing the 'ack_signature' 
    // or 'committed_at' fields.
    // -----------------------------------------------------------------------
    @Test
    public void testSerialization_MissingLegacyFields_DoesNotCrash() {
        try {
            JSONObject legacyJson = new JSONObject();
            legacyJson.put("tx_id", "tx_old_version");
            legacyJson.put("token_id", "tok_123");
            legacyJson.put("sender", "s");
            legacyJson.put("receiver", "r");
            legacyJson.put("timestamp", 12345L);
            legacyJson.put("nonce", "n");
            legacyJson.put("signature", "s");
            // MISSING: ack_signature, committed_at, role
            
            // Should throw JSONException but handle it gracefully via catch
            TransactionProof.fromJson(legacyJson);
            fail("Parser should have caught missing fields in mandatory model");
        } catch (org.json.JSONException e) {
            // Expected: Security protocol requires these fields, so error is safe
            assertNotNull(e);
        }
    }

    // -----------------------------------------------------------------------
    // TEST 2: Data Type Drift
    // Simulates a field having the wrong data type (e.g. an array where a 
    // string was expected) due to data corruption.
    // -----------------------------------------------------------------------
    @Test
    public void testSerialization_DataTypeDrift_IsCaught() throws Exception {
        JSONObject corruptedJson = new JSONObject();
        corruptedJson.put("tx_id", new JSONObject()); // Expected String
        
        try {
            Transaction.fromJson(corruptedJson);
            fail("Parser should have failed on corrupted Object-as-String");
        } catch (org.json.JSONException e) {
            assertNotNull(e);
        }
    }

    // -----------------------------------------------------------------------
    // TEST 3: Null vs Empty Handling
    // Ensures that 'null' in JSON is handled differently than an empty string.
    // -----------------------------------------------------------------------
    @Test
    public void testSerialization_NullFieldHandling() throws Exception {
        JSONObject json = new JSONObject();
        json.put("tx_id", JSONObject.NULL);
        
        try {
            Transaction.fromJson(json);
            fail("Parser should reject explicit NULL values for mandatory IDs");
        } catch (org.json.JSONException e) {
            assertNotNull(e);
        }
    }
}
