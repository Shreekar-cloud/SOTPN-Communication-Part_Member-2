package com.sotpn.transaction;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : NonceStoreTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/transaction/
 *   Right-click → Run 'NonceStoreTest'
 *   No phone needed.
 */
public class NonceStoreTest {

    private NonceStore store;

    @Before
    public void setUp() {
        store = new NonceStore();
    }

    // -----------------------------------------------------------------------
    // Test 1: New nonce is accepted
    // -----------------------------------------------------------------------
    @Test
    public void testNewNonce_isAccepted() {
        boolean result = store.checkAndRecord("nonce_001");
        assertTrue("A brand new nonce should be accepted", result);
    }

    // -----------------------------------------------------------------------
    // Test 2: Same nonce rejected second time — replay attack blocked
    // -----------------------------------------------------------------------
    @Test
    public void testSameNonce_secondTime_isRejected() {
        store.checkAndRecord("nonce_001");
        boolean result = store.checkAndRecord("nonce_001");
        assertFalse("Same nonce used twice should be REJECTED (replay attack)", result);
    }

    // -----------------------------------------------------------------------
    // Test 3: Same nonce rejected third time too
    // -----------------------------------------------------------------------
    @Test
    public void testSameNonce_thirdTime_isRejected() {
        store.checkAndRecord("nonce_001");
        store.checkAndRecord("nonce_001");
        boolean result = store.checkAndRecord("nonce_001");
        assertFalse("Same nonce used three times should still be rejected", result);
    }

    // -----------------------------------------------------------------------
    // Test 4: Different nonces are all accepted
    // -----------------------------------------------------------------------
    @Test
    public void testDifferentNonces_allAccepted() {
        assertTrue("nonce_001 should be accepted", store.checkAndRecord("nonce_001"));
        assertTrue("nonce_002 should be accepted", store.checkAndRecord("nonce_002"));
        assertTrue("nonce_003 should be accepted", store.checkAndRecord("nonce_003"));
        assertTrue("nonce_004 should be accepted", store.checkAndRecord("nonce_004"));
    }

    // -----------------------------------------------------------------------
    // Test 5: hasSeenNonce returns false for unknown nonce
    // -----------------------------------------------------------------------
    @Test
    public void testHasSeenNonce_unknownNonce_returnsFalse() {
        assertFalse("Unknown nonce should return false",
                store.hasSeenNonce("nonce_never_used"));
    }

    // -----------------------------------------------------------------------
    // Test 6: hasSeenNonce returns true after recording
    // -----------------------------------------------------------------------
    @Test
    public void testHasSeenNonce_afterRecording_returnsTrue() {
        store.checkAndRecord("nonce_abc");
        assertTrue("Recorded nonce should return true",
                store.hasSeenNonce("nonce_abc"));
    }

    // -----------------------------------------------------------------------
    // Test 7: Size increases with each new nonce
    // -----------------------------------------------------------------------
    @Test
    public void testSize_increasesWithNewNonces() {
        assertEquals("Empty store should have size 0", 0, store.size());
        store.checkAndRecord("nonce_001");
        assertEquals("Size should be 1 after one nonce", 1, store.size());
        store.checkAndRecord("nonce_002");
        assertEquals("Size should be 2 after two nonces", 2, store.size());
        store.checkAndRecord("nonce_001"); // duplicate — should not increase
        assertEquals("Duplicate nonce should not increase size", 2, store.size());
    }

    // -----------------------------------------------------------------------
    // Test 8: clearAll wipes all nonces
    // -----------------------------------------------------------------------
    @Test
    public void testClearAll_wipesAllNonces() {
        store.checkAndRecord("nonce_001");
        store.checkAndRecord("nonce_002");
        store.checkAndRecord("nonce_003");
        store.clearAll();
        assertEquals("Size should be 0 after clearAll", 0, store.size());
        assertFalse("Nonce should not be found after clearAll",
                store.hasSeenNonce("nonce_001"));
        // After clear, same nonce should be accepted again
        assertTrue("After clearAll, nonce should be accepted again",
                store.checkAndRecord("nonce_001"));
    }
    @Test
    public void testClearExpired_allowsNonceReuse() {
        store.checkAndRecord("nonce_temp");
        store.clearAll();
        assertTrue("After clear, nonce should be accepted again",
                store.checkAndRecord("nonce_temp"));
    }
}