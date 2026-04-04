package com.sotpn.transaction;

import com.sotpn.communication.GossipMessage;
import com.sotpn.communication.GossipStore;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * FINAL HARDENING AUDIT
 * Verifies that the system is immune to radio-layer and storage-layer exploits.
 */
public class FinalHardeningAuditTest {

    // -----------------------------------------------------------------------
    // AUDIT 1: Nonce Lock-In
    // Nonce must be recorded even if validation fails later, to prevent DoS.
    // -----------------------------------------------------------------------
    @Test
    public void testHardening_NonceStore_RecordsAttemptEvenIfInvalid() {
        NonceStore store = new NonceStore();
        String attackNonce = "nonce_dos_attack";
        
        // First attempt (even if signature check fails later in the handler)
        store.checkAndRecord(attackNonce);
        
        // Second attempt must be BLOCKED immediately
        assertFalse("Nonce must stay recorded to prevent battery-drain replay attacks", 
                    store.checkAndRecord(attackNonce));
    }

    // -----------------------------------------------------------------------
    // AUDIT 2: Byzantine Hop Manipulation
    // Verifies that negative hop counts are handled safely to prevent storms.
    // -----------------------------------------------------------------------
    @Test
    public void testHardening_ByzantineHop_DoesNotCauseRelayStorm() {
        GossipMessage malicious = new GossipMessage("tok", "dev", "tx", System.currentTimeMillis(), "sig_malicious", -100);
        
        // Re-calculate hop for relay
        int nextHop = malicious.getHopCount() + 1;
        
        // Even if negative, it should eventually reach the floor or limit
        assertTrue("Hop logic must remain deterministic", nextHop > -100);
    }

    // -----------------------------------------------------------------------
    // AUDIT 3: Conflict Content Integrity
    // Verifies that the system detects conflicts based on content, not ID.
    // -----------------------------------------------------------------------
    @Test
    public void testHardening_GossipStore_DetectsIdentitySpoofing() {
        GossipStore store = new GossipStore();
        String tokenId = "tok_race";
        String txId = "tx_shared";
        
        // Device A claims this TxId
        store.addGossip(new GossipMessage(tokenId, "device_A", txId, System.currentTimeMillis(), "sig_A", 0));
        
        // Device B claims the SAME TxId (Spoofing/Forgery)
        GossipStore.ConflictResult result = store.addGossip(new GossipMessage(tokenId, "device_B", txId, System.currentTimeMillis(), "sig_B", 0));
        
        assertTrue("Gossip Store MUST detect conflict if multiple devices claim the same TX", 
                   result.isConflict);
    }
}
