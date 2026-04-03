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

    private MockWallet          myWallet;
    private MockWallet          otherWallet;
    private CommitPhaseHandler  handler;

    private TransactionProof completedProof   = null;
    private String           failureReason    = null;
    private boolean          receiverComplete = false;
    private boolean          senderComplete   = false;

    @Before
    public void setUp() {
        myWallet  = new MockWallet();
        otherWallet = new MockWallet();
        handler = new CommitPhaseHandler(myWallet, null, null);
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

    private Transaction buildTransaction(String senderKey, String receiverKey) {
        Transaction tx = new Transaction(
                "tx_001", "tok_abc",
                senderKey, receiverKey,
                System.currentTimeMillis(),
                "nonce_001", ""
        );
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        // Assume senderWallet signed it
        tx.setSignature("sig:" + senderKey + ":" + data.hashCode());
        return tx;
    }

    private TransactionAck buildValidAck(String txId, String tokenId, String receiverKey) {
        String ackData = "Received:" + txId + ":" + tokenId;
        // Signature format sig:[receiverKey]:[hash]
        String sig = "sig:" + receiverKey + ":" + ackData.hashCode();
        
        return new TransactionAck(
                txId, tokenId,
                receiverKey,
                System.currentTimeMillis(),
                sig,
                true 
        );
    }

    @Test
    public void testReceiverCommit_receiveTokenCalled() {
        // We are the receiver
        Transaction tx = buildTransaction(otherWallet.getPublicKey(), myWallet.getPublicKey());
        handler.executeReceiverCommit(tx, true, makeListener());
        assertTrue("receiveToken should be called", myWallet.tokenWasReceived);
        assertEquals(TransactionPhase.FINALIZED, tx.getPhase());
    }

    @Test
    public void testSenderCommit_validAck_marksTokenSpent() {
        // We are the sender
        Transaction tx  = buildTransaction(myWallet.getPublicKey(), otherWallet.getPublicKey());
        TransactionAck ack = buildValidAck("tx_001", "tok_abc", otherWallet.getPublicKey());
        
        handler.executeSenderCommit(tx, ack, makeListener());
        assertTrue("Token should be marked SPENT", myWallet.tokenWasSpent);
        assertTrue(senderComplete);
    }

    @Test
    public void testSenderCommit_ackTxIdMismatch_fails() {
        Transaction tx = buildTransaction(myWallet.getPublicKey(), otherWallet.getPublicKey());
        TransactionAck wrongAck = new TransactionAck(
                "tx_WRONG", "tok_abc", otherWallet.getPublicKey(),
                System.currentTimeMillis(), "sig", true);
        
        handler.executeSenderCommit(tx, wrongAck, makeListener());
        assertNotNull(failureReason);
        assertFalse(senderComplete);
    }

    @Test
    public void testSenderCommit_ackTokenIdMismatch_fails() {
        Transaction tx = buildTransaction(myWallet.getPublicKey(), otherWallet.getPublicKey());
        TransactionAck wrongAck = new TransactionAck(
                "tx_001", "tok_WRONG", otherWallet.getPublicKey(),
                System.currentTimeMillis(), "sig", true);
        
        handler.executeSenderCommit(tx, wrongAck, makeListener());
        assertNotNull(failureReason);
        assertEquals(TransactionPhase.FAILED, tx.getPhase());
    }

    @Test
    public void testSenderCommit_receiverIdentityMismatch_fails() {
        Transaction tx = buildTransaction(myWallet.getPublicKey(), otherWallet.getPublicKey());
        // Ack claims to be from a third party
        TransactionAck hijackedAck = buildValidAck("tx_001", "tok_abc", "THIRD_PARTY_KEY");
        
        handler.executeSenderCommit(tx, hijackedAck, makeListener());
        assertNotNull(failureReason);
        assertEquals(TransactionPhase.FAILED, tx.getPhase());
    }

    @Test
    public void testReceiverCommit_producesProofWithReceiverRole() {
        Transaction tx = buildTransaction(otherWallet.getPublicKey(), myWallet.getPublicKey());
        handler.executeReceiverCommit(tx, true, makeListener());
        assertNotNull(completedProof);
        assertEquals(TransactionProof.Role.RECEIVER, completedProof.getRole());
    }
}
