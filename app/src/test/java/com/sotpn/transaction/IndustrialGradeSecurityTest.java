package com.sotpn.transaction;

import android.content.Context;
import android.os.SystemClock;
import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionPhase;
import com.sotpn.wallet.WalletInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * INDUSTRIAL GRADE SECURITY TESTS
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class IndustrialGradeSecurityTest {

    private TransactionManager manager;
    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        Context context = RuntimeEnvironment.getApplication();
        TransactionManager.TransactionListener listener = mock(TransactionManager.TransactionListener.class);
        manager = new TransactionManager(context, wallet, listener);
        
        // Use a fixed baseline time past epoch 0
        SystemClock.setCurrentTimeMillis(100_000);
    }

    private void signTransaction(Transaction tx, MockWallet signer) {
        String data = tx.getTokenId() + tx.getReceiverPublicKey() + tx.getTimestamp() + tx.getNonce();
        tx.setSignature(signer.signTransaction(data));
    }

    // -----------------------------------------------------------------------
    // TEST 1: Race-to-the-Bottom (Concurrent Lock)
    // -----------------------------------------------------------------------
    @Test
    public void testRace_ConcurrentTokenLock_OnlyOneWins() throws InterruptedException {
        final String tokenId = "tok_race_industrial";
        final int threadCount = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (wallet.lockToken(tokenId)) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertEquals("Exactly one thread must succeed in locking the token", 1, successCount.get());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Clock Jump Resilience
    // -----------------------------------------------------------------------
    @Test
    public void testResilience_ClockJumpMidTransaction_AbortsOnExpiry() {
        MockWallet senderWallet = new MockWallet();
        senderWallet.setPublicKey("pub_sender_jump");
        
        long startTime = System.currentTimeMillis();
        
        Transaction tx = new Transaction("tx_jump_ind", "tok_jump_ind", senderWallet.getPublicKey(), wallet.getPublicKey(), 
                                         startTime, "n_jump_ind", "");
        signTransaction(tx, senderWallet);

        // 1. Receive transaction - enters Phase 3 (DELAYING) synchronously.
        manager.onTransactionReceived(tx);
        
        assertEquals("Transaction should be in DELAYING phase after receipt", 
                     TransactionPhase.DELAYING, tx.getPhase());

        // 2. SIMULATE CLOCK JUMP: System wall clock moves 2 minutes forward.
        // Token validity is 60s, so it is now EXPIRED.
        // We use advanceBy to ensure looper and system clock are in sync.
        ShadowSystemClock.advanceBy(2, TimeUnit.MINUTES);
        
        // 3. Process the queued commit task. 
        ShadowLooper.idleMainLooper();

        // 4. Verification: The re-verification in CommitPhaseHandler must have aborted the tx.
        assertEquals("Transaction MUST be FAILED after mid-delay clock jump results in expiry", 
                     TransactionPhase.FAILED, tx.getPhase());
    }

    // -----------------------------------------------------------------------
    // TEST 3: Multi-Token Exhaustion
    // -----------------------------------------------------------------------
    @Test
    public void testExhaustion_StartingMoreTransactionsThanTokens_FailsCorrectly() {
        wallet.reset();
        wallet.balance = 1000;
        PreparePhaseHandler prepareHandler = new PreparePhaseHandler(wallet, null, null);
        
        long now = System.currentTimeMillis();
        WalletInterface.TokenInfo t1 = new WalletInterface.TokenInfo("tok_limit_ind", 100, now + 60000);
        wallet.tokenToReturn = t1;

        // 1st preparation succeeds
        prepareHandler.execute("rec_pub", 100, true, mock(PreparePhaseHandler.PrepareListener.class));
        assertTrue("Token 1 must be locked", wallet.isTokenLocked("tok_limit_ind"));

        // 2nd preparation with same token fails
        final String[] failure = {null};
        prepareHandler.execute("rec_pub", 100, true, new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) {}
            @Override public void onPrepareFailed(String r) { failure[0] = r; }
        });

        assertNotNull("Should fail to prepare second transaction", failure[0]);
        assertEquals("Token lock failed", failure[0]);
    }
}
