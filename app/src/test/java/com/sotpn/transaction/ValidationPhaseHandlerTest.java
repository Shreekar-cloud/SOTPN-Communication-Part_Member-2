package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ValidationPhaseHandlerTest {

    private MockWallet             wallet;
    private NonceStore             nonceStore;
    private GossipStore            gossipStore;
    private ValidationPhaseHandler handler;

    private Transaction      lastValidTx  = null;
    private Transaction      lastFailedTx = null;
    private ValidationResult lastResult   = null;

    @Before
    public void setUp() {
        wallet      = new MockWallet();
        nonceStore  = new NonceStore();
        gossipStore = new GossipStore();

        GossipEngine gossipEngine = new GossipEngine(
                mock(BleManager.class), mock(WifiDirectManager.class), gossipStore,
                mock(GossipEngine.GossipListener.class), "test_device");

        handler = new ValidationPhaseHandler(wallet, nonceStore, gossipEngine);
        resetResults();
    }

    private void resetResults() {
        lastValidTx  = null;
        lastFailedTx = null;
        lastResult   = null;
    }

    private ValidationPhaseHandler.ValidationListener makeListener() {
        return new ValidationPhaseHandler.ValidationListener() {
            @Override
            public void onValidationPassed(Transaction tx) { lastValidTx = tx; }
            @Override
            public void onValidationFailed(Transaction tx, ValidationResult r) {
                lastFailedTx = tx;
                lastResult   = r;
            }
        };
    }

    private Transaction buildValidTransaction() {
        String txId = "tx_001";
        String tokenId = "tok_abc";
        String sender = wallet.getPublicKey();
        String receiver = "receiver_pubkey";
        long ts = System.currentTimeMillis();
        String nonce = "nonce_" + System.nanoTime();
        
        // Generate valid signature matching MockWallet format
        String data = tokenId + receiver + ts + nonce;
        String sig = wallet.signTransaction(data);

        return new Transaction(txId, tokenId, sender, receiver, ts, nonce, sig);
    }

    @Test
    public void testValidTransaction_passesAllChecks() {
        handler.execute(buildValidTransaction(), makeListener());
        assertNotNull("Valid transaction should pass", lastValidTx);
        assertNull("No failure expected", lastResult);
    }

    @Test
    public void testMissingTxId_failsFieldCheck() {
        Transaction tx = buildValidTransaction();
        tx.setTxId(null);
        handler.execute(tx, makeListener());
        assertNotNull("Missing txId should fail", lastResult);
        assertEquals(ValidationResult.FailureCode.MISSING_FIELDS, lastResult.getFailureCode());
    }

    @Test
    public void testMissingSignature_failsFieldCheck() {
        Transaction tx = buildValidTransaction();
        tx.setSignature(null);
        handler.execute(tx, makeListener());
        assertNotNull("Missing signature should fail", lastResult);
        assertEquals(ValidationResult.FailureCode.MISSING_FIELDS, lastResult.getFailureCode());
    }

    @Test
    public void testInvalidSignature_failsSignatureCheck() {
        Transaction tx = buildValidTransaction();
        tx.setSignature("sig:wrong:format");
        handler.execute(tx, makeListener());
        assertNotNull("Invalid signature should fail", lastResult);
        assertEquals(ValidationResult.FailureCode.INVALID_SIGNATURE, lastResult.getFailureCode());
    }

    @Test
    public void testExpiredTransaction_failsExpiryCheck() {
        String tokenId = "tok_expired";
        String receiver = "receiver";
        long ts = System.currentTimeMillis() - 61_000;
        String nonce = "nonce_expired";
        String sig = wallet.signTransaction(tokenId + receiver + ts + nonce);

        Transaction tx = new Transaction("tx_001", tokenId, wallet.getPublicKey(), receiver, ts, nonce, sig);
        handler.execute(tx, makeListener());
        assertNotNull("Expired transaction should fail", lastResult);
        assertEquals(ValidationResult.FailureCode.TOKEN_EXPIRED, lastResult.getFailureCode());
    }

    @Test
    public void testFutureTimestamp_failsExpiryCheck() {
        String tokenId = "tok_future";
        String receiver = "receiver";
        long ts = System.currentTimeMillis() + 10_000;
        String nonce = "nonce_future";
        String sig = wallet.signTransaction(tokenId + receiver + ts + nonce);

        Transaction tx = new Transaction("tx_001", tokenId, wallet.getPublicKey(), receiver, ts, nonce, sig);
        handler.execute(tx, makeListener());
        assertNotNull("Future timestamp should fail", lastResult);
        assertEquals(ValidationResult.FailureCode.TOKEN_EXPIRED, lastResult.getFailureCode());
    }

    @Test
    public void testReplayAttack_sameNonceRejected() {
        Transaction tx1 = buildValidTransaction();
        handler.execute(tx1, makeListener());
        assertNotNull("First tx should pass", lastValidTx);
        
        resetResults();
        
        // Same nonce, different transaction
        Transaction tx2 = buildValidTransaction();
        tx2.setNonce(tx1.getNonce());
        // Resign with the same nonce
        String data = tx2.getTokenId() + tx2.getReceiverPublicKey() + tx2.getTimestamp() + tx2.getNonce();
        tx2.setSignature(wallet.signTransaction(data));

        handler.execute(tx2, makeListener());
        assertNotNull("Replay should fail", lastResult);
        assertEquals(ValidationResult.FailureCode.NONCE_REPLAY, lastResult.getFailureCode());
    }

    @Test
    public void testDifferentNonces_bothAccepted() {
        handler.execute(buildValidTransaction(), makeListener());
        assertNotNull("First tx should pass", lastValidTx);
        resetResults();
        handler.execute(buildValidTransaction(), makeListener());
        assertNotNull("Second tx should pass", lastValidTx);
    }

    @Test
    public void testGossipConflict_failsValidation() {
        // Updated to use 6-argument constructor
        gossipStore.addGossip(new GossipMessage("tok_conflict", "device_A", "tx_existing", System.currentTimeMillis(), "sig_existing", 0));
        gossipStore.addGossip(new GossipMessage("tok_conflict", "device_B", "tx_different", System.currentTimeMillis(), "sig_different", 0));
        
        Transaction tx = buildValidTransaction();
        tx.setTokenId("tok_conflict");
        // Resign with the conflict tokenId
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(wallet.signTransaction(data));

        handler.execute(tx, makeListener());
        assertNotNull("Gossip conflict should fail", lastResult);
        assertEquals(ValidationResult.FailureCode.GOSSIP_CONFLICT, lastResult.getFailureCode());
    }

    @Test
    public void testPhase_changedToDelayingAfterPass() {
        Transaction tx = buildValidTransaction();
        handler.execute(tx, makeListener());
        assertNotNull("Transaction should pass", lastValidTx);
        assertEquals("Phase should be DELAYING", TransactionPhase.DELAYING, tx.getPhase());
    }

    @Test
    public void testEmptyNonce_failsFieldCheck() {
        Transaction tx = buildValidTransaction();
        tx.setNonce("");
        handler.execute(tx, makeListener());
        assertEquals(ValidationResult.FailureCode.MISSING_FIELDS, lastResult.getFailureCode());
    }
}
