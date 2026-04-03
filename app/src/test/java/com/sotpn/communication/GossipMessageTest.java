package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : GossipMessageTest.java
 * Package  : com.sotpn.communication (test)
 * Step     : Testing - Gossip Message
 *
 * HOW TO RUN:
 *   Place in: app/src/test/java/com/sotpn/communication/
 *   Right-click → Run 'GossipMessageTest'
 *   No phone needed — pure Java logic.
 */
public class GossipMessageTest {

    private GossipMessage message;

    private static final String TOKEN_ID  = "tok_abc123";
    private static final String SENDER_ID = "device_XYZ";
    private static final String TX_ID     = "tx_999";
    private static final long   TIMESTAMP = 1712345678900L;
    private static final int    HOP_COUNT = 0;

    @Before
    public void setUp() {
        message = new GossipMessage(TOKEN_ID, SENDER_ID, TX_ID, TIMESTAMP, HOP_COUNT);
    }

    // -----------------------------------------------------------------------
    // Test 1: Wire format structure
    // -----------------------------------------------------------------------
    @Test
    public void testWireFormat_containsAllFields() {
        String wire = message.toWireString();
        assertTrue("Wire must contain TOKEN_SEEN prefix",
                wire.startsWith("TOKEN_SEEN"));
        assertTrue("Wire must contain tokenId",     wire.contains(TOKEN_ID));
        assertTrue("Wire must contain senderId",    wire.contains(SENDER_ID));
        assertTrue("Wire must contain txId",        wire.contains(TX_ID));
        assertTrue("Wire must contain timestamp",   wire.contains(String.valueOf(TIMESTAMP)));
        assertTrue("Wire must contain hop count",   wire.contains(String.valueOf(HOP_COUNT)));
    }

    // -----------------------------------------------------------------------
    // Test 2: Serialization round-trip
    // -----------------------------------------------------------------------
    @Test
    public void testSerialization_roundTrip() {
        String wire   = message.toWireString();
        GossipMessage parsed = GossipMessage.fromWireString(wire);

        assertNotNull("Parsed message should not be null", parsed);
        assertEquals("tokenId must match",   TOKEN_ID,  parsed.getTokenId());
        assertEquals("senderId must match",  SENDER_ID, parsed.getSenderDeviceId());
        assertEquals("txId must match",      TX_ID,     parsed.getTxId());
        assertEquals("timestamp must match", TIMESTAMP, parsed.getTimestampMs());
        assertEquals("hopCount must match",  HOP_COUNT, parsed.getHopCount());
    }

    // -----------------------------------------------------------------------
    // Test 3: Null input returns null
    // -----------------------------------------------------------------------
    @Test
    public void testFromWireString_nullInput_returnsNull() {
        GossipMessage result = GossipMessage.fromWireString(null);
        assertNull("Null input should return null", result);
    }

    // -----------------------------------------------------------------------
    // Test 4: Empty string returns null
    // -----------------------------------------------------------------------
    @Test
    public void testFromWireString_emptyString_returnsNull() {
        GossipMessage result = GossipMessage.fromWireString("");
        assertNull("Empty string should return null", result);
    }

    // -----------------------------------------------------------------------
    // Test 5: Malformed string returns null
    // -----------------------------------------------------------------------
    @Test
    public void testFromWireString_malformed_returnsNull() {
        GossipMessage result = GossipMessage.fromWireString("RANDOM_GARBAGE");
        assertNull("Malformed string should return null", result);
    }

    // -----------------------------------------------------------------------
    // Test 6: Incomplete fields returns null
    // -----------------------------------------------------------------------
    @Test
    public void testFromWireString_incompleteFields_returnsNull() {
        // Only 3 parts instead of 6
        GossipMessage result = GossipMessage.fromWireString("TOKEN_SEEN:tok:device");
        assertNull("Incomplete fields should return null", result);
    }

    // -----------------------------------------------------------------------
    // Test 7: Hop increment
    // -----------------------------------------------------------------------
    @Test
    public void testWithIncrementedHop_incrementsByOne() {
        GossipMessage relayed = message.withIncrementedHop();
        assertEquals("Hop count should be incremented by 1",
                HOP_COUNT + 1, relayed.getHopCount());
    }

    // -----------------------------------------------------------------------
    // Test 8: Original message unchanged after hop increment
    // -----------------------------------------------------------------------
    @Test
    public void testWithIncrementedHop_originalUnchanged() {
        message.withIncrementedHop();
        assertEquals("Original hop count should not change",
                HOP_COUNT, message.getHopCount());
    }

    // -----------------------------------------------------------------------
    // Test 9: isExpired returns false before max hops
    // -----------------------------------------------------------------------
    @Test
    public void testIsExpired_belowMaxHops_returnsFalse() {
        GossipMessage hop1 = message.withIncrementedHop(); // hop = 1
        GossipMessage hop2 = hop1.withIncrementedHop();    // hop = 2
        assertFalse("hop=2 should NOT be expired (max=" +
                GossipMessage.MAX_HOPS + ")", hop2.isExpired());
    }

    // -----------------------------------------------------------------------
    // Test 10: isExpired returns true at max hops
    // -----------------------------------------------------------------------
    @Test
    public void testIsExpired_atMaxHops_returnsTrue() {
        GossipMessage atMax = new GossipMessage(
                TOKEN_ID, SENDER_ID, TX_ID, TIMESTAMP,
                GossipMessage.MAX_HOPS);
        assertTrue("Message at MAX_HOPS should be expired", atMax.isExpired());
    }
}