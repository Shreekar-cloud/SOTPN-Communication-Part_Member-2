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
import org.robolectric.annotation.LooperMode;
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
@LooperMode(LooperMode.Mode.PAUSED)
public class IndustrialGradeSecurityTest {

    private TransactionManager manager;
    private MockWallet wallet;

    @Before
    public void setUp() {
        wallet = new MockWallet();
        Context context = RuntimeEnvironment.getApplication();
        TransactionManager.TransactionListener listener = mock(TransactionManager.TransactionListener.class);
        manager = new TransactionManager(context, wallet, listener);
        
        // Start at a safe baseline time well past epoch 0
        if (System.currentTimeMillis() < 500_000) {
            ShadowSystemClock.advanceBy(1, TimeUnit.HOURS);
        }
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
        final String tokenId = "tok_race_ind_" + System.nanoTime();
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
        assertEquals("Exactly one thread must succeed in locking the token index", 1, successCount.get());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Clock Jump Resilience
    // -----------------------------------------------------------------------
    @Test
    public void testResilience_ClockJumpMidTransaction_AbortsOnExpiry() {
        MockWallet senderWallet = new MockWallet();
        senderWallet.setPublicKey("pub_sender_jump");
        
        // 1. Prepare transaction with current shadowed time
        long startTime = System.currentTimeMillis();
        Transaction tx = new Transaction("tx_jump_ind", "tok_jump_ind", senderWallet.getPublicKey(), wallet.getPublicKey(), 
                                         startTime, "n_jump_ind", "");
        signTransaction(tx, senderWallet);

        // 2. Receive transaction - enters Phase 3 (DELAYING) synchronously.
        manager.onTransactionReceived(tx);
        
        // Initial state check
        assertEquals("Transaction should be in DELAYING phase after passing initial checks", 
                     TransactionPhase.DELAYING, tx.getPhase());

        // 3. SIMULATE EXPIRATION:
        // Advance monotonic time so the Looper triggers the delayed commit runnable.
        ShadowSystemClock.advanceBy(10, TimeUnit.SECONDS);
        // Manually set the transaction timestamp back in time to ensure it looks expired.
        tx.setTimestamp(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2));
        
        // 4. Process the looper.
        ShadowLooper.idleMainLooper();

        // 5. Verification: The re-verification in CommitPhaseHandler must have aborted the tx.
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
        
        String tokenId = "tok_limit_ind_" + System.nanoTime();
        WalletInterface.TokenInfo t1 = new WalletInterface.TokenInfo(tokenId, 100, System.currentTimeMillis() + 60000);
        wallet.tokenToReturn = t1;

        // 1st preparation succeeds and locks the token
        prepareHandler.execute("rec_pub", 100, true, mock(PreparePhaseHandler.PrepareListener.class));
        assertTrue("Token 1 must be locked", wallet.isTokenLocked(tokenId));

        // 2nd preparation with same token fails because wallet.lockToken returns false
        final String[] failure = {null};
        prepareHandler.execute("rec_pub", 100, true, new PreparePhaseHandler.PrepareListener() {
            @Override public void onPrepareSent(Transaction tx) {}
            @Override public void onPrepareFailed(String r) { failure[0] = r; }
        });

        assertNotNull("Should fail to prepare second transaction with locked token", failure[0]);
        assertEquals("Token lock failed", failure[0]);
    }
}
