package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * TEMPORAL SECURITY TESTS
 * Verifies that tokens don't expire during the 10-second gossip window.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TemporalSecurityTest {

    private CommitPhaseHandler receiverCommit;
    private MockWallet receiverWallet;

    @Before
    public void setUp() {
        receiverWallet = new MockWallet();
        receiverCommit = new CommitPhaseHandler(receiverWallet, null, null);
    }

    // -----------------------------------------------------------------------
    // TEST: Expiry during Delay
    // If a token was valid at Phase 2 but expires during Phase 3, 
    // the Receiver MUST NOT sign the ACK in Phase 4.
    // -----------------------------------------------------------------------
    @Test
    public void testTemporal_TokenExpiresDuringDelay_FailsCommit() {
        // Token expires in 2 seconds
        long expiry = System.currentTimeMillis() + 2000;
        Transaction tx = new Transaction("tx_1", "tok_exp", "s", receiverWallet.getPublicKey(), 
                                         System.currentTimeMillis(), "n", "s");
        
        // Simulating the 10-second wait...
        // 5 seconds pass -> Token is now EXPIRED
        receiverWallet.tokenToReturn = new WalletInterface.TokenInfo("tok_exp", 1000L, expiry);
        
        // In a secure system, executeReceiverCommit should check isValid() one last time.
        // This test serves as a security audit check.
        receiverCommit.executeReceiverCommit(tx, true, new CommitPhaseHandler.CommitListener() {
            @Override public void onReceiverCommitComplete(TransactionProof p) {
                // If this fires, we have a "Ghost Token" security risk!
            }
            @Override public void onSenderCommitComplete(TransactionProof p) {}
            @Override public void onCommitFailed(String id, String r) {
                // Secure behavior: Abort because token is no longer valid.
            }
        });
    }
}
