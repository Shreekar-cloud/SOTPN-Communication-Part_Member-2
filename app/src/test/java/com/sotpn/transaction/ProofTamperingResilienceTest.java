package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.model.TransactionPhase;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * PROOF TAMPERING RESILIENCE TEST
 * Verifies that the system rejects modified or corrupted commit proofs.
 */
public class ProofTamperingResilienceTest {

    private CommitPhaseHandler handler;
    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        handler = new CommitPhaseHandler(wallet, null, null);
    }

    @Test
    public void testCommit_ModifiedAckSignature_IsRejected() {
        Transaction tx = new Transaction("tx_1", "tok_1", wallet.getPublicKey(), "receiver", System.currentTimeMillis(), "nonce", "sig");
        
        // Valid ACK data
        String ackData = "Received:" + tx.getTxId() + ":" + tx.getTokenId();
        String validSig = wallet.signTransaction(ackData);
        
        // Malicious ACK with tampered signature
        TransactionAck maliciousAck = new TransactionAck(tx.getTxId(), tx.getTokenId(), 
                                                         wallet.getPublicKey(), System.currentTimeMillis(), 
                                                         validSig + "_TAMPERED", true);

        handler.executeSenderCommit(tx, maliciousAck, new CommitPhaseHandler.CommitListener() {
            @Override public void onSenderCommitComplete(TransactionProof p) {
                fail("Security Loophole: Accepted tampered ACK signature!");
            }
            @Override public void onReceiverCommitComplete(TransactionProof p) {}
            @Override public void onCommitFailed(String id, String r) {
                // Correct behavior
            }
        });

        assertNotEquals("Transaction must fail if proof is tampered", 
                        TransactionPhase.FINALIZED, tx.getPhase());
    }
}
