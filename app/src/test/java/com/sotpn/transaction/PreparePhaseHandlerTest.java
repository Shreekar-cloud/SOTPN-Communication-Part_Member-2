package com.sotpn.transaction;

import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PreparePhaseHandlerTest {

    private MockWallet          wallet;
    private PreparePhaseHandler handler;

    private com.sotpn.model.Transaction lastSentTransaction = null;
    private String                       lastFailureReason  = null;

    @Before
    public void setUp() {
        wallet  = new MockWallet();
        handler = new PreparePhaseHandler(wallet, null, null);
        lastSentTransaction = null;
        lastFailureReason   = null;
    }

    private PreparePhaseHandler.PrepareListener makeListener() {
        return new PreparePhaseHandler.PrepareListener() {
            @Override
            public void onPrepareSent(com.sotpn.model.Transaction t) {
                lastSentTransaction = t;
            }
            @Override
            public void onPrepareFailed(String reason) {
                lastFailureReason = reason;
            }
        };
    }

    @Test
    public void testZeroAmount_isRejected() {
        handler.execute("receiver_key", 0, true, makeListener());
        ShadowLooper.idleMainLooper();
        assertNotNull("Zero amount should fail", lastFailureReason);
        assertFalse("Token should NOT be locked", wallet.tokenWasLocked);
    }

    @Test
    public void testAmountOverLimit_isRejected() {
        long overLimit = PreparePhaseHandler.MAX_TRANSACTION_PAISE + 1;
        handler.execute("receiver_key", overLimit, true, makeListener());
        ShadowLooper.idleMainLooper();
        assertNotNull("Amount over limit should fail", lastFailureReason);
        assertFalse("Token should NOT be locked", wallet.tokenWasLocked);
    }

    @Test
    public void testInsufficientBalance_isRejected() {
        wallet.balance = 1000L;
        handler.execute("receiver_key", 50_000L, true, makeListener());
        ShadowLooper.idleMainLooper();
        assertNotNull("Insufficient balance should fail", lastFailureReason);
    }

    @Test
    public void testNoToken_isRejected() {
        wallet.tokenToReturn = null;
        handler.execute("receiver_key", 10_000L, true, makeListener());
        ShadowLooper.idleMainLooper();
        assertNotNull("No token should fail", lastFailureReason);
    }

    @Test
    public void testExpiredToken_isRejected() {
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_expired", 50_000L,
                System.currentTimeMillis() - 10_000);
        handler.execute("receiver_key", 10_000L, true, makeListener());
        ShadowLooper.idleMainLooper();
        assertNotNull("Expired token should fail", lastFailureReason);
        assertFalse("Expired token should NOT be locked", wallet.tokenWasLocked);
    }

    @Test
    public void testLockFailure_isHandledGracefully() {
        wallet.lockShouldSucceed = false;
        handler.execute("receiver_key", 10_000L, true, makeListener());
        ShadowLooper.idleMainLooper();
        assertNotNull("Lock failure should fail", lastFailureReason);
    }

    @Test
    public void testValidAmount_tokenLockedAndSigned() {
        handler.execute("receiver_key", 10_000L, true, makeListener());
        ShadowLooper.idleMainLooper();
        assertTrue("Token should be locked", wallet.tokenWasLocked);
        assertTrue("sign should be called", wallet.signCallCount.get() > 0);
    }

    @Test
    public void testAbort_unlocksToken() {
        handler.execute("receiver_key", 10_000L, true, makeListener());
        ShadowLooper.idleMainLooper();
        handler.abort();
        assertTrue("Abort should unlock token", wallet.tokenWasUnlocked);
    }

    @Test
    public void testMaxTransactionLimit_is500Rupees() {
        assertEquals(50_000L, PreparePhaseHandler.MAX_TRANSACTION_PAISE);
    }

    @Test
    public void testMaxWalletLimit_is2000Rupees() {
        assertEquals(200_000L, PreparePhaseHandler.MAX_WALLET_PAISE);
    }

    @Test
    public void testExactLimit_isAccepted() {
        // Exactly ₹500 should be accepted
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_exact", 50_000L,
                System.currentTimeMillis() + 60_000);
        handler.execute("receiver_key",
                PreparePhaseHandler.MAX_TRANSACTION_PAISE,
                true, makeListener());
        ShadowLooper.idleMainLooper();
        assertTrue("Exactly ₹500 should be accepted",
                wallet.tokenWasLocked);
    }
}
