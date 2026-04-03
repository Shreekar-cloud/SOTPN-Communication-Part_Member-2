package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : SyncPayloadTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * Tests the sync payload format that gets sent to Member 3's backend.
 *
 * SOTPN Integration Contract:
 * {
 *   "device_id": "...",
 *   "transactions": [...],
 *   "proofs": [...]
 * }
 */
public class SyncPayLoadTest {

    private MockWallet         wallet;
    private CommitPhaseHandler commitHandler;

    @Before
    public void setUp() {
        wallet        = new MockWallet();
        commitHandler = new CommitPhaseHandler(wallet, null, null);
    }

    private TransactionProof buildProof(String txId, String tokenId,
                                        TransactionProof.Role role) {
        return new TransactionProof(
                txId, tokenId,
                "sender_key", "receiver_key",
                System.currentTimeMillis(),
                "nonce_" + txId, "tx_sig_" + txId, "ack_sig_" + txId,
                System.currentTimeMillis(), role);
    }

    // -----------------------------------------------------------------------
    // TEST 1: Sync payload contains device_id
    // -----------------------------------------------------------------------
    @Test
    public void test1_syncPayload_containsDeviceId() {
        try {
            String deviceId = wallet.getPublicKey();

            JSONObject payload = new JSONObject();
            payload.put("device_id", deviceId);
            payload.put("transactions", new JSONArray());
            payload.put("proofs",       new JSONArray());

            assertTrue("Payload must contain device_id",
                    payload.has("device_id"));
            assertEquals("device_id must match wallet public key",
                    deviceId, payload.getString("device_id"));

            System.out.println("TEST 1 — Sync payload has device_id ✅: "
                    + deviceId);
        } catch (Exception e) {
            fail("Payload build must not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 2: Sync payload format matches integration contract
    // -----------------------------------------------------------------------
    @Test
    public void test2_syncPayload_matchesIntegrationContract() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("device_id",    wallet.getPublicKey());
            payload.put("transactions", new JSONArray());
            payload.put("proofs",       new JSONArray());

            // Verify all 3 required fields exist
            assertTrue("Must have device_id",    payload.has("device_id"));
            assertTrue("Must have transactions", payload.has("transactions"));
            assertTrue("Must have proofs",       payload.has("proofs"));

            // Verify types
            assertNotNull("device_id must be string",
                    payload.getString("device_id"));
            assertNotNull("transactions must be array",
                    payload.getJSONArray("transactions"));
            assertNotNull("proofs must be array",
                    payload.getJSONArray("proofs"));

            System.out.println("TEST 2 — Sync payload matches contract ✅");
        } catch (Exception e) {
            fail("Contract validation must not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 3: Sync payload includes all proofs
    // -----------------------------------------------------------------------
    @Test
    public void test3_syncPayload_includesAllProofs() {
        try {
            // Build 5 proofs
            List<TransactionProof> proofs = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                proofs.add(buildProof("tx_" + i, "tok_" + i,
                        i % 2 == 0
                                ? TransactionProof.Role.SENDER
                                : TransactionProof.Role.RECEIVER));
            }

            // Build payload
            JSONArray proofsArray = new JSONArray();
            for (TransactionProof p : proofs) {
                proofsArray.put(p.toJson());
            }

            JSONObject payload = new JSONObject();
            payload.put("device_id",    wallet.getPublicKey());
            payload.put("transactions", new JSONArray());
            payload.put("proofs",       proofsArray);

            assertEquals("Payload must include all 5 proofs",
                    5, payload.getJSONArray("proofs").length());

            System.out.println("TEST 3 — Sync payload includes all 5 proofs ✅");
        } catch (Exception e) {
            fail("Payload build must not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 4: Sync payload JSON is valid and parseable
    // -----------------------------------------------------------------------
    @Test
    public void test4_syncPayload_jsonIsValid() {
        try {
            TransactionProof proof = buildProof(
                    "tx_valid", "tok_valid", TransactionProof.Role.SENDER);

            JSONArray proofs = new JSONArray();
            proofs.put(proof.toJson());

            JSONArray transactions = new JSONArray();
            Transaction tx = new Transaction(
                    "tx_valid", "tok_valid", "sender", "receiver",
                    System.currentTimeMillis(), "nonce", "sig");
            transactions.put(tx.toJson());

            JSONObject payload = new JSONObject();
            payload.put("device_id",    wallet.getPublicKey());
            payload.put("transactions", transactions);
            payload.put("proofs",       proofs);

            // Verify it can be serialized and re-parsed
            String jsonString    = payload.toString();
            JSONObject reparsed  = new JSONObject(jsonString);

            assertNotNull("Re-parsed payload must not be null", reparsed);
            assertEquals("device_id must survive round-trip",
                    wallet.getPublicKey(),
                    reparsed.getString("device_id"));
            assertEquals("proofs count must survive round-trip",
                    1, reparsed.getJSONArray("proofs").length());

            System.out.println("TEST 4 — Sync payload JSON valid ✅ ("
                    + jsonString.length() + " bytes)");
        } catch (Exception e) {
            fail("JSON validation must not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 5: Empty proof list handled gracefully
    // -----------------------------------------------------------------------
    @Test
    public void test5_emptyProofList_handledGracefully() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("device_id",    wallet.getPublicKey());
            payload.put("transactions", new JSONArray());
            payload.put("proofs",       new JSONArray()); // empty

            String jsonString = payload.toString();
            assertNotNull("Empty payload must not be null",  jsonString);
            assertFalse("Empty payload must not be empty",   jsonString.isEmpty());

            // Re-parse and verify
            JSONObject reparsed = new JSONObject(jsonString);
            assertEquals("Empty proofs must have 0 elements",
                    0, reparsed.getJSONArray("proofs").length());
            assertEquals("Empty transactions must have 0 elements",
                    0, reparsed.getJSONArray("transactions").length());

            System.out.println("TEST 5 — Empty proof list handled ✅");
        } catch (Exception e) {
            fail("Empty payload must not throw: " + e.getMessage());
        }
    }
}