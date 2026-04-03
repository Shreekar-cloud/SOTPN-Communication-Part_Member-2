package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
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
 * WALLET ATOMIC INTEGRITY TEST
 * Verifies that money is never lost or doubled if a failure occurs 
 * at the exact moment of the final commit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WalletAtomicIntegrityTest {

    private MockWallet receiverWallet;
    private CommitPhaseHandler handler;
    private BleManager bleMock;

    @Before
    public void setUp() {
        receiverWallet = new MockWallet();
        bleMock = mock(BleManager.class);
        handler = new CommitPhaseHandler(receiverWallet, bleMock, null);
    }

    // -----------------------------------------------------------------------
    // TEST: Failure during ACK delivery
    // If the wallet is credited but the network fails to deliver the ACK,
    // the system is in an inconsistent "Ghost Token" state.
    // -----------------------------------------------------------------------
    @Test
    public void testReceiver_FailureAfterWalletCredit_IdentifiesInconsistency() {
        Transaction tx = new Transaction("tx_race", "tok_1", "sender", receiverWallet.getPublicKey(),
                                         System.currentTimeMillis(), "nonce", "sig");

        // Simulate network failure during the SEND step, AFTER the Wallet logic
        doThrow(new RuntimeException("Radio Connection Lost")).when(bleMock).sendAck(any());

        handler.executeReceiverCommit(tx, true, new CommitPhaseHandler.CommitListener() {
            @Override public void onReceiverCommitComplete(TransactionProof proof) { fail("Should not complete"); }
            @Override public void onSenderCommitComplete(TransactionProof proof) {}
            @Override public void onCommitFailed(String txId, String reason) {
                // System identifies failure
            }
        });

        // The Receiver's wallet already called receiveToken()
        boolean receiverHasToken = receiverWallet.tokenWasReceived;
        
        // This assertion checks if we have created a "Ghost Token"
        // In a perfect system, if phase is FAILED, receiverHasToken should be false (or rollbacked)
        if (receiverHasToken && tx.getPhase() == TransactionPhase.FAILED) {
            System.err.println("⚠️ GRAVE SECURITY RISK: Receiver has the token, but ACK failed.");
            System.err.println("The Sender will now unlock the same token. Double-spend created.");
        }
        
        // We expect the system to be robust enough to handle this
        assertFalse("Receiver credited wallet but failed to notify sender - Double Spend Risk!", receiverHasToken);
    }
}
