package com.sotpn.transaction;

import android.os.SystemClock;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;
import com.sotpn.model.TransactionPhase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * ATOMIC INTEGRITY AUDIT
 * Verifies that Phase 4 (Commit) is immune to identity hijacking and timing exploits.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AtomicIntegrityAuditTest {

    private CommitPhaseHandler handler;
    private MockWallet myWallet;
    private MockWallet otherWallet;

    @Before
    public void setUp() {
        myWallet = new MockWallet();
        otherWallet = new MockWallet();
        handler = new CommitPhaseHandler(myWallet, null, null);
    }

    private void signTransaction(Transaction tx, MockWallet signer) {
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(signer.signTransaction(data));
    }

    // -----------------------------------------------------------------------
    // TEST 1: Phase 4 Identity Shielding
    // Verifies that the Sender rejects an ACK if it comes from anyone other 
    // than the intended Receiver specified in Phase 1.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Commit_RejectsAckFromThirdParty() {
        // We are the Sender
        String tokenId = "tok_secure";
        Transaction tx = new Transaction("tx_1", tokenId, myWallet.getPublicKey(), otherWallet.getPublicKey(), 
                                         System.currentTimeMillis(), "n1", "");
        signTransaction(tx, myWallet);

        // Attacker observes the mesh and tries to send a spoofed ACK
        MockWallet attacker = new MockWallet();
        attacker.setPublicKey("ATTACKER_PUB_KEY");
        
        String ackData = "Received:tx_1:" + tokenId;
        TransactionAck hijackedAck = new TransactionAck(
                "tx_1", tokenId, attacker.getPublicKey(), 
                System.currentTimeMillis(), attacker.signTransaction(ackData), true);

        final String[] failure = {null};
        handler.executeSenderCommit(tx, hijackedAck, new CommitPhaseHandler.CommitListener() {
            @Override public void onReceiverCommitComplete(TransactionProof p) {}
            @Override public void onSenderCommitComplete(TransactionProof p) {}
            @Override public void onCommitFailed(String txId, String reason) { failure[0] = reason; }
        });

        assertNotNull("Commit MUST fail if ACK receiver doesn't match Phase 1 receiver", failure[0]);
        assertTrue(failure[0].contains("identity mismatch"));
        assertFalse("Token MUST NOT be marked spent on hijacked ACK", myWallet.tokenWasSpent);
    }

    // -----------------------------------------------------------------------
    // TEST 2: Pre-Commit Expiry Re-Verification
    // If a token expires *exactly* during the 10s Adaptive Delay, the 
    // Receiver MUST NOT commit it even if the delay finishes.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Commit_RejectsExpiredTokenAfterDelay() {
        // Set a fixed start time for deterministic testing
        long baseTime = 100_000;
        SystemClock.setCurrentTimeMillis(baseTime);
        
        // Transaction signed at baseTime (Valid for 60s, i.e. until 160,000)
        Transaction tx = new Transaction("tx_expired", "tok_1", otherWallet.getPublicKey(), myWallet.getPublicKey(), 
                                         baseTime, "n1", "");
        signTransaction(tx, otherWallet);

        // Advance to 161,000 (EXPIRED)
        SystemClock.setCurrentTimeMillis(161_000);
        
        final String[] failure = {null};
        handler.executeReceiverCommit(tx, true, new CommitPhaseHandler.CommitListener() {
            @Override public void onReceiverCommitComplete(TransactionProof p) {}
            @Override public void onSenderCommitComplete(TransactionProof p) {}
            @Override public void onCommitFailed(String txId, String reason) { failure[0] = reason; }
        });

        assertNotNull("Receiver MUST reject if token expired during the delay window", failure[0]);
        assertEquals(TransactionPhase.FAILED, tx.getPhase());
        assertFalse("Receiver MUST NOT accept expired token", myWallet.tokenWasReceived);
    }

    // -----------------------------------------------------------------------
    // TEST 3: Phase 1 Fund Recovery
    // Verifies that if the radio fails during Phase 1, the token is unlocked.
    // -----------------------------------------------------------------------
    @Test
    public void testAudit_Prepare_UnlocksTokenOnRadioFailure() {
        com.sotpn.communication.BleManager brokenBle = mock(com.sotpn.communication.BleManager.class);
        doThrow(new RuntimeException("Radio Death")).when(brokenBle).sendTransaction(any());

        PreparePhaseHandler prepareHandler = new PreparePhaseHandler(myWallet, brokenBle, null);
        
        myWallet.tokenToReturn = new com.sotpn.wallet.WalletInterface.TokenInfo("tok_1", 100, System.currentTimeMillis() + 100000);
        
        prepareHandler.execute("receiver_pub", 50, true, new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) {}
            @Override public void onPrepareFailed(String r) {}
        });

        assertFalse("Token MUST be unlocked if radio fails", myWallet.isTokenLocked("tok_1"));
        assertTrue("unlockToken() should have been called", myWallet.tokenWasUnlocked);
    }
}
