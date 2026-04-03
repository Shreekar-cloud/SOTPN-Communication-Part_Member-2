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
 * MITM ACK THEFT TEST
 * Verifies that the Sender rejects ACKs from identities other than the intended receiver.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MitmAckTheftTest {

    private MockWallet senderWallet;
    private MockWallet intendedReceiverWallet;
    private MockWallet mitmWallet;
    private CommitPhaseHandler commitHandler;

    @Before
    public void setUp() {
        senderWallet = new MockWallet();
        intendedReceiverWallet = new MockWallet();
        mitmWallet = new MockWallet();
        commitHandler = new CommitPhaseHandler(senderWallet, null, null);
    }

    @Test
    public void testCommit_AckFromWrongIdentity_IsRejected() {
        // 1. Create transaction intended for Alice (intendedReceiver)
        Transaction tx = new Transaction("tx_1", "tok_1", senderWallet.getPublicKey(), intendedReceiverWallet.getPublicKey(), 
                                         System.currentTimeMillis(), "nonce_1", "sig");

        // 2. Eve (mitmWallet) intercepts and generates a valid signature for the ACK data
        String ackData = "Received:" + tx.getTxId() + ":" + tx.getTokenId();
        String eveSignature = mitmWallet.signTransaction(ackData);

        // 3. Eve sends back an ACK with HER public key
        TransactionAck maliciousAck = new TransactionAck(tx.getTxId(), tx.getTokenId(), 
                                                         mitmWallet.getPublicKey(), 
                                                         System.currentTimeMillis(), 
                                                         eveSignature, true);

        // 4. Sender attempts to commit
        commitHandler.executeSenderCommit(tx, maliciousAck, new CommitPhaseHandler.CommitListener() {
            @Override public void onSenderCommitComplete(TransactionProof p) {
                fail("Security Loophole: Sender accepted an ACK from a MITM attacker!");
            }
            @Override public void onReceiverCommitComplete(TransactionProof p) {}
            @Override public void onCommitFailed(String id, String r) {
                // Expected secure behavior
            }
        });

        // Current status check: Logic should prevent transition to FINALIZED
        assertNotEquals("Transaction must not succeed if ACK identity is hijacked", 
                        TransactionPhase.FINALIZED, tx.getPhase());
    }
}
