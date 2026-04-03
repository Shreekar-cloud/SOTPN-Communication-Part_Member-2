package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TransactionTimeoutTest {

    private MockWallet wallet;
    private ValidationPhaseHandler handler;
    private NonceStore ns;
    private GossipStore gs;
    private GossipEngine ge;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        wallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_timeout", 10_000L,
                System.currentTimeMillis() + 60_000);
        
        ns = new NonceStore();
        gs = new GossipStore();
        ge = new GossipEngine(mock(BleManager.class), mock(WifiDirectManager.class), 
                              gs, mock(GossipEngine.GossipListener.class), "test_device");
        
        handler = new ValidationPhaseHandler(wallet, ns, ge);
    }

    private void signTx(Transaction tx) {
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(wallet.signTransaction(data));
    }

    @Test
    public void test1_transactionJustBeforeExpiry_accepted() {
        Transaction tx = new Transaction("tx_1", "tok_timeout", wallet.getPublicKey(), "receiver",
                                         System.currentTimeMillis() - 59_000, "nonce_1", "");
        signTx(tx);

        final boolean[] passed = {false};
        handler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction t) { passed[0] = true; }
            @Override public void onValidationFailed(Transaction t, ValidationResult r) {}
        });
        ShadowLooper.idleMainLooper();

        assertTrue("Transaction 59s old should be accepted", passed[0]);
    }

    @Test
    public void test2_transactionAtExactExpiry_rejected() {
        Transaction tx = new Transaction("tx_2", "tok_timeout", wallet.getPublicKey(), "receiver",
                                         System.currentTimeMillis() - 60_001, "nonce_2", "");
        signTx(tx);

        final String[] failure = {null};
        handler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction t) {}
            @Override public void onValidationFailed(Transaction t, ValidationResult r) {
                failure[0] = r.getFailureCode().name();
            }
        });
        ShadowLooper.idleMainLooper();

        assertEquals("TOKEN_EXPIRED", failure[0]);
    }

    @Test
    public void test3_delayExpires_movesToCommit() {
        AdaptiveDelayCalculator calc = new AdaptiveDelayCalculator();
        AdaptiveDelayHandlerTest.MockAdaptiveDelayHandler mock =
                new AdaptiveDelayHandlerTest.MockAdaptiveDelayHandler(calc, gs);

        Transaction tx = new Transaction("tx_3", "tok_3", "s", "r", System.currentTimeMillis(), "n", "s");

        final boolean[] completed = {false};
        mock.simulateComplete(tx, new AdaptiveDelayHandler.DelayListener() {
            @Override public void onDelayComplete(Transaction t) { completed[0] = true; }
            @Override public void onDelayAborted(Transaction t, GossipStore.ConflictResult c) {}
            @Override public void onDelayProgress(long r, long total, AdaptiveDelayCalculator.RiskLevel risk) {}
        });

        assertTrue(completed[0]);
        assertEquals(TransactionPhase.COMMITTING, tx.getPhase());
    }

    @Test
    public void test4_tokenExpiryWindow_exactlySixtySeconds() {
        WalletInterface.TokenInfo validToken = new WalletInterface.TokenInfo("tok_v", 10000L, System.currentTimeMillis() + 59000);
        assertTrue(validToken.isValid());

        WalletInterface.TokenInfo expiredToken = new WalletInterface.TokenInfo("tok_e", 10000L, System.currentTimeMillis() - 1);
        assertFalse(expiredToken.isValid());
    }

    @Test
    public void test5_futureTimestampOver5Seconds_rejected() {
        Transaction tx = new Transaction("tx_5", "tok_timeout", wallet.getPublicKey(), "receiver",
                                         System.currentTimeMillis() + 6_000, "nonce_5", "");
        signTx(tx);

        final String[] failure = {null};
        handler.execute(tx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction t) {}
            @Override public void onValidationFailed(Transaction t, ValidationResult r) {
                failure[0] = r.getFailureCode().name();
            }
        });
        ShadowLooper.idleMainLooper();

        assertEquals("TOKEN_EXPIRED", failure[0]);
    }
}
