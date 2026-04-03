package com.sotpn.transaction;

import android.util.Log;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.WifiDirectManager;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;

import java.util.UUID;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 */
public class PreparePhaseHandler {

    private static final String TAG = "PreparePhaseHandler";

    public static final long MAX_TRANSACTION_PAISE = 50_000L;  // ₹500
    public static final long MAX_WALLET_PAISE      = 200_000L; // ₹2000

    private final WalletInterface   wallet;
    private final BleManager        bleManager;
    private final WifiDirectManager wifiManager;

    private Transaction pendingTransaction = null;

    public interface PrepareListener {
        void onPrepareSent(Transaction transaction);
        void onPrepareFailed(String reason);
    }

    public PreparePhaseHandler(WalletInterface wallet,
                               BleManager bleManager,
                               WifiDirectManager wifiManager) {
        this.wallet      = wallet;
        this.bleManager  = bleManager;
        this.wifiManager = wifiManager;
    }

    public void execute(String receiverPublicKey,
                        long amountPaise,
                        boolean useBle,
                        PrepareListener listener) {

        Log.i(TAG, "▶ Phase 1 PREPARE started | amount=" + amountPaise + " paise");

        String amountError = validateAmount(amountPaise);
        if (amountError != null) {
            Log.e(TAG, "Amount validation failed: " + amountError);
            listener.onPrepareFailed(amountError);
            return;
        }

        WalletInterface.TokenInfo token = wallet.getSpendableToken(amountPaise);
        if (token == null) {
            String msg = "No spendable token available";
            listener.onPrepareFailed(msg);
            return;
        }

        if (!token.isValid()) {
            String msg = "Selected token has expired";
            listener.onPrepareFailed(msg);
            return;
        }

        boolean locked = wallet.lockToken(token.tokenId);
        if (!locked) {
            String msg = "Token lock failed";
            listener.onPrepareFailed(msg);
            return;
        }

        Transaction transaction;
        try {
            transaction = buildTransaction(token, receiverPublicKey);
        } catch (Exception e) {
            wallet.unlockToken(token.tokenId);
            listener.onPrepareFailed("Failed to build transaction");
            return;
        }

        pendingTransaction = transaction;

        try {
            if (useBle) {
                if (bleManager != null) bleManager.sendTransaction(transaction);
            } else {
                if (wifiManager != null) wifiManager.sendTransaction(transaction);
            }
        } catch (Exception e) {
            wallet.unlockToken(token.tokenId);
            pendingTransaction = null;
            listener.onPrepareFailed("Failed to send transaction");
            return;
        }

        transaction.setPhase(TransactionPhase.VALIDATING);
        Log.i(TAG, "✅ Phase 1 PREPARE complete");
        listener.onPrepareSent(transaction);
    }

    public void abort() {
        if (pendingTransaction != null) {
            String tokenId = pendingTransaction.getTokenId();
            wallet.unlockToken(tokenId);
            pendingTransaction = null;
        }
    }

    public Transaction getPendingTransaction() {
        return pendingTransaction;
    }

    private Transaction buildTransaction(WalletInterface.TokenInfo token,
                                         String receiverPublicKey) {
        String txId      = UUID.randomUUID().toString();
        long   timestamp = System.currentTimeMillis();
        String nonce     = wallet.generateNonce();
        String myPubKey  = wallet.getPublicKey();

        String dataToSign = token.tokenId + receiverPublicKey + timestamp + nonce;
        String signature  = wallet.signTransaction(dataToSign);

        return new Transaction(txId, token.tokenId, myPubKey,
                receiverPublicKey, timestamp, nonce, signature);
    }

    private String validateAmount(long amountPaise) {
        if (amountPaise <= 0) return "Amount must be greater than zero";
        if (amountPaise > MAX_TRANSACTION_PAISE) return "Amount exceeds offline limit";
        if (amountPaise > wallet.getBalance()) return "Insufficient balance";
        return null;
    }
}
