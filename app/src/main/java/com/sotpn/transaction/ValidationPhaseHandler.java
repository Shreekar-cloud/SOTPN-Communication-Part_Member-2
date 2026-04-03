package com.sotpn.transaction;

import android.util.Log;

import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

public class ValidationPhaseHandler {

    private static final String TAG              = "ValidationPhaseHandler";
    private static final long   TOKEN_VALIDITY_MS = 60_000;
    private static final long   MAX_CLOCK_SKEW_MS = 5_000;

    private final WalletInterface wallet;
    private final NonceStore      nonceStore;
    private final GossipEngine    gossipEngine;

    public interface ValidationListener {
        void onValidationPassed(Transaction transaction);
        void onValidationFailed(Transaction transaction, ValidationResult result);
    }

    public ValidationPhaseHandler(WalletInterface wallet,
                                  NonceStore nonceStore,
                                  GossipEngine gossipEngine) {
        this.wallet       = wallet;
        this.nonceStore   = nonceStore;
        this.gossipEngine = gossipEngine;
    }

    public void execute(Transaction transaction, ValidationListener listener) {
        Log.i(TAG, "Phase 2 VALIDATION started for txId: " + transaction.getTxId());
        transaction.setPhase(TransactionPhase.VALIDATING);

        ValidationResult result = runAllChecks(transaction);

        if (result.isValid()) {
            transaction.setPhase(TransactionPhase.DELAYING);
            Log.i(TAG, "Phase 2 PASSED");
            listener.onValidationPassed(transaction);
        } else {
            transaction.setPhase(TransactionPhase.FAILED);
            Log.w(TAG, "Phase 2 FAILED: " + result);
            listener.onValidationFailed(transaction, result);
        }
    }

    private ValidationResult runAllChecks(Transaction tx) {
        ValidationResult r;

        r = checkRequiredFields(tx);
        if (!r.isValid()) return r;

        r = checkSignature(tx);
        if (!r.isValid()) return r;

        r = checkExpiry(tx);
        if (!r.isValid()) return r;

        r = checkNonce(tx);
        if (!r.isValid()) return r;

        r = checkGossipConflict(tx);
        if (!r.isValid()) return r;

        return ValidationResult.pass();
    }

    private ValidationResult checkRequiredFields(Transaction tx) {
        if (isEmpty(tx.getTxId())
                || isEmpty(tx.getTokenId())
                || isEmpty(tx.getSenderPublicKey())
                || isEmpty(tx.getReceiverPublicKey())
                || tx.getTimestamp() <= 0
                || isEmpty(tx.getNonce())
                || isEmpty(tx.getSignature())) {
            Log.w(TAG, "CHECK 1 FAILED — missing required fields");
            return ValidationResult.fail(
                    ValidationResult.FailureCode.MISSING_FIELDS,
                    "Transaction is missing one or more required fields");
        }
        return ValidationResult.pass();
    }

    private ValidationResult checkSignature(Transaction tx) {
        String dataToVerify = tx.getTokenId()
                + tx.getReceiverPublicKey()
                + tx.getTimestamp()
                + tx.getNonce();

        boolean valid = wallet.verifySignature(
                dataToVerify, tx.getSignature(), tx.getSenderPublicKey());

        if (!valid) {
            Log.w(TAG, "CHECK 2 FAILED — invalid signature");
            return ValidationResult.fail(
                    ValidationResult.FailureCode.INVALID_SIGNATURE,
                    "Transaction signature is invalid");
        }
        return ValidationResult.pass();
    }

    private ValidationResult checkExpiry(Transaction tx) {
        long now  = System.currentTimeMillis();
        long age  = now - tx.getTimestamp();

        if (age > TOKEN_VALIDITY_MS) {
            Log.w(TAG, "CHECK 3 FAILED — expired. Age=" + age + "ms");
            return ValidationResult.fail(
                    ValidationResult.FailureCode.TOKEN_EXPIRED,
                    "Transaction expired. Age: " + (age / 1000) + "s");
        }
        if (tx.getTimestamp() > now + MAX_CLOCK_SKEW_MS) {
            Log.w(TAG, "CHECK 3 FAILED — future timestamp");
            return ValidationResult.fail(
                    ValidationResult.FailureCode.TOKEN_EXPIRED,
                    "Transaction timestamp is in the future");
        }
        return ValidationResult.pass();
    }

    private ValidationResult checkNonce(Transaction tx) {
        boolean isNew = nonceStore.checkAndRecord(tx.getNonce());
        if (!isNew) {
            Log.w(TAG, "CHECK 4 FAILED — nonce replay: " + tx.getNonce());
            return ValidationResult.fail(
                    ValidationResult.FailureCode.NONCE_REPLAY,
                    "Nonce already seen — possible replay attack");
        }
        return ValidationResult.pass();
    }

    private ValidationResult checkGossipConflict(Transaction tx) {
        GossipStore.ConflictResult conflict =
                gossipEngine.checkConflict(tx.getTokenId());
        if (conflict.isConflict) {
            Log.w(TAG, "CHECK 5 FAILED — gossip conflict for token: " + tx.getTokenId());
            return ValidationResult.fail(
                    ValidationResult.FailureCode.GOSSIP_CONFLICT,
                    "Token seen in another transaction — possible double-spend");
        }
        return ValidationResult.pass();
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}