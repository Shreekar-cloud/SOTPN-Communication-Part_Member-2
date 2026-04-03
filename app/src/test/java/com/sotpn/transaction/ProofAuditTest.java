package com.sotpn.transaction;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

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
public class ProofAuditTest {

    private MockWallet         senderWallet;
    private MockWallet         receiverWallet;
    private CommitPhaseHandler senderCommit;
    private CommitPhaseHandler receiverCommit;

    private TransactionProof lastSenderProof   = null;
    private TransactionProof lastReceiverProof = null;
    private String           lastFailure       = null;

    @Before
    public void setUp() {
        senderWallet   = new MockWallet();
        receiverWallet = new MockWallet();
        senderCommit   = new CommitPhaseHandler(senderWallet, null, null);
        receiverCommit = new CommitPhaseHandler(receiverWallet, null, null);
        reset();
    }

    private void reset() {
        lastSenderProof   = null;
        lastReceiverProof = null;
        lastFailure       = null;
    }

    private Transaction buildTransaction() {
        String tokenId = "tok_proof_001";
        String receiverKey = receiverWallet.getPublicKey();
        long ts = System.currentTimeMillis();
        String nonce = "nonce_proof_001";
        
        // Correct signature for the transaction
        String data = tokenId + receiverKey + ts + nonce;
        String sig = senderWallet.signTransaction(data);

        return new Transaction("tx_proof_001", tokenId, senderWallet.getPublicKey(), 
                               receiverKey, ts, nonce, sig);
    }

    private TransactionAck buildAck(Transaction tx) {
        // Correct signature for the ACK (as done in CommitPhaseHandler)
        String ackData = "Received:" + tx.getTxId() + ":" + tx.getTokenId();
        String sig = receiverWallet.signTransaction(ackData);

        return new TransactionAck(
                tx.getTxId(), tx.getTokenId(),
                receiverWallet.getPublicKey(),
                System.currentTimeMillis(), sig, true);
    }

    @Test
    public void test1_senderProof_containsCorrectTxId() {
        Transaction tx = buildTransaction();
        senderCommit.executeSenderCommit(tx, buildAck(tx),
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) { lastSenderProof = p; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) { lastFailure = r; }
                });
        assertNotNull("Sender proof must exist. Failure: " + lastFailure, lastSenderProof);
        assertEquals("tx_proof_001", lastSenderProof.getTxId());
    }

    @Test
    public void test2_receiverProof_containsCorrectTxId() {
        Transaction tx = buildTransaction();
        receiverCommit.executeReceiverCommit(tx, true,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onReceiverCommitComplete(TransactionProof p) { lastReceiverProof = p; }
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) { lastFailure = r; }
                });
        assertNotNull("Receiver proof must exist", lastReceiverProof);
        assertEquals("tx_proof_001", lastReceiverProof.getTxId());
    }

    @Test
    public void test3_bothProofs_sameTxIdAndTokenId() {
        Transaction tx  = buildTransaction();
        TransactionAck ack = buildAck(tx);

        senderCommit.executeSenderCommit(tx, ack,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) { lastSenderProof = p; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        receiverCommit.executeReceiverCommit(tx, true,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onReceiverCommitComplete(TransactionProof p) { lastReceiverProof = p; }
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        assertNotNull(lastSenderProof);
        assertNotNull(lastReceiverProof);
        assertEquals(lastSenderProof.getTxId(), lastReceiverProof.getTxId());
        assertEquals(lastSenderProof.getTokenId(), lastReceiverProof.getTokenId());
    }

    @Test
    public void test4_proof_serializesToJsonCorrectly() {
        TransactionProof proof = new TransactionProof(
                "tx_001", "tok_001", "sender", "receiver",
                System.currentTimeMillis(), "nonce",
                "tx_sig", "ack_sig", System.currentTimeMillis(),
                TransactionProof.Role.SENDER);

        try {
            String json = proof.toJson().toString();
            assertTrue(json.contains("tx_id"));
            assertTrue(json.contains("token_id"));
            assertTrue(json.contains("role"));
        } catch (Exception e) {
            fail("toJson() failed: " + e.getMessage());
        }
    }

    @Test
    public void test5_proof_deserializesFromJsonCorrectly() {
        TransactionProof original = new TransactionProof(
                "tx_deser_001", "tok_deser_001",
                "sender_key", "receiver_key",
                1712345678900L, "nonce_deser",
                "tx_sig_deser", "ack_sig_deser",
                1712345679000L, TransactionProof.Role.RECEIVER);

        try {
            org.json.JSONObject json = original.toJson();
            TransactionProof parsed = TransactionProof.fromJson(json);
            assertEquals(original.getTxId(), parsed.getTxId());
            assertEquals(original.getRole(), parsed.getRole());
        } catch (Exception e) {
            fail("fromJson() failed: " + e.getMessage());
        }
    }

    @Test
    public void test6_proofRoles_senderNotEqualReceiver() {
        assertNotEquals(TransactionProof.Role.SENDER, TransactionProof.Role.RECEIVER);
    }

    @Test
    public void test7_proof_committedAtIsAfterTransactionTimestamp() {
        long txTimestamp = System.currentTimeMillis() - 1_000;
        Transaction tx = new Transaction("tx_time", "tok_time", senderWallet.getPublicKey(), 
                                         receiverWallet.getPublicKey(), txTimestamp, "n", "");
        signTx(tx, senderWallet);

        senderCommit.executeSenderCommit(tx, buildAck(tx),
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) { lastSenderProof = p; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        assertNotNull(lastSenderProof);
        assertTrue(lastSenderProof.getCommittedAtMs() >= txTimestamp);
    }

    @Test
    public void test8_proof_containsBothSignatures() {
        Transaction tx = buildTransaction();
        senderCommit.executeSenderCommit(tx, buildAck(tx),
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) { lastSenderProof = p; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        assertNotNull(lastSenderProof);
        assertNotNull(lastSenderProof.getTxSignature());
        assertNotNull(lastSenderProof.getAckSignature());
    }

    private void signTx(Transaction tx, MockWallet wallet) {
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(wallet.signTransaction(data));
    }
}
