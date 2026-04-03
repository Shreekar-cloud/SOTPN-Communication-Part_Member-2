package com.sotpn.transaction;

import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : TransactionBoundaryTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * Tests exact boundary values from the SOTPN spec:
 *   Max per transaction : ₹500  (50,000 paise)
 *   Max wallet balance  : ₹2000 (200,000 paise)
 */
public class TransactionBoundaryTest {

    private MockWallet          wallet;
    private PreparePhaseHandler handler;

    private String lastFailure = null;
    private boolean lastSent   = false;

    @Before
    public void setUp() {
        wallet  = new MockWallet();
        handler = new PreparePhaseHandler(wallet, null, null);
        reset();
    }

    private void reset() {
        lastFailure = null;
        lastSent    = false;
        wallet.reset();
        handler     = new PreparePhaseHandler(wallet, null, null);
    }

    private PreparePhaseHandler.PrepareListener listener() {
        return new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(com.sotpn.model.Transaction tx) {
                lastSent = true;
            }
            @Override public void onPrepareFailed(String reason) {
                lastFailure = reason;
            }
        };
    }

    // -----------------------------------------------------------------------
    // TEST 1: Send exactly ₹1 (minimum valid amount)
    // -----------------------------------------------------------------------
    @Test
    public void test1_exactlyOneRupee_accepted() {
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_001", 100L, System.currentTimeMillis() + 60_000);
        handler.execute("receiver", 100L, true, listener()); // ₹1 = 100 paise
        assertTrue("₹1 should be accepted", wallet.tokenWasLocked);
        assertNull("No failure for ₹1", lastFailure);
        System.out.println("TEST 1 — ₹1 minimum: ACCEPTED ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 2: Send exactly ₹500 (maximum valid amount)
    // -----------------------------------------------------------------------
    @Test
    public void test2_exactlyFiveHundredRupees_accepted() {
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_002", 50_000L, System.currentTimeMillis() + 60_000);
        handler.execute("receiver", 50_000L, true, listener()); // ₹500
        assertTrue("₹500 should be accepted", wallet.tokenWasLocked);
        assertNull("No failure for ₹500", lastFailure);
        System.out.println("TEST 2 — ₹500 maximum: ACCEPTED ✅");
    }

    // -----------------------------------------------------------------------
    // TEST 3: Send ₹501 (one paise over limit)
    // -----------------------------------------------------------------------
    @Test
    public void test3_fiveHundredOneRupees_rejected() {
        handler.execute("receiver", 50_001L, true, listener()); // ₹500.01
        assertNotNull("₹500.01 should be rejected", lastFailure);
        assertFalse("Token should NOT be locked", wallet.tokenWasLocked);
        assertTrue("Error should mention limit",
                lastFailure.contains("exceeds") || lastFailure.contains("limit"));
        System.out.println("TEST 3 — ₹501 over limit: REJECTED ✅ — " + lastFailure);
    }

    // -----------------------------------------------------------------------
    // TEST 4: Send ₹0
    // -----------------------------------------------------------------------
    @Test
    public void test4_zeroRupees_rejected() {
        handler.execute("receiver", 0L, true, listener());
        assertNotNull("₹0 should be rejected", lastFailure);
        assertFalse("Token should NOT be locked", wallet.tokenWasLocked);
        System.out.println("TEST 4 — ₹0: REJECTED ✅ — " + lastFailure);
    }

    // -----------------------------------------------------------------------
    // TEST 5: Send negative amount
    // -----------------------------------------------------------------------
    @Test
    public void test5_negativeAmount_rejected() {
        handler.execute("receiver", -100L, true, listener());
        assertNotNull("Negative amount should be rejected", lastFailure);
        assertFalse("Token should NOT be locked", wallet.tokenWasLocked);
        System.out.println("TEST 5 — Negative amount: REJECTED ✅ — " + lastFailure);
    }

    // -----------------------------------------------------------------------
    // TEST 6: Empty wallet (balance = 0)
    // -----------------------------------------------------------------------
    @Test
    public void test6_emptyWallet_rejected() {
        wallet.balance = 0L;
        handler.execute("receiver", 100L, true, listener());
        assertNotNull("Empty wallet should be rejected", lastFailure);
        assertFalse("Token should NOT be locked", wallet.tokenWasLocked);
        System.out.println("TEST 6 — Empty wallet: REJECTED ✅ — " + lastFailure);
    }

    // -----------------------------------------------------------------------
    // TEST 7: Insufficient balance (have ₹100, send ₹200)
    // -----------------------------------------------------------------------
    @Test
    public void test7_insufficientBalance_rejected() {
        wallet.balance = 10_000L; // ₹100
        handler.execute("receiver", 20_000L, true, listener()); // try ₹200
        assertNotNull("Insufficient balance should be rejected", lastFailure);
        assertFalse("Token should NOT be locked", wallet.tokenWasLocked);
        assertTrue("Error should mention balance",
                lastFailure.contains("balance") || lastFailure.contains("Insufficient"));
        System.out.println("TEST 7 — Insufficient balance: REJECTED ✅ — " + lastFailure);
    }

    // -----------------------------------------------------------------------
    // TEST 8: Send exact wallet balance (boundary — should pass)
    // -----------------------------------------------------------------------
    @Test
    public void test8_exactWalletBalance_accepted() {
        wallet.balance = 10_000L; // ₹100
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_008", 10_000L, System.currentTimeMillis() + 60_000);
        handler.execute("receiver", 10_000L, true, listener()); // exactly ₹100
        assertTrue("Exact balance amount should be accepted", wallet.tokenWasLocked);
        assertNull("No failure for exact balance", lastFailure);
        System.out.println("TEST 8 — Exact balance: ACCEPTED ✅");
    }
}