package com.sotpn.transaction;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : ValidationResult.java
 * Package  : com.sotpn.transaction
 * Step     : Step 5 - Validation Phase Handler
 * Status   : Complete
 *
 * Depends on  : Nothing
 * Used by     : ValidationPhaseHandler, TransactionManager
 *
 * -----------------------------------------------------------------------
 * Represents the outcome of Phase 2 validation.
 *
 * If valid    → TransactionManager moves to Phase 3 (Adaptive Delay)
 * If invalid  → TransactionManager aborts and notifies sender
 *
 * FAILURE CODES — tell us exactly which check failed:
 *
 *   INVALID_SIGNATURE  → signature doesn't match sender's public key
 *   TOKEN_EXPIRED      → token's expiry time has passed
 *   NONCE_REPLAY       → this nonce was used in a previous transaction
 *   GOSSIP_CONFLICT    → same token seen in another transaction nearby
 *   MISSING_FIELDS     → transaction JSON is incomplete / malformed
 */
public class ValidationResult {

    // -----------------------------------------------------------------------
    // Static factories — clean way to create results
    // -----------------------------------------------------------------------

    /** Create a passing result */
    public static ValidationResult pass() {
        return new ValidationResult(true, FailureCode.NONE, null);
    }

    /** Create a failing result with a specific code and message */
    public static ValidationResult fail(FailureCode code, String message) {
        return new ValidationResult(false, code, message);
    }

    // -----------------------------------------------------------------------
    // Failure codes
    // -----------------------------------------------------------------------

    public enum FailureCode {
        NONE,               // no failure (used in passing results)
        INVALID_SIGNATURE,  // cryptographic signature check failed
        TOKEN_EXPIRED,      // token's expiry timestamp is in the past
        NONCE_REPLAY,       // this nonce was already used → replay attack
        GOSSIP_CONFLICT,    // same token seen in a different transaction
        MISSING_FIELDS,     // required transaction fields are null/empty
        AMOUNT_EXCEEDED,    // amount exceeds ₹500 offline limit
        UNKNOWN             // catch-all for unexpected errors
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final boolean     isValid;
    private final FailureCode failureCode;
    private final String      failureMessage;

    private ValidationResult(boolean isValid, FailureCode failureCode,
                             String failureMessage) {
        this.isValid        = isValid;
        this.failureCode    = failureCode;
        this.failureMessage = failureMessage;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /** True if all checks passed — safe to proceed to Phase 3 */
    public boolean isValid()            { return isValid; }

    /** Which specific check failed (NONE if isValid == true) */
    public FailureCode getFailureCode() { return failureCode; }

    /** Human-readable explanation of the failure */
    public String getFailureMessage()   { return failureMessage; }

    @Override
    public String toString() {
        if (isValid) return "ValidationResult{PASS}";
        return "ValidationResult{FAIL, code=" + failureCode
                + ", msg=" + failureMessage + "}";
    }
}
