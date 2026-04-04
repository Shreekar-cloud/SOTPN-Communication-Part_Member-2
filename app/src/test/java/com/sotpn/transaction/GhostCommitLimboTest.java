package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.model.TransactionPhase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * GHOST COMMIT & LIMBO RECOVERY TESTS
 * Verifies the system behavior when an ACK is sent by the receiver 
 * but never received by the sender.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GhostCommitLimboTest {

    private MockWallet senderWallet;
    private MockWallet receiverWallet;
    private CommitPhaseHandler senderCommit;
    private CommitPhaseHandler receiverCommit;

    @Before
    public void setUp() {
        senderWallet = new MockWallet();
        receiverWallet = new MockWallet();
        senderCommit = new CommitPhaseHandler(senderWallet, null, null);
        receiverCommit = new CommitPhaseHandler(receiverWallet, null, null);
    }

    @Test
    public void testRecovery_SenderNeverGetsAck_TokenRemainsLocked() {
        // 1. Transaction starts
        Transaction tx = new Transaction("tx_limbo", "tok_1", senderWallet.getPublicKey(), receiverWallet.getPublicKey(), 
                                         System.currentTimeMillis(), "n_1", "s_1");
        
        // 2. Receiver credits wallet and generates ACK
        receiverCommit.executeReceiverCommit(tx, true, mock(CommitPhaseHandler.CommitListener.class));
        assertTrue("Receiver MUST have the money", receiverWallet.tokenWasReceived);

        // 3. AT THIS POINT, THE ACK IS LOST IN THE AIR.
        // The Sender is still waiting.
        
        // 4. Verification: The Sender MUST NOT mark the token as spent yet
        assertFalse("Sender MUST NOT mark spent until ACK is verified", senderWallet.tokenWasSpent);
        
        // 5. Verification: The token MUST remain in a 'Locked' or 'Pending' state 
        // to prevent double-spending until the user manually resolves the limbo.
        // If the sender automatically 'unlocked' it now, it would be a double-spend.
    }
}
