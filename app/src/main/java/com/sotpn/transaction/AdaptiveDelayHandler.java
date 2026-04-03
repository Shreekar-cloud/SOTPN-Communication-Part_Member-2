package com.sotpn.transaction;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sotpn.communication.BleManager;
import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : AdaptiveDelayHandler.java
 * Package  : com.sotpn.transaction
 * Step     : Step 6 - Adaptive Delay Calculator
 * Status   : Complete
 */
public class AdaptiveDelayHandler implements GossipEngine.GossipListener {

    private static final String TAG           = "AdaptiveDelayHandler";
    private static final long   TICK_INTERVAL = 1_000;

    private final AdaptiveDelayCalculator calculator;
    private final GossipEngine            gossipEngine;
    private final BleManager              bleManager;
    private final Handler                 handler;

    private Transaction     activeTransaction = null;
    private DelayListener   listener          = null;
    private long            delayMs           = 0;
    private long            startTimeMs       = 0;
    private boolean         isRunning         = false;

    private Runnable commitRunnable  = null;
    private Runnable tickRunnable    = null;

    public interface DelayListener {
        void onDelayComplete(Transaction transaction);
        void onDelayAborted(Transaction transaction,
                            GossipStore.ConflictResult conflictDetails);
        void onDelayProgress(long remainingMs, long totalMs,
                             AdaptiveDelayCalculator.RiskLevel riskLevel);
    }

    public AdaptiveDelayHandler(AdaptiveDelayCalculator calculator,
                                GossipEngine gossipEngine,
                                BleManager bleManager) {
        this.calculator   = calculator;
        this.gossipEngine = gossipEngine;
        this.bleManager   = bleManager;
        this.handler      = new Handler(Looper.getMainLooper());
    }

    public void execute(Transaction transaction, DelayListener listener) {
        if (isRunning) return;

        this.activeTransaction = transaction;
        this.listener          = listener;
        this.isRunning         = true;
        this.startTimeMs       = System.currentTimeMillis();

        int nearbyCount = bleManager.getNearbyDeviceCount();
        this.delayMs    = calculator.calculateDelayMs(nearbyCount);

        AdaptiveDelayCalculator.RiskLevel risk = calculator.getRiskLevel(nearbyCount);

        Log.i(TAG, "▶ Phase 3 ADAPTIVE DELAY started");

        transaction.setPhase(TransactionPhase.DELAYING);

        gossipEngine.startBroadcasting(
                transaction.getTokenId(),
                transaction.getTxId(),
                delayMs
        );

        commitRunnable = () -> {
            if (!isRunning) return;
            finalize(true, null);
        };
        handler.postDelayed(commitRunnable, delayMs);

        tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                long elapsed   = System.currentTimeMillis() - startTimeMs;
                long remaining = Math.max(0, delayMs - elapsed);

                listener.onDelayProgress(remaining, delayMs, risk);

                if (remaining > 0) {
                    handler.postDelayed(this, TICK_INTERVAL);
                }
            }
        };
        handler.postDelayed(tickRunnable, TICK_INTERVAL);

        listener.onDelayProgress(delayMs, delayMs, risk);
    }

    @Override
    public void onConflictDetected(GossipStore.ConflictResult result) {
        if (!isRunning) return;
        finalize(false, result);
    }

    @Override
    public void onGossipReceived(GossipMessage message) {
        Log.d(TAG, "Gossip received: " + message);
    }

    public void abort() {
        if (isRunning) {
            finalize(false, null);
        }
    }

    private void finalize(boolean success, GossipStore.ConflictResult conflict) {
        isRunning = false;

        if (commitRunnable != null) {
            handler.removeCallbacks(commitRunnable);
            commitRunnable = null;
        }
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }

        gossipEngine.stopBroadcasting();

        Transaction tx = activeTransaction;
        activeTransaction = null;

        if (success) {
            tx.setPhase(TransactionPhase.COMMITTING);
            listener.onDelayComplete(tx);
        } else {
            tx.setPhase(TransactionPhase.FAILED);
            listener.onDelayAborted(tx,
                    conflict != null ? conflict : GossipStore.ConflictResult.NO_CONFLICT);
        }
    }

    public boolean isRunning() { return isRunning; }

    public long getRemainingMs() {
        if (!isRunning) return 0;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return Math.max(0, delayMs - elapsed);
    }
}
