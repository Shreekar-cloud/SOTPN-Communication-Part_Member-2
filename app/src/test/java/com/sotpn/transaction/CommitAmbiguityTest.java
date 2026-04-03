package com.sotpn.transaction;

import android.content.Context;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
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
public class CommitAmbiguityTest {

    private TransactionManager receiverManager;
    private MockWallet receiverWallet;
    private MockWallet senderWallet;
    private Transaction tx;

    @Before
    public void setUp() {
        receiverWallet = new MockWallet();
        senderWallet = new MockWallet();
        receiverManager = new TransactionManager(RuntimeEnvironment.getApplication(), receiverWallet, mock(TransactionManager.TransactionListener.class));
        
        tx = new Transaction("tx_retried_123", "tok_1", senderWallet.getPublicKey(), receiverWallet.getPublicKey(), 
                             System.currentTimeMillis(), "nonce_retried", "");
        
        // Sign it correctly with the sender's identity
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(senderWallet.signTransaction(data));
    }

    @Test
    public void testIdempotency_DuplicateSuccessRetried_DoesNotDoubleBalance() {
        // 1. Process transaction for the first time
        receiverManager.onTransactionReceived(tx);
        ShadowLooper.idleMainLooper(); 
        
        // Wait for the 10s Adaptive Delay to finish so commit happens
        ShadowLooper.idleMainLooper(11, TimeUnit.SECONDS);
        
        assertTrue("Receiver should have processed and received the token", receiverWallet.tokenWasReceived);
        receiverWallet.tokenWasReceived = false; // Reset flag for idempotency check

        // 2. Sender retries the EXACT same TX because they didn't get the ACK
        receiverManager.onTransactionReceived(tx);
        ShadowLooper.idleMainLooper();

        // 3. Verification: System must detect this is already processed via NonceStore 
        // and NOT call receiveToken again.
        assertFalse("CRITICAL: Receiver must NOT accept the same transaction twice!", 
                    receiverWallet.tokenWasReceived);
    }
}
