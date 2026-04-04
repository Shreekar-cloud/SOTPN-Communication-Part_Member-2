package com.sotpn.communication;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * PROTOCOL HARDENING TESTS
 */
public class ProtocolHardeningTest {

    private static final String SIG = "sig_hardened";

    @Test
    public void testProtocol_SeparatorInjection_DoesNotCorruptData() {
        String maliciousTokenId = "tok:inject:attack"; 
        String senderId = "dev_1";
        String txId = "tx_1";
        long ts = System.currentTimeMillis();
        
        // Updated to use 6-argument constructor
        GossipMessage original = new GossipMessage(maliciousTokenId, senderId, txId, ts, SIG, 0);
        String wire = original.toWireString();
        
        GossipMessage parsed = GossipMessage.fromWireString(wire);
        
        if (parsed != null) {
            assertEquals("TokenId must be preserved exactly", 
                         maliciousTokenId, parsed.getTokenId());
        }
    }

    @Test
    public void testProtocol_MalformedHeader_ReturnsNull() {
        String spoof = "TOKEN_SEEN:only:three:parts";
        GossipMessage result = GossipMessage.fromWireString(spoof);
        assertNull("Parser must return null for malformed wire strings", result);
    }
}
