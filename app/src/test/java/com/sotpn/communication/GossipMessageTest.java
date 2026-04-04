package com.sotpn.communication;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 */
public class GossipMessageTest {

    private GossipMessage message;

    private static final String TOKEN_ID = "tok_abc123";
    private static final String SENDER_KEY = "device_XYZ";
    private static final String TX_ID = "tx_999";
    private static final long TIMESTAMP = 1712345678900L;
    private static final String SIGNATURE = "sig_valid_123";
    private static final int HOP_COUNT = 0;

    @Before
    public void setUp() {
        // Updated to use 6-argument constructor
        message = new GossipMessage(TOKEN_ID, SENDER_KEY, TX_ID, TIMESTAMP, SIGNATURE, HOP_COUNT);
    }

    @Test
    public void testGetters() {
        assertEquals(TOKEN_ID, message.getTokenId());
        assertEquals(SENDER_KEY, message.getSenderPublicKey());
        assertEquals(TX_ID, message.getTxId());
        assertEquals(TIMESTAMP, message.getTimestampMs());
        assertEquals(SIGNATURE, message.getSignature());
        assertEquals(HOP_COUNT, message.getHopCount());
    }

    @Test
    public void testToWireString() {
        String wire = message.toWireString();
        // TOKEN_SEEN : tokenId : senderPubKey : txId : timestamp : signature : hopCount
        String expected = "TOKEN_SEEN:" + TOKEN_ID + ":" + SENDER_KEY + ":" + TX_ID + ":" + TIMESTAMP + ":" + SIGNATURE + ":" + HOP_COUNT;
        assertEquals(expected, wire);
    }

    @Test
    public void testFromWireString_valid() {
        String wire = message.toWireString();
        GossipMessage parsed = GossipMessage.fromWireString(wire);

        assertNotNull(parsed);
        assertEquals(TOKEN_ID, parsed.getTokenId());
        assertEquals(SENDER_KEY, parsed.getSenderPublicKey());
        assertEquals(TX_ID, parsed.getTxId());
        assertEquals(TIMESTAMP, parsed.getTimestampMs());
        assertEquals(SIGNATURE, parsed.getSignature());
        assertEquals(HOP_COUNT, parsed.getHopCount());
    }

    @Test
    public void testFromWireString_invalid_prefix() {
        assertNull(GossipMessage.fromWireString("BAD_PREFIX:tok:dev:tx:123:sig:0"));
    }

    @Test
    public void testFromWireString_invalid_parts() {
        assertNull(GossipMessage.fromWireString("TOKEN_SEEN:too:few:parts"));
    }

    @Test
    public void testFromWireString_invalid_numbers() {
        assertNull(GossipMessage.fromWireString("TOKEN_SEEN:tok:dev:tx:not_a_number:sig:0"));
        assertNull(GossipMessage.fromWireString("TOKEN_SEEN:tok:dev:tx:123:sig:not_a_number"));
    }

    @Test
    public void testWithIncrementedHop() {
        GossipMessage relayed = message.withIncrementedHop();
        assertEquals(message.getHopCount() + 1, relayed.getHopCount());
        assertEquals(message.getTokenId(), relayed.getTokenId());
        assertEquals(message.getSignature(), relayed.getSignature());
    }

    @Test
    public void testIsExpired() {
        assertFalse(message.isExpired());

        // Updated to use 6-argument constructor
        GossipMessage atMax = new GossipMessage(
                TOKEN_ID, SENDER_KEY, TX_ID, TIMESTAMP, SIGNATURE, GossipMessage.MAX_HOPS);
        assertTrue(atMax.isExpired());
    }

    @Test
    public void testIsAboutToken() {
        assertTrue(message.isAboutToken(TOKEN_ID));
        assertFalse(message.isAboutToken("different_token"));
    }
}
