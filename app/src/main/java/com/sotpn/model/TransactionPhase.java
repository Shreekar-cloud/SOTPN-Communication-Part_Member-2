package com.sotpn.model;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : TransactionPhase.java
 * Package  : com.sotpn.model
 * Step     : Step 2 - Transaction Model
 * Status   : Complete
 */
public enum TransactionPhase {
    PREPARE,      // Phase 1 — sender preparing
    VALIDATING,   // Phase 2 — receiver validating
    DELAYING,     // Phase 3 — adaptive delay window
    COMMITTING,   // Phase 4 — commit/ACK exchange
    FINALIZED,    // Terminal — success
    FAILED        // Terminal — rejected/error
}
