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
 * ATOMIC COMMIT CONSISTENCY TEST
 * Verifies that the Receiver side does not end up in an inconsistent state
 * if the network fails during the final commit step.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AtomicCommitConsistencyTest {

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
    // TEST: Failure between Wallet Credit and ACK Transmission
    // -----------------------------------------------------------------------
    @Test
    public void testReceiver_CommitFailure_DoesNotCreateGhostToken() {
        Transaction tx = new Transaction("tx_race", "tok_1", "sender", receiverWallet.getPublicKey(),
                                         System.currentTimeMillis(), "nonce", "sig");

        // Simulate a network failure EXACTLY during the sendAck call
        doThrow(new RuntimeException("Radio Signal Lost")).when(bleMock).sendAck(any());

        handler.executeReceiverCommit(tx, true, new CommitPhaseHandler.CommitListener() {
            @Override public void onReceiverCommitComplete(TransactionProof proof) {
                fail("Should not complete if ACK failed");
            }
            @Override public void onSenderCommitComplete(TransactionProof proof) {}
            @Override public void onCommitFailed(String txId, String reason) {
                // This is where we are now
            }
        });

        // CRITICAL CHECK:
        // If the ACK failed, the Receiver MUST NOT keep the token in their wallet,
        // because the Sender will assume the transaction failed and unlock it.
        boolean walletHasToken = receiverWallet.tokenWasReceived;
        
        if (walletHasToken && tx.getPhase() == TransactionPhase.FAILED) {
            System.err.println("CRITICAL INCONSISTENCY DETECTED: Receiver has token, but transaction is FAILED.");
            // To be secure, the system must either:
            // 1. Rollback the wallet.receiveToken()
            // 2. OR reliably retry the ACK until the sender gets it.
        }
        
        // Asserting the current state to highlight the gap
        assertFalse("Security Risk: Receiver credited wallet even though ACK failed to send", walletHasToken);
    }
}
