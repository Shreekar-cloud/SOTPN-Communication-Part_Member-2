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

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommitPhaseHandlerTest {

    private MockWallet          wallet;
    private CommitPhaseHandler  handler;

    // Result holders
    private TransactionProof completedProof   = null;
    private String           failureReason    = null;
    private boolean          receiverComplete = false;
    private boolean          senderComplete   = false;

    @Before
    public void setUp() {
        wallet  = new MockWallet();
        handler = new CommitPhaseHandler(wallet, null, null);
        resetResults();
    }

    private void resetResults() {
        completedProof   = null;
        failureReason    = null;
        receiverComplete = false;
        senderComplete   = false;
    }

    private CommitPhaseHandler.CommitListener makeListener() {
        return new CommitPhaseHandler.CommitListener() {
            @Override
            public void onReceiverCommitComplete(TransactionProof proof) {
                completedProof   = proof;
                receiverComplete = true;
            }
            @Override
            public void onSenderCommitComplete(TransactionProof proof) {
                completedProof = proof;
                senderComplete = true;
            }
            @Override
            public void onCommitFailed(String txId, String reason) {
                failureReason = reason;
            }
        };
    }

    private Transaction buildTransaction() {
        Transaction tx = new Transaction(
                "tx_001", "tok_abc",
                "sender_pubkey", "receiver_pubkey",
                System.currentTimeMillis(),
                "nonce_001", "tx_signature"
        );
        // Manual override signature to pass MockWallet check if needed
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(wallet.signTransaction(data));
        return tx;
    }

    private TransactionAck buildValidAck(String txId, String tokenId) {
        String ackData = "Received:" + txId + ":" + tokenId;
        String receiverPubKey = wallet.getPublicKey();
        String sig = wallet.signTransaction(ackData);
        
        return new TransactionAck(
                txId, tokenId,
                receiverPubKey,
                System.currentTimeMillis(),
                sig,
                true // accepted
        );
    }

    @Test
    public void testReceiverCommit_signIsCalled() {
        Transaction tx = buildTransaction();
        handler.executeReceiverCommit(tx, true, makeListener());
        assertTrue("signTransaction should be called for ACK",
                wallet.signCallCount.get() > 0);
    }

    @Test
    public void testReceiverCommit_receiveTokenCalled() {
        Transaction tx = buildTransaction();
        handler.executeReceiverCommit(tx, true, makeListener());
        assertTrue("receiveToken should be called",
                wallet.tokenWasReceived);
    }

    @Test
    public void testReceiverCommit_phaseIsFinalized() {
        Transaction tx = buildTransaction();
        handler.executeReceiverCommit(tx, true, makeListener());
        assertEquals("Phase should be FINALIZED",
                TransactionPhase.FINALIZED, tx.getPhase());
    }

    @Test
    public void testSenderCommit_ackTxIdMismatch_fails() {
        Transaction tx = buildTransaction();
        TransactionAck wrongAck = new TransactionAck(
                "tx_WRONG", "tok_abc", "receiver_pubkey",
                System.currentTimeMillis(), "sig", true);
        handler.executeSenderCommit(tx, wrongAck, makeListener());
        assertNotNull("TxId mismatch should fail", failureReason);
        assertFalse("Sender complete should NOT fire", senderComplete);
    }

    @Test
    public void testSenderCommit_invalidAckSignature_fails() {
        Transaction tx  = buildTransaction();
        TransactionAck ack = buildValidAck("tx_001", "tok_abc");
        
        TransactionAck badSigAck = new TransactionAck(
                ack.getTxId(), ack.getTokenId(), ack.getReceiverPublicKey(),
                ack.getAckTimestamp(), "BAD_SIG", ack.isAccepted());
                
        handler.executeSenderCommit(tx, badSigAck, makeListener());
        assertNotNull("Invalid ACK sig should fail", failureReason);
        assertFalse("Token should NOT be marked spent", wallet.tokenWasSpent);
    }

    @Test
    public void testSenderCommit_validAck_marksTokenSpent() {
        Transaction tx  = buildTransaction();
        TransactionAck ack = buildValidAck("tx_001", "tok_abc");
        handler.executeSenderCommit(tx, ack, makeListener());
        assertTrue("Token should be marked SPENT", wallet.tokenWasSpent);
    }

    @Test
    public void testSenderCommit_producesProofWithSenderRole() {
        Transaction tx  = buildTransaction();
        TransactionAck ack = buildValidAck("tx_001", "tok_abc");
        handler.executeSenderCommit(tx, ack, makeListener());
        assertNotNull("Proof should be generated", completedProof);
        assertEquals("Proof role should be SENDER",
                TransactionProof.Role.SENDER, completedProof.getRole());
    }

    @Test
    public void testReceiverCommit_producesProofWithReceiverRole() {
        Transaction tx = buildTransaction();
        handler.executeReceiverCommit(tx, true, makeListener());
        assertNotNull("Proof should be generated", completedProof);
        assertEquals("Proof role should be RECEIVER",
                TransactionProof.Role.RECEIVER, completedProof.getRole());
    }
}
