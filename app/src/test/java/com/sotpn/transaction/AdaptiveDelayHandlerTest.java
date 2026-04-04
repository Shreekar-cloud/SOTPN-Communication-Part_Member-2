package com.sotpn.transaction;

import com.sotpn.communication.GossipEngine;
import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
public class AdaptiveDelayHandlerTest {

    private AdaptiveDelayCalculator calculator;
    private GossipStore             gossipStore;
    private MockAdaptiveDelayHandler handler;

    private Transaction                      completedTx    = null;
    private Transaction                      abortedTx      = null;
    private GossipStore.ConflictResult       conflictResult = null;

    @Before
    public void setUp() {
        calculator  = new AdaptiveDelayCalculator();
        gossipStore = new GossipStore();
        handler     = new MockAdaptiveDelayHandler(calculator, gossipStore);
    }

    private AdaptiveDelayHandler.DelayListener makeListener() {
        return new AdaptiveDelayHandler.DelayListener() {
            @Override public void onDelayComplete(Transaction tx) { completedTx = tx; }
            @Override public void onDelayAborted(Transaction tx, GossipStore.ConflictResult conflict) {
                abortedTx      = tx;
                conflictResult = conflict;
            }
            @Override public void onDelayProgress(long remainingMs, long totalMs, AdaptiveDelayCalculator.RiskLevel risk) {}
        };
    }

    private Transaction buildTransaction() {
        return new Transaction("tx_001", "tok_abc", "s", "r", System.currentTimeMillis(), "n_001", "sig_001");
    }

    @Test
    public void testDelay_completesCleanly_whenNoConflict() {
        Transaction tx = buildTransaction();
        handler.simulateComplete(tx, makeListener());
        assertNotNull(completedTx);
        assertEquals(TransactionPhase.COMMITTING, completedTx.getPhase());
    }

    @Test
    public void testDelay_abortsWhenConflictDetected() {
        // Updated to use hardened 6-argument constructor
        gossipStore.addGossip(new GossipMessage("tok_abc", "device_A", "tx_001", System.currentTimeMillis(), "sig_001", 0));
        gossipStore.addGossip(new GossipMessage("tok_abc", "device_B", "tx_999", System.currentTimeMillis(), "sig_999", 0));

        GossipStore.ConflictResult conflict = gossipStore.checkConflict("tok_abc");
        handler.simulateAbort(buildTransaction(), conflict, makeListener());

        assertNotNull(abortedTx);
        assertTrue(conflictResult.isConflict);
    }

    static class MockAdaptiveDelayHandler {
        private final AdaptiveDelayCalculator calculator;
        private final GossipStore             gossipStore;

        MockAdaptiveDelayHandler(AdaptiveDelayCalculator calculator, GossipStore gossipStore) {
            this.calculator  = calculator;
            this.gossipStore = gossipStore;
        }

        void simulateComplete(Transaction tx, AdaptiveDelayHandler.DelayListener listener) {
            tx.setPhase(TransactionPhase.COMMITTING);
            listener.onDelayComplete(tx);
        }

        void simulateAbort(Transaction tx, GossipStore.ConflictResult conflict, AdaptiveDelayHandler.DelayListener listener) {
            tx.setPhase(TransactionPhase.FAILED);
            listener.onDelayAborted(tx, conflict);
        }
    }
}
