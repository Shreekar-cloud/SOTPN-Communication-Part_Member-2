package com.sotpn.transaction;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : ValidationResultTest.java
 * Package  : com.sotpn.transaction (test)
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/transaction/
 *   Right-click → Run 'ValidationResultTest'
 *   No phone needed.
 */
public class ValidationResultTest {

    // -----------------------------------------------------------------------
    // Test 1: pass() returns valid result
    // -----------------------------------------------------------------------
    @Test
    public void testPass_isValid() {
        ValidationResult result = ValidationResult.pass();
        assertTrue("pass() should return a valid result", result.isValid());
    }

    // -----------------------------------------------------------------------
    // Test 2: pass() has no failure code
    // -----------------------------------------------------------------------
    @Test
    public void testPass_hasNoneFailureCode() {
        ValidationResult result = ValidationResult.pass();
        assertEquals("pass() should have NONE failure code",
                ValidationResult.FailureCode.NONE, result.getFailureCode());
    }

    // -----------------------------------------------------------------------
    // Test 3: pass() has null failure message
    // -----------------------------------------------------------------------
    @Test
    public void testPass_hasNullMessage() {
        ValidationResult result = ValidationResult.pass();
        assertNull("pass() should have null failure message",
                result.getFailureMessage());
    }

    // -----------------------------------------------------------------------
    // Test 4: fail() returns invalid result
    // -----------------------------------------------------------------------
    @Test
    public void testFail_isNotValid() {
        ValidationResult result = ValidationResult.fail(
                ValidationResult.FailureCode.INVALID_SIGNATURE,
                "Bad signature");
        assertFalse("fail() should return an invalid result", result.isValid());
    }

    // -----------------------------------------------------------------------
    // Test 5: fail() stores correct failure code and message
    // -----------------------------------------------------------------------
    @Test
    public void testFail_storesCorrectCodeAndMessage() {
        String msg = "Token has expired";
        ValidationResult result = ValidationResult.fail(
                ValidationResult.FailureCode.TOKEN_EXPIRED, msg);
        assertEquals("Failure code must match",
                ValidationResult.FailureCode.TOKEN_EXPIRED,
                result.getFailureCode());
        assertEquals("Failure message must match", msg, result.getFailureMessage());
    }

    // -----------------------------------------------------------------------
    // Test 6: All failure codes are distinct
    // -----------------------------------------------------------------------
    @Test
    public void testAllFailureCodes_areDistinct() {
        ValidationResult sig     = ValidationResult.fail(
                ValidationResult.FailureCode.INVALID_SIGNATURE, "sig");
        ValidationResult expired = ValidationResult.fail(
                ValidationResult.FailureCode.TOKEN_EXPIRED, "exp");
        ValidationResult replay  = ValidationResult.fail(
                ValidationResult.FailureCode.NONCE_REPLAY, "replay");
        ValidationResult gossip  = ValidationResult.fail(
                ValidationResult.FailureCode.GOSSIP_CONFLICT, "gossip");
        ValidationResult fields  = ValidationResult.fail(
                ValidationResult.FailureCode.MISSING_FIELDS, "fields");

        assertNotEquals("INVALID_SIGNATURE != TOKEN_EXPIRED",
                sig.getFailureCode(), expired.getFailureCode());
        assertNotEquals("TOKEN_EXPIRED != NONCE_REPLAY",
                expired.getFailureCode(), replay.getFailureCode());
        assertNotEquals("NONCE_REPLAY != GOSSIP_CONFLICT",
                replay.getFailureCode(), gossip.getFailureCode());
        assertNotEquals("GOSSIP_CONFLICT != MISSING_FIELDS",
                gossip.getFailureCode(), fields.getFailureCode());
    }
}