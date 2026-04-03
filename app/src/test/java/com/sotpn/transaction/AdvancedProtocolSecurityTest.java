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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AdvancedProtocolSecurityTest {

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
    public void testAudit_Receiver_IgnoresTransactionsForOthers() {
        String someoneElse = "pub_key_someone_else";
        Transaction txForOther = new Transaction("tx_1", "tok_1", senderWallet.getPublicKey(), someoneElse, 
                                                 System.currentTimeMillis(), "nonce_1", "");
        signTransaction(txForOther, senderWallet);
        
        manager.onTransactionReceived(txForOther);
        ShadowLooper.idleMainLooper();

        assertNull("Receiver MUST ignore transactions not meant for their public key", 
                   manager.getActiveTransaction());
    }

    @Test
    public void testAudit_Sender_RejectsAckWithWrongTokenId() {
        // manager starts as SENDER
        myWallet.tokenToReturn = new com.sotpn.wallet.WalletInterface.TokenInfo("tok_correct", 1000L, System.currentTimeMillis() + 60000);
        manager.startSend("receiver_key", 1000L);
        ShadowLooper.idleMainLooper();
        Transaction active = manager.getActiveTransaction();
        assertNotNull(active);

        // Receiver sends ACK for the correct txId but WRONG tokenId
        String txId = active.getTxId();
        String wrongToken = "WRONG_TOKEN_ID";
        String receiverKey = "receiver_key";
        String ackData = "Received:" + txId + ":" + wrongToken;
        String maliciousSig = "sig:" + receiverKey + ":" + ackData.hashCode();

        TransactionAck maliciousAck = new TransactionAck(txId, wrongToken, 
                                                         receiverKey, System.currentTimeMillis(), maliciousSig, true);
        
        manager.onAckReceived(maliciousAck);
        ShadowLooper.idleMainLooper();

        assertEquals("Sender MUST fail the transaction if TokenId mismatch", 
                     TransactionPhase.FAILED, active.getPhase());
    }

    @Test
    public void testAudit_Gossip_DetectsIdentityCollision() {
        com.sotpn.communication.GossipStore store = new com.sotpn.communication.GossipStore();
        String tokenId = "tok_1";
        String txId = "tx_shared";
        
        // 1. Device A reports originating Tx_shared
        store.addGossip(new com.sotpn.communication.GossipMessage(tokenId, "device_A", txId, System.currentTimeMillis(), 0));
        
        // 2. Device B reports originating the SAME TxId (Collision/Spoofing)
        com.sotpn.communication.GossipStore.ConflictResult result = 
            store.addGossip(new com.sotpn.communication.GossipMessage(tokenId, "device_B", txId, System.currentTimeMillis(), 0));

        assertTrue("Gossip Store MUST detect conflict if two different devices report the same TxId", 
                   result.isConflict);
    }
}
