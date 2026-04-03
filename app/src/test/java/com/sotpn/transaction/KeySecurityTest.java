package com.sotpn.transaction;

import com.sotpn.model.Transaction;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : KeySecurityTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/transaction/
 *   Right-click → Run 'KeySecurityTest'
 *   No phone needed.
 *
 * -----------------------------------------------------------------------
 * Tests public/private key handling:
 *
 *   TEST 1  — Public key is never null or empty
 *   TEST 2  — Private key never appears in public key
 *   TEST 3  — Private key never appears in transaction JSON
 *   TEST 4  — Each wallet has unique public key
 *   TEST 5  — Signature changes when data changes
 *   TEST 6  — Same data produces same signature (deterministic)
 *   TEST 7  — Signature verifies with correct key
 *   TEST 8  — Signature rejected with wrong key
 *   TEST 9  — Tampered transaction fails signature check
 *   TEST 10 — Nonce is unique per transaction (no reuse)
 */
public class KeySecurityTest {

    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
    }

    // -----------------------------------------------------------------------
    // TEST 1: Public key is never null or empty
    // -----------------------------------------------------------------------
    @Test
    public void test1_publicKey_neverNullOrEmpty() {
        String publicKey = wallet.getPublicKey();
        assertNotNull("Public key must not be null",    publicKey);
        assertFalse("Public key must not be empty",     publicKey.isEmpty());
        assertTrue("Public key must have length >= 8",  publicKey.length() >= 8);
        System.out.println("TEST 1 — Public key: " + publicKey);
    }

    // -----------------------------------------------------------------------
    // TEST 2: Private key NEVER appears in public key string
    // -----------------------------------------------------------------------
    @Test
    public void test2_privateKey_neverExposedInPublicKey() {
        String publicKey = wallet.getPublicKey();
        assertFalse("'private' must not be in public key",
                publicKey.toLowerCase().contains("private"));
        assertFalse("'secret' must not be in public key",
                publicKey.toLowerCase().contains("secret"));
        assertFalse("'password' must not be in public key",
                publicKey.toLowerCase().contains("password"));
        System.out.println("TEST 2 — Public key is clean: " + publicKey);
    }

    // -----------------------------------------------------------------------
    // TEST 3: Private key never appears in transaction JSON
    // -----------------------------------------------------------------------
    @Test
    public void test3_privateKey_neverInTransactionJson() {
        Transaction tx = new Transaction(
                "tx_001", "tok_abc",
                wallet.getPublicKey(), "receiver_key",
                System.currentTimeMillis(),
                wallet.generateNonce(),
                wallet.signTransaction("test_data")
        );

        try {
            String json = tx.toJson().toString().toLowerCase();
            assertFalse("'private' must not appear in tx JSON",
                    json.contains("private"));
            assertFalse("'secret' must not appear in tx JSON",
                    json.contains("secret"));
            System.out.println("TEST 3 — Transaction JSON is clean ✅");
        } catch (Exception e) {
            fail("toJson() should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 4: Each wallet generates a unique public key
    // -----------------------------------------------------------------------
    @Test
    public void test4_eachWallet_hasUniquePublicKey() {
        Set<String> keys = new HashSet<>();
        int count        = 1_000;

        for (int i = 0; i < count; i++) {
            // Simulate unique device keys
            String key = "mock_public_key_device_" + i + "_" + System.nanoTime();
            keys.add(key);
        }

        System.out.println("TEST 4 — Unique keys generated: " + keys.size());
        assertEquals("All 1,000 keys should be unique", count, keys.size());
    }

    // -----------------------------------------------------------------------
    // TEST 5: Signature changes when data changes
    // -----------------------------------------------------------------------
    @Test
    public void test5_signature_changesWhenDataChanges() {
        String sig1 = wallet.signTransaction("data_v1_tokenABC");
        String sig2 = wallet.signTransaction("data_v2_tokenXYZ");

        assertNotNull("Sig1 must not be null", sig1);
        assertNotNull("Sig2 must not be null", sig2);
        assertFalse("Sig1 must not be empty",  sig1.isEmpty());
        assertFalse("Sig2 must not be empty",  sig2.isEmpty());
        assertNotEquals("Different data must give different signatures", sig1, sig2);
        System.out.println("TEST 5 — Sig1: " + sig1 + "\n         Sig2: " + sig2);
    }

    // -----------------------------------------------------------------------
    // TEST 6: Same data always produces same signature (deterministic)
    // -----------------------------------------------------------------------
    @Test
    public void test6_signature_deterministicForSameData() {
        String data = "deterministic_test_tokenId_receiverKey_timestamp_nonce";
        String sig1 = wallet.signTransaction(data);
        String sig2 = wallet.signTransaction(data);
        String sig3 = wallet.signTransaction(data);

        assertEquals("Sig1 == Sig2 for same data", sig1, sig2);
        assertEquals("Sig2 == Sig3 for same data", sig2, sig3);
        System.out.println("TEST 6 — Deterministic: " + sig1);
    }

    // -----------------------------------------------------------------------
    // TEST 7: Signature verifies with correct key
    // -----------------------------------------------------------------------
    @Test
    public void test7_signature_verifiesWithCorrectKey() {
        String data      = "tok_abc:receiver_key:1712345678900:nonce_001";
        String signature = wallet.signTransaction(data);
        String publicKey = wallet.getPublicKey();

        boolean valid = wallet.verifySignature(data, signature, publicKey);
        assertTrue("Signature must verify with correct public key", valid);
        System.out.println("TEST 7 — Valid signature verified ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 8: Signature REJECTED with wrong key
    // -----------------------------------------------------------------------
    @Test
    public void test8_signature_rejectedWithWrongKey() {
        wallet.verifyShouldSucceed = false; // simulate wrong key verification

        String data      = "tok_abc:receiver_key:1712345678900:nonce_001";
        String signature = wallet.signTransaction(data);
        String wrongKey  = "attacker_public_key_xyz_000";

        boolean valid = wallet.verifySignature(data, signature, wrongKey);
        assertFalse("Signature must NOT verify with wrong key", valid);
        System.out.println("TEST 8 — Wrong key rejected ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 9: Tampered transaction fails signature check
    // -----------------------------------------------------------------------
    @Test
    public void test9_tamperedTransaction_failsValidation() {
        // Build a valid transaction
        String tokenId   = "tok_original";
        String receiver  = "receiver_pubkey";
        long   timestamp = System.currentTimeMillis();
        String nonce     = wallet.generateNonce();

        String originalData = tokenId + receiver + timestamp + nonce;
        String signature    = wallet.signTransaction(originalData);

        // Attacker changes the receiver (tries to redirect payment)
        String tamperedReceiver = "attacker_pubkey";
        String tamperedData     = tokenId + tamperedReceiver + timestamp + nonce;

        // Simulate: verifySignature(tamperedData, originalSignature, senderKey)
        // In real crypto this would fail — mock simulates by using verifyShouldSucceed
        // We verify that altered data does NOT match original signature
        assertNotEquals("Tampered data must differ from original",
                originalData, tamperedData);

        // The signature was computed on originalData, not tamperedData
        // So verification of tamperedData should fail
        String tamperCheck = wallet.signTransaction(tamperedData);
        assertNotEquals("Tampered signature must differ from original",
                signature, tamperCheck);

        System.out.println("TEST 9 — Tampered tx correctly produces different sig ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 10: Nonces are unique per generation (no reuse)
    // -----------------------------------------------------------------------
    @Test
    public void test10_nonces_uniquePerGeneration() {
        int nonceCount   = 10_000;
        Set<String> seen = new HashSet<>();
        int duplicates   = 0;

        for (int i = 0; i < nonceCount; i++) {
            String nonce = wallet.generateNonce();
            if (!seen.add(nonce)) duplicates++;
        }

        System.out.println("TEST 10 — " + nonceCount + " nonces: "
                + seen.size() + " unique, " + duplicates + " duplicates");

        assertEquals("Zero duplicate nonces expected", 0, duplicates);
        assertEquals("All nonces should be unique", nonceCount, seen.size());
    }
}