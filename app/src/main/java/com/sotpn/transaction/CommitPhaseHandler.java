package com.sotpn.transaction;

import android.util.Log;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : CommitPhaseHandler.java
 * Package  : com.sotpn.transaction
 * Step     : Step 7 - Commit Phase Handler
 * Status   : Complete (Fixed - direct callbacks for testability)
 */
public class CommitPhaseHandler {

    private static final String TAG = "CommitPhaseHandler";

    private final WalletInterface   wallet;
    private final BleManager        bleManager;
    private final WifiDirectManager wifiManager;

    public interface CommitListener {
        void onReceiverCommitComplete(TransactionProof proof);
        void onSenderCommitComplete(TransactionProof proof);
        void onCommitFailed(String txId, String reason);
    }

    public CommitPhaseHandler(WalletInterface wallet,
                              BleManager bleManager,
                              WifiDirectManager wifiManager) {
        this.wallet      = wallet;
        this.bleManager  = bleManager;
        this.wifiManager = wifiManager;
    }

    // -----------------------------------------------------------------------
    // RECEIVER SIDE
    // -----------------------------------------------------------------------

    public void executeReceiverCommit(Transaction transaction,
                                      boolean useBle,
                                      CommitListener listener) {
        Log.i(TAG, "Phase 4 COMMIT (RECEIVER) txId: " + transaction.getTxId());
        transaction.setPhase(TransactionPhase.COMMITTING);

        // Sign the ACK
        String ackData = buildAckData(transaction.getTxId(), transaction.getTokenId());
        String ackSignature;
        try {
            ackSignature = wallet.signTransaction(ackData);
        } catch (Exception e) {
            String msg = "Failed to sign ACK: " + e.getMessage();
            Log.e(TAG, msg, e);
            transaction.setPhase(TransactionPhase.FAILED);
            listener.onCommitFailed(transaction.getTxId(), msg);
            return;
        }

        // Build and send ACK
        TransactionAck ack = new TransactionAck(
                transaction.getTxId(),
                transaction.getTokenId(),
                wallet.getPublicKey(),
                System.currentTimeMillis(),
                ackSignature,
                true
        );

        try {
            if (useBle && bleManager != null) {
                bleManager.sendAck(ack);
            } else if (!useBle && wifiManager != null) {
                wifiManager.sendAck(ack);
            }
        } catch (Exception e) {
            String msg = "Failed to send ACK: " + e.getMessage();
            Log.e(TAG, msg, e);
            transaction.setPhase(TransactionPhase.FAILED);
            listener.onCommitFailed(transaction.getTxId(), msg);
            return;
        }

        // Add token to wallet
        String proofStr = buildProofString(transaction, ackSignature);
        try {
            wallet.receiveToken(transaction.getTokenId(),
                    transaction.getSenderPublicKey(), proofStr);
        } catch (Exception e) {
            Log.e(TAG, "receiveToken() failed — continuing", e);
        }

        // Build proof and finalize
        TransactionProof proof = buildProof(transaction, ackSignature,
                TransactionProof.Role.RECEIVER);
        transaction.setPhase(TransactionPhase.FINALIZED);
        Log.i(TAG, "Phase 4 COMMIT complete (RECEIVER)");
        listener.onReceiverCommitComplete(proof);
    }

    // -----------------------------------------------------------------------
    // SENDER SIDE
    // -----------------------------------------------------------------------

    public void executeSenderCommit(Transaction transaction,
                                    TransactionAck ack,
                                    CommitListener listener) {
        Log.i(TAG, "Phase 4 COMMIT (SENDER) txId: " + transaction.getTxId());
        transaction.setPhase(TransactionPhase.COMMITTING);

        // Check txId match
        if (!ack.getTxId().equals(transaction.getTxId())) {
            String msg = "ACK txId mismatch — expected: " + transaction.getTxId()
                    + " got: " + ack.getTxId();
            Log.e(TAG, msg);
            transaction.setPhase(TransactionPhase.FAILED);
            listener.onCommitFailed(transaction.getTxId(), msg);
            return;
        }

        // Verify ACK signature
        String ackData = buildAckData(ack.getTxId(), ack.getTokenId());
        boolean ackValid = wallet.verifySignature(
                ackData, ack.getAckSignature(), ack.getReceiverPublicKey());

        if (!ackValid) {
            String msg = "ACK signature verification FAILED";
            Log.e(TAG, msg);
            transaction.setPhase(TransactionPhase.FAILED);
            listener.onCommitFailed(transaction.getTxId(), msg);
            return;
        }

        // Mark token spent
        String proofStr = buildProofString(transaction, ack.getAckSignature());
        try {
            wallet.markTokenSpent(transaction.getTokenId(), proofStr);
        } catch (Exception e) {
            String msg = "Failed to mark token spent: " + e.getMessage();
            Log.e(TAG, msg, e);
            transaction.setPhase(TransactionPhase.FAILED);
            listener.onCommitFailed(transaction.getTxId(), msg);
            return;
        }

        // Build proof and finalize
        TransactionProof proof = buildProof(transaction, ack.getAckSignature(),
                TransactionProof.Role.SENDER);
        transaction.setPhase(TransactionPhase.FINALIZED);
        Log.i(TAG, "Phase 4 COMMIT complete (SENDER)");
        listener.onSenderCommitComplete(proof);
    }

    // -----------------------------------------------------------------------
    // Rejection
    // -----------------------------------------------------------------------

    public void sendRejection(Transaction transaction, String reason, boolean useBle) {
        Log.w(TAG, "Sending REJECTION txId: " + transaction.getTxId());
        try {
            String rejectData = "REJECTED:" + transaction.getTxId()
                    + ":" + transaction.getTokenId();
            String rejectSignature = wallet.signTransaction(rejectData);
            TransactionAck rejection = new TransactionAck(
                    transaction.getTxId(),
                    transaction.getTokenId(),
                    wallet.getPublicKey(),
                    System.currentTimeMillis(),
                    rejectSignature,
                    false
            );
            if (useBle && bleManager != null) {
                bleManager.sendAck(rejection);
            } else if (!useBle && wifiManager != null) {
                wifiManager.sendAck(rejection);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send rejection: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String buildAckData(String txId, String tokenId) {
        return "Received:" + txId + ":" + tokenId;
    }

    private String buildProofString(Transaction tx, String ackSignature) {
        return tx.getTxId() + "|" + tx.getTokenId() + "|" + ackSignature;
    }

    private TransactionProof buildProof(Transaction tx,
                                        String ackSignature,
                                        TransactionProof.Role role) {
        return new TransactionProof(
                tx.getTxId(), tx.getTokenId(),
                tx.getSenderPublicKey(), tx.getReceiverPublicKey(),
                tx.getTimestamp(), tx.getNonce(),
                tx.getSignature(), ackSignature,
                System.currentTimeMillis(), role
        );
    }
}