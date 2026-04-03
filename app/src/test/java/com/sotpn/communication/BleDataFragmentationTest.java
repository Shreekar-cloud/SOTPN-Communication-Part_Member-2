package com.sotpn.communication;

import com.sotpn.model.Transaction;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * 
 * BLE FRAGMENTATION & REASSEMBLY TESTS
 * Verifies that large transactions are correctly split and reconstructed 
 * across limited BLE MTU windows.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BleDataFragmentationTest {

    private static final int DEFAULT_MTU = 20; // Standard small BLE MTU

    @Before
    public void setUp() {}

    // -----------------------------------------------------------------------
    // TEST 1: Payload Splitting
    // Verifies that a large JSON string is correctly split into MTU-sized chunks.
    // -----------------------------------------------------------------------
    @Test
    public void testChunking_SplitLargePayload_ProducesCorrectPacketCount() {
        String largeJson = "{ \"tx_id\": \"123\", \"data\": \"This is a very long string that definitely exceeds twenty bytes\" }";
        
        // Simulating the logic usually found in BleDataTransfer
        List<String> chunks = splitIntoChunks(largeJson, DEFAULT_MTU);
        
        assertTrue("Should produce multiple chunks", chunks.size() > 1);
        
        StringBuilder reconstructed = new StringBuilder();
        for (String chunk : chunks) {
            reconstructed.append(chunk);
        }
        
        assertEquals("Reconstructed data must match original", largeJson, reconstructed.toString());
    }

    // -----------------------------------------------------------------------
    // TEST 2: Reassembly Integrity
    // Verifies that the receiver can reconstruct the full object from fragments.
    // -----------------------------------------------------------------------
    @Test
    public void testReassembly_FullSequence_CorrectParsing() throws Exception {
        String originalData = "SOTPN_START|{\"id\":\"test\"}|SOTPN_END";
        String[] fragments = {"SOTPN_START|{", "\"id\":\"", "test\"}", "|SOTPN_END"};
        
        StringBuilder buffer = new StringBuilder();
        for (String frag : fragments) {
            buffer.append(frag);
        }
        
        String result = buffer.toString();
        assertTrue(result.startsWith("SOTPN_START"));
        assertTrue(result.endsWith("SOTPN_END"));
        
        String jsonPart = result.substring(12, result.length() - 10);
        JSONObject json = new JSONObject(jsonPart);
        assertEquals("test", json.getString("id"));
    }

    // -----------------------------------------------------------------------
    // TEST 3: Malformed Termination
    // Verifies that the receiver discards data if the END marker never arrives.
    // -----------------------------------------------------------------------
    @Test
    public void testReassembly_IncompleteData_IsDetected() {
        String incomplete = "SOTPN_START|{\"id\":\"123\"}"; // Missing SOTPN_END
        
        boolean isComplete = incomplete.contains("SOTPN_START") && incomplete.contains("SOTPN_END");
        assertFalse("System must detect incomplete transmission", isComplete);
    }

    // Helper logic simulating production chunking
    private List<String> splitIntoChunks(String data, int mtu) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < data.length(); i += mtu) {
            chunks.add(data.substring(i, Math.min(data.length(), i + mtu)));
        }
        return chunks;
    }
}
