package com.sotpn;

import android.app.Application;
import android.util.Log;

import com.sotpn.transaction.TransactionManager;
import com.sotpn.wallet.SimpleWallet;
import com.sotpn.wallet.WalletInterface;

/**
 * Custom Application class to host the Singleton TransactionManager.
 */
public class SotpnApp extends Application {
    private static final String TAG = "SotpnApp";

    private static SotpnApp instance;
    private TransactionManager transactionManager;
    private WalletInterface wallet;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "SotpnApp created");
    }

    public static SotpnApp getInstance() {
        return instance;
    }

    public synchronized TransactionManager getTransactionManager(TransactionManager.TransactionListener listener) {
        if (transactionManager == null) {
            // Lazy initialize with the SimpleWallet
            wallet = new SimpleWallet(this);
            transactionManager = new TransactionManager(this, wallet, listener);
            Log.i(TAG, "Global TransactionManager initialized");
        } else {
            // Update the listener to the caller (e.g. the active ViewModel)
            transactionManager.setListener(listener);
        }
        return transactionManager;
    }

    public WalletInterface getWallet() {
        if (wallet == null) {
            wallet = new SimpleWallet(this);
        }
        return wallet;
    }
}
