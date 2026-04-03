package com.sotpn.model;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 */
public enum TransactionPhase {
    PREPARE,      // Phase 1 — sender preparing
    VALIDATING,   // Phase 2 — receiver validating
    DELAYING,     // Phase 3 — adaptive delay window
    COMMITTING,   // Phase 4 — commit/ACK exchange
    FINALIZED,    // Terminal — success
    FAILED        // Terminal — rejected/error
}
