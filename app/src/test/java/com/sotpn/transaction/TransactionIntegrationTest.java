package com.sotpn.transaction;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.communication.WifiDirectManager;
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Comprehensive Integration Test for the Full Transaction Lifecycle.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TransactionIntegrationTest {

    // Sender side
    private MockWallet           senderWallet;
    private PreparePhaseHandler  prepareHandler;

    // Receiver side
    private MockWallet             receiverWallet;
    private NonceStore             nonceStore;
    private GossipStore            gossipStore;
    private ValidationPhaseHandler validationHandler;
    private CommitPhaseHandler     receiverCommitHandler;
    private CommitPhaseHandler     senderCommitHandler;

    // Shared state across phases for verification
    private Transaction      sentTransaction = null;
    private Transaction      validatedTx     = null;
    private TransactionAck   generatedAck    = null;
    private TransactionProof senderProof     = null;
    private TransactionProof receiverProof   = null;
    private String           failureReason   = null;

    @Before
    public void setUp() {
        senderWallet = new MockWallet();
        senderWallet.tokenToReturn = new WalletInterface.TokenInfo(
                "tok_integration_001", 10_000L,
                System.currentTimeMillis() + 60_000);
        
        senderWallet.verifyShouldSucceed = true;
        senderWallet.signShouldSucceed   = true;

        receiverWallet = new MockWallet();
        receiverWallet.verifyShouldSucceed = true;
        receiverWallet.signShouldSucceed   = true;

        nonceStore  = new NonceStore();
        gossipStore = new GossipStore();

        GossipEngine gossipEngine = new GossipEngine(
                mock(BleManager.class), 
                mock(WifiDirectManager.class), 
                gossipStore,
                new GossipEngine.GossipListener() {
                    @Override public void onConflictDetected(GossipStore.ConflictResult r) {}
                    @Override public void onGossipReceived(GossipMessage m) {}
                }, 
                receiverWallet.getPublicKey()
        );

        prepareHandler        = new PreparePhaseHandler(senderWallet, mock(BleManager.class), mock(WifiDirectManager.class));
        validationHandler     = new ValidationPhaseHandler(receiverWallet, nonceStore, gossipEngine);
        receiverCommitHandler = new CommitPhaseHandler(receiverWallet, mock(BleManager.class), mock(WifiDirectManager.class));
        senderCommitHandler   = new CommitPhaseHandler(senderWallet, mock(BleManager.class), mock(WifiDirectManager.class));

        resetResults();
    }

    private void resetResults() {
        sentTransaction = null;
        validatedTx     = null;
        generatedAck    = null;
        senderProof     = null;
        receiverProof   = null;
        failureReason   = null;
    }

    private boolean runFullFlow(long amountPaise) {
        // PHASE 1: Sender prepares
        prepareHandler.execute(receiverWallet.getPublicKey(), amountPaise, true,
                new PreparePhaseHandler.PrepareListener() {
                    @Override public void onPrepareSent(Transaction tx) { sentTransaction = tx; }
                    @Override public void onPrepareFailed(String reason) { failureReason = "Phase1: " + reason; }
                });
        ShadowLooper.idleMainLooper();
        if (sentTransaction == null) return false;

        // PHASE 2: Receiver validates
        validationHandler.execute(sentTransaction,
                new ValidationPhaseHandler.ValidationListener() {
                    @Override public void onValidationPassed(Transaction tx) { validatedTx = tx; }
                    @Override public void onValidationFailed(Transaction tx, ValidationResult r) { 
                        failureReason = "Phase2: " + r.getFailureMessage(); 
                    }
                });
        ShadowLooper.idleMainLooper();
        if (validatedTx == null) return false;

        // PHASE 3: Adaptive Delay (Skipped in this unit test to reach Phase 4)

        // PHASE 4 Receiver: Generate ACK and store token
        receiverCommitHandler.executeReceiverCommit(validatedTx, true,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onReceiverCommitComplete(TransactionProof proof) {
                        receiverProof = proof;
                        String ackData = "Received:" + validatedTx.getTxId() + ":" + validatedTx.getTokenId();
                        String ackSig = receiverWallet.signTransaction(ackData);
                        generatedAck = new TransactionAck(
                                validatedTx.getTxId(),
                                validatedTx.getTokenId(),
                                receiverWallet.getPublicKey(),
                                System.currentTimeMillis(),
                                ackSig,
                                true
                        );
                    }
                    @Override public void onSenderCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String txId, String reason) { failureReason = "Phase4R: " + reason; }
                });
        ShadowLooper.idleMainLooper();
        if (generatedAck == null) return false;

        // PHASE 4 Sender: Verify ACK and mark token SPENT
        senderCommitHandler.executeSenderCommit(sentTransaction, generatedAck,
                new CommitPhaseHandler.CommitListener() {
                    @Override public void onSenderCommitComplete(TransactionProof proof) { senderProof = proof; }
                    @Override public void onReceiverCommitComplete(TransactionProof p) {}
                    @Override public void onCommitFailed(String txId, String reason) { failureReason = "Phase4S: " + reason; }
                });
        ShadowLooper.idleMainLooper();

        return senderProof != null;
    }

    // 1. Full 4-phase flow completes successfully
    @Test
    public void test1_FullFlowCompletes() {
        assertTrue("Full flow should complete. Failure: " + failureReason, runFullFlow(10_000L));
    }

    // 2. Token locked during Phase 1
    @Test
    public void test2_TokenLockedDuringPhase1() {
        runFullFlow(10_000L);
        assertTrue("Token should be locked in sender wallet", senderWallet.tokenWasLocked);
    }

    // 3. Token marked SPENT after sender commit
    @Test
    public void test3_TokenMarkedSpentAfterCommit() {
        runFullFlow(10_000L);
        assertTrue("Token should be marked SPENT in sender wallet", senderWallet.tokenWasSpent);
    }

    // 4. Receiver wallet gets the token
    @Test
    public void test4_ReceiverGetsToken() {
        runFullFlow(10_000L);
        assertTrue("Receiver wallet should have received the token", receiverWallet.tokenWasReceived);
    }

    // 5. Sender proof has SENDER role
    @Test
    public void test5_SenderProofRole() {
        runFullFlow(10_000L);
        assertNotNull("Sender proof should not be null", senderProof);
        assertEquals(TransactionProof.Role.SENDER, senderProof.getRole());
    }

    // 6. Receiver proof has RECEIVER role
    @Test
    public void test6_ReceiverProofRole() {
        runFullFlow(10_000L);
        assertNotNull("Receiver proof should not be null", receiverProof);
        assertEquals(TransactionProof.Role.RECEIVER, receiverProof.getRole());
    }

    // 7. Both proofs reference the same txId
    @Test
    public void test7_ProofsReferenceSameTxId() {
        runFullFlow(10_000L);
        assertNotNull("Sender proof should not be null", senderProof);
        assertNotNull("Receiver proof should not be null", receiverProof);
        assertEquals("Both proofs must share the same txId", senderProof.getTxId(), receiverProof.getTxId());
    }

    // 8. Amount over ₹500 fails at Phase 1
    @Test
    public void test8_AmountOverLimitFails() {
        boolean success = runFullFlow(PreparePhaseHandler.MAX_TRANSACTION_PAISE + 100);
        assertFalse("Transaction over ₹500 should fail", success);
        assertNotNull("Failure reason should be set", failureReason);
        assertTrue(failureReason.contains("exceeds offline limit"));
    }

    // 9. Replay attack — same nonce rejected by Phase 2
    @Test
    public void test9_ReplayAttackRejected() {
        runFullFlow(10_000L);
        String usedNonce = sentTransaction.getNonce();
        
        resetResults();
        String tokenId = "tok_replay";
        long ts = System.currentTimeMillis();
        String receiverKey = receiverWallet.getPublicKey();
        String sig = senderWallet.signTransaction(tokenId + receiverKey + ts + usedNonce);
        
        Transaction replayTx = new Transaction("tx_replay", tokenId, senderWallet.getPublicKey(), 
                                              receiverKey, ts, usedNonce, sig);
        
        validationHandler.execute(replayTx, new ValidationPhaseHandler.ValidationListener() {
            @Override public void onValidationPassed(Transaction tx) { validatedTx = tx; }
            @Override public void onValidationFailed(Transaction tx, ValidationResult r) { failureReason = r.getFailureMessage(); }
        });
        ShadowLooper.idleMainLooper();
        
        assertNull("Replayed nonce should be rejected", validatedTx);
        assertNotNull("Failure reason should be set", failureReason);
        assertTrue(failureReason.contains("Nonce already seen"));
    }

    // 10. Phase progression ends at FINALIZED
    @Test
    public void test10_PhaseProgressionEndsAtFinalized() {
        runFullFlow(10_000L);
        assertEquals("Final state should be FINALIZED", TransactionPhase.FINALIZED, sentTransaction.getPhase());
    }
}
