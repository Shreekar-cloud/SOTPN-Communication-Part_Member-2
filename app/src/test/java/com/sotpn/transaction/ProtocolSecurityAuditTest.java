package com.sotpn.transaction;

import android.content.Context;
import com.sotpn.communication.GossipMessage;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProtocolSecurityAuditTest {

    private TransactionManager manager;
    private MockWallet myWallet;
    private MockWallet senderWallet;

    @Before
    public void setUp() {
        myWallet = new MockWallet();
        senderWallet = new MockWallet();
        Context context = RuntimeEnvironment.getApplication();
        TransactionManager.TransactionListener listener = mock(TransactionManager.TransactionListener.class);
        manager = new TransactionManager(context, myWallet, listener);
    }

    private void signTransaction(Transaction tx, MockWallet signer) {
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(signer.signTransaction(data));
    }

    @Test
    public void testAudit_GossipConflictDuringDelay_AbortsTransaction() {
        Transaction tx = new Transaction("tx_audit", "tok_audit", senderWallet.getPublicKey(), myWallet.getPublicKey(), 
                                         System.currentTimeMillis(), "n_audit", "");
        signTransaction(tx, senderWallet);
        
        manager.onTransactionReceived(tx);
        ShadowLooper.idleMainLooper();
        assertEquals(TransactionPhase.DELAYING, tx.getPhase());

        // Simulate conflict arriving via Gossip
        // Correct wire format: TOKEN_SEEN : tokenId : senderPubKey : txId : timestamp : signature : hopCount
        String maliciousGossip = "TOKEN_SEEN:tok_audit:dev_evil:tx_evil:" + System.currentTimeMillis() + ":sig_evil:0";
        manager.onGossipReceived(maliciousGossip);
        
        // CRITICAL: Must idle looper to allow the internal handleConflict logic to run
        ShadowLooper.idleMainLooper();

        assertEquals("Transaction MUST be FAILED after mid-delay conflict", 
                     TransactionPhase.FAILED, tx.getPhase());
    }

    @Test
    public void testAudit_InterRadioCollision_IsTreatedAsDoubleSpend() {
        String tokenId = "tok_race";
        Transaction tx1 = new Transaction("tx_1", tokenId, senderWallet.getPublicKey(), myWallet.getPublicKey(), System.currentTimeMillis(), "n1", "");
        Transaction tx2 = new Transaction("tx_2", tokenId, senderWallet.getPublicKey(), myWallet.getPublicKey(), System.currentTimeMillis(), "n2", "");
        signTransaction(tx1, senderWallet);
        signTransaction(tx2, senderWallet);

        manager.onTransactionReceived(tx1);
        ShadowLooper.idleMainLooper();

        manager.onTransactionReceived(tx2, "MAC_BLE");
        ShadowLooper.idleMainLooper();

        assertEquals(TransactionPhase.FAILED, tx1.getPhase());
    }
}
