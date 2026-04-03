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
import org.robolectric.shadows.ShadowLooper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DataIntegrityTest {

    private MockWallet senderWallet;
    private MockWallet receiverWallet;

    @Before
    public void setUp() {
        senderWallet   = new MockWallet();
        receiverWallet = new MockWallet();
        senderWallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_integrity", 10_000L,
                System.currentTimeMillis() + 60_000);
    }

    // -----------------------------------------------------------------------
    // TEST 1: TxId never changes from Phase 1 to Phase 4
    // -----------------------------------------------------------------------
    @Test
    public void test1_txId_neverChangesPhase1ToPhase4() {
        PreparePhaseHandler handler =
                new PreparePhaseHandler(senderWallet, null, null);

        final Transaction[] sentTx = {null};
        handler.execute(receiverWallet.getPublicKey(), 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                    @Override public void onPrepareFailed(String r) {}
                });

        ShadowLooper.idleMainLooper();
        assertNotNull("Transaction must be sent", sentTx[0]);
        String originalTxId = sentTx[0].getTxId();

        // Simulate phase changes
        sentTx[0].setPhase(TransactionPhase.VALIDATING);
        sentTx[0].setPhase(TransactionPhase.DELAYING);
        sentTx[0].setPhase(TransactionPhase.COMMITTING);
        sentTx[0].setPhase(TransactionPhase.FINALIZED);

        assertEquals("TxId must not change through all phases",
                originalTxId, sentTx[0].getTxId());
    }

    // -----------------------------------------------------------------------
    // TEST 2: TokenId never changes throughout flow
    // -----------------------------------------------------------------------
    @Test
    public void test2_tokenId_neverChanges() {
        PreparePhaseHandler handler =
                new PreparePhaseHandler(senderWallet, null, null);

        final Transaction[] sentTx = {null};
        handler.execute(receiverWallet.getPublicKey(), 10_000L, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) { sentTx[0] = tx; }
                    @Override public void onPrepareFailed(String r) {}
                });

        ShadowLooper.idleMainLooper();
        assertNotNull(sentTx[0]);
        String originalTokenId = sentTx[0].getTokenId();
        assertEquals("TokenId must be tok_integrity",
                "tok_integrity", originalTokenId);

        // Verify tokenId is same in proof
        CommitPhaseHandler commit =
                new CommitPhaseHandler(senderWallet, null, null);
        
        // Generate valid signature for ACK
        String ackData = "Received:" + sentTx[0].getTxId() + ":" + sentTx[0].getTokenId();
        String ackSig = receiverWallet.signTransaction(ackData);

        TransactionAck ack = new TransactionAck(
                sentTx[0].getTxId(), sentTx[0].getTokenId(),
                receiverWallet.getPublicKey(),
                System.currentTimeMillis(), ackSig, true);

        final TransactionProof[] proof = {null};
        commit.executeSenderCommit(sentTx[0], ack,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) { proof[0] = p; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {}
                });

        assertNotNull(proof[0]);
        assertEquals("TokenId must be same in proof",
                originalTokenId, proof[0].getTokenId());
    }

    // -----------------------------------------------------------------------
    // TEST 3: Signature never modified in transit
    // -----------------------------------------------------------------------
    @Test
    public void test3_signature_neverModified() {
        String data = "tok_integrity" + receiverWallet.getPublicKey()
                + System.currentTimeMillis() + "nonce_integrity";
        String signature = senderWallet.signTransaction(data);

        // Verify same data → same signature every time
        String sig2 = senderWallet.signTransaction(data);
        assertEquals("Signature must be deterministic", signature, sig2);

        // Verify different data → different signature
        String sig3 = senderWallet.signTransaction(data + "tampered");
        assertNotEquals("Tampered data must give different signature",
                signature, sig3);
    }

    // -----------------------------------------------------------------------
    // TEST 4: Nonce never reused across 1000 transactions
    // -----------------------------------------------------------------------
    @Test
    public void test4_nonces_neverReusedAcross1000Transactions() {
        Set<String> nonces = new HashSet<>();
        int count = 1_000;
        int duplicates = 0;

        for (int i = 0; i < count; i++) {
            String nonce = senderWallet.generateNonce();
            if (!nonces.add(nonce)) duplicates++;
        }

        assertEquals("Zero duplicate nonces in 1000 generations",
                0, duplicates);
        assertEquals("All 1000 nonces must be unique", count, nonces.size());
    }

    // -----------------------------------------------------------------------
    // TEST 5: Proof contains exact same data as original transaction
    // -----------------------------------------------------------------------
    @Test
    public void test5_proof_containsExactTransactionData() {
        String txId = "tx_data_001";
        String tokenId = "tok_data_001";
        String senderKey = senderWallet.getPublicKey();
        String receiverKey = receiverWallet.getPublicKey();
        long ts = 1712345678900L;
        String nonce = "nonce_data_001";
        
        // Generate valid signature for TX
        String txData = tokenId + receiverKey + ts + nonce;
        String txSig = senderWallet.signTransaction(txData);

        Transaction tx = new Transaction(txId, tokenId, senderKey, receiverKey, ts, nonce, txSig);

        CommitPhaseHandler commit =
                new CommitPhaseHandler(senderWallet, null, null);
        
        // Generate valid signature for ACK
        String ackDataStr = "Received:" + txId + ":" + tokenId;
        String ackSig = receiverWallet.signTransaction(ackDataStr);

        TransactionAck ack = new TransactionAck(
                tx.getTxId(), tx.getTokenId(),
                receiverKey,
                System.currentTimeMillis(), ackSig, true);

        final TransactionProof[] proof = {null};
        commit.executeSenderCommit(tx, ack,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof p) { proof[0] = p; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String id, String r) {
                        System.out.println("Commit failed in test5: " + r);
                    }
                });

        assertNotNull("Proof must exist", proof[0]);
        assertEquals("Proof txId must match",    tx.getTxId(),    proof[0].getTxId());
        assertEquals("Proof tokenId must match", tx.getTokenId(), proof[0].getTokenId());
        assertEquals("Proof sender must match",  tx.getSenderPublicKey(),
                proof[0].getSenderPublicKey());
        assertEquals("Proof receiver must match", tx.getReceiverPublicKey(),
                proof[0].getReceiverPublicKey());
        assertEquals("Proof timestamp must match", tx.getTimestamp(),
                proof[0].getTimestamp());
        assertEquals("Proof nonce must match",   tx.getNonce(),    proof[0].getNonce());
    }
}
