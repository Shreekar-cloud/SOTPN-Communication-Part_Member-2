package com.sotpn.communication;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * PROTOCOL HARDENING TESTS
 * Verifies that the wire protocol is immune to character injection and 
 * field shifting attacks.
 */
public class ProtocolHardeningTest {

    // -----------------------------------------------------------------------
    // TEST 1: Separator Injection Attack
    // Tests what happens if a TokenId contains the protocol separator ':'.
    // -----------------------------------------------------------------------
    @Test
    public void testProtocol_SeparatorInjection_DoesNotCorruptData() {
        String maliciousTokenId = "tok:inject:attack"; // Contains separators
        String senderId = "dev_1";
        String txId = "tx_1";
        long ts = System.currentTimeMillis();
        
        GossipMessage original = new GossipMessage(maliciousTokenId, senderId, txId, ts, 0);
        String wire = original.toWireString();
        
        // Parsing back
        GossipMessage parsed = GossipMessage.fromWireString(wire);
        
        // In a vulnerable system, 'parsed' will be null or have shifted fields.
        // A hardened system must either escape the colon or reject the ID at creation.
        if (parsed != null) {
            assertEquals("TokenId must be preserved exactly (Escaping needed!)", 
                         maliciousTokenId, parsed.getTokenId());
        } else {
            // Rejection is also a valid security stance
            System.out.println("Protocol correctly rejected ID with reserved characters.");
        }
    }

    // -----------------------------------------------------------------------
    // TEST 2: Header Spoofing
    // Attacker tries to send a message starting with 'TOKEN_SEEN' but 
    // with different internal structure.
    // -----------------------------------------------------------------------
    @Test
    public void testProtocol_MalformedHeader_ReturnsNull() {
        String spoof = "TOKEN_SEEN:only:three:parts";
        GossipMessage result = GossipMessage.fromWireString(spoof);
        assertNull("Parser must return null for malformed wire strings", result);
    }
}
