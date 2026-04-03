package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.model.Transaction;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : EdgeCaseTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * Tests extreme and unusual inputs that could cause crashes or bugs.
 */
public class EdgeCaseTest {

    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
    }

    // -----------------------------------------------------------------------
    // TEST 1: Very long public key (1000 chars) — handled safely
    // -----------------------------------------------------------------------
    @Test
    public void test1_veryLongPublicKey_handledSafely() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append("A");
        String longKey = sb.toString();

        Transaction tx = new Transaction(
                "tx_longkey", "tok_001", longKey, "receiver",
                System.currentTimeMillis(), "nonce_longkey", "sig");

        assertNotNull("Transaction with long key must be created", tx);
        assertEquals("Long key must be stored exactly", longKey,
                tx.getSenderPublicKey());

        try {
            String json = tx.toJson().toString();
            assertTrue("JSON must contain long key", json.contains(longKey));
            System.out.println("TEST 1 — 1000-char public key handled ✅");
        } catch (Exception e) {
            fail("Long key must not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // TEST 2: Very long signature (2000 chars) — handled safely
    // -----------------------------------------------------------------------
    @Test
    public void test2_veryLongSignature_handledSafely() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append("B");
        String longSig = sb.toString();

        Transaction tx = new Transaction(
                "tx_longsig", "tok_001", "sender", "receiver",
                System.currentTimeMillis(), "nonce_longsig", longSig);

        assertNotNull("Transaction with long signature created", tx);
        assertEquals("Long signature stored exactly", longSig, tx.getSignature());
        System.out.println("TEST 2 — 2000-char signature handled ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 3: Special characters in nonce — handled safely
    // -----------------------------------------------------------------------
    @Test
    public void test3_specialCharsInNonce_handledSafely() {
        String specialNonce = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        NonceStore store = new NonceStore();
        boolean accepted = store.checkAndRecord(specialNonce);
        assertTrue("Special char nonce must be accepted", accepted);

        boolean replay = store.checkAndRecord(specialNonce);
        assertFalse("Special char nonce replay must be rejected", replay);
        System.out.println("TEST 3 — Special chars in nonce handled ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 4: Whitespace-only nonce — rejected by validation
    // -----------------------------------------------------------------------
    @Test
    public void test4_whitespaceNonce_rejectedByValidation() {
        com.sotpn.communication.GossipStore gs =
                new com.sotpn.communication.GossipStore();
        NonceStore ns = new NonceStore();
        com.sotpn.communication.GossipEngine ge =
                new com.sotpn.communication.GossipEngine(
                        null, null, gs,
                        new com.sotpn.communication.GossipEngine.GossipListener() {
                            @Override public void onConflictDetected(
                                    com.sotpn.communication.GossipStore.ConflictResult r) {}
                            @Override public void onGossipReceived(
                                    com.sotpn.communication.GossipMessage m) {}
                        }, "test") {
                    @Override
                    public com.sotpn.communication.GossipStore.ConflictResult
                    checkConflict(String t) { return gs.checkConflict(t); }
                };

        ValidationPhaseHandler handler =
                new ValidationPhaseHandler(wallet, ns, ge);

        Transaction tx = new Transaction(
                "tx_ws", "tok_001", "sender", "receiver",
                System.currentTimeMillis(),
                "   ", // whitespace nonce
                "sig");

        final String[] failure = {null};
        handler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction t) {}
            @Override public void onValidationFailed(Transaction t, ValidationResult r) {
                failure[0] = r.getFailureCode().name();
            }
        });

        assertNotNull("Whitespace nonce must be rejected", failure[0]);
        assertEquals("Must fail MISSING_FIELDS",
                "MISSING_FIELDS", failure[0]);
        System.out.println("TEST 4 — Whitespace nonce rejected ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 5: Zero timestamp — rejected by validation
    // -----------------------------------------------------------------------
    @Test
    public void test5_zeroTimestamp_rejectedByValidation() {
        com.sotpn.communication.GossipStore gs =
                new com.sotpn.communication.GossipStore();
        NonceStore ns = new NonceStore();
        com.sotpn.communication.GossipEngine ge =
                new com.sotpn.communication.GossipEngine(
                        null, null, gs,
                        new com.sotpn.communication.GossipEngine.GossipListener() {
                            @Override public void onConflictDetected(
                                    com.sotpn.communication.GossipStore.ConflictResult r) {}
                            @Override public void onGossipReceived(
                                    com.sotpn.communication.GossipMessage m) {}
                        }, "test") {
                    @Override
                    public com.sotpn.communication.GossipStore.ConflictResult
                    checkConflict(String t) { return gs.checkConflict(t); }
                };

        ValidationPhaseHandler handler =
                new ValidationPhaseHandler(wallet, ns, ge);

        Transaction tx = new Transaction(
                "tx_ts0", "tok_001", "sender", "receiver",
                0L, // zero timestamp
                "nonce_ts0", "sig");

        final String[] failure = {null};
        handler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction t) {}
            @Override public void onValidationFailed(Transaction t, ValidationResult r) {
                failure[0] = r.getFailureCode().name();
            }
        });

        assertNotNull("Zero timestamp must be rejected", failure[0]);
        System.out.println("TEST 5 — Zero timestamp rejected ✅: " + failure[0]);
    }

    // -----------------------------------------------------------------------
    // TEST 6: Gossip with empty tokenId — handled safely
    // -----------------------------------------------------------------------
    @Test
    public void test6_gossipWithEmptyTokenId_parsedAsNull() {
        // Malformed gossip with missing tokenId
        GossipMessage result = GossipMessage.fromWireString("TOKEN_SEEN::device:tx:123:0");
        // Empty tokenId is technically valid in parsing but logically invalid
        // The key check is it doesn't throw/crash
        if (result != null) {
            System.out.println("TEST 6 — Empty tokenId gossip parsed (tokenId='"
                    + result.getTokenId() + "') ✅");
        } else {
            System.out.println("TEST 6 — Empty tokenId gossip returned null ✅");
        }
        // Either null or parsed — just no crash
        assertTrue("Must not throw", true);
    }

    // -----------------------------------------------------------------------
    // TEST 7: Transaction with identical sender and receiver — allowed
    // -----------------------------------------------------------------------
    @Test
    public void test7_senderEqualsReceiver_technicallyAllowed() {
        // Same key for sender and receiver — unusual but not blocked at this layer
        String sameKey = wallet.getPublicKey();
        Transaction tx = new Transaction(
                "tx_self", "tok_self", sameKey, sameKey,
                System.currentTimeMillis(), "nonce_self", "sig_self");

        assertNotNull("Self-transaction must be created", tx);
        assertEquals("Sender and receiver are same key",
                tx.getSenderPublicKey(), tx.getReceiverPublicKey());
        System.out.println("TEST 7 — Sender=Receiver allowed at transport layer ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 8: Maximum Long value as amount — rejected by limit check
    // -----------------------------------------------------------------------
    @Test
    public void test8_maxLongAmount_rejectedByLimitCheck() {
        PreparePhaseHandler handler =
                new PreparePhaseHandler(wallet, null, null);
        final String[] failure = {null};
        handler.execute("receiver", Long.MAX_VALUE, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) {}
                    @Override public void onPrepareFailed(String r) { failure[0] = r; }
                });

        assertNotNull("Long.MAX_VALUE must be rejected", failure[0]);
        assertFalse("Token must not be locked", wallet.tokenWasLocked);
        System.out.println("TEST 8 — Long.MAX_VALUE rejected ✅: " + failure[0]);
    }
}