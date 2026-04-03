package com.sotpn.communication;

import java.util.UUID;

/**
 * All BLE UUIDs and protocol-level constants for SOTPN.
 *
 * Every SOTPN device advertises SERVICE_UUID so peers can identify each other.
 * TX_CHARACTERISTIC  → sender writes transaction bytes to this
 * ACK_CHARACTERISTIC → receiver writes ACK bytes back on this
 * GOSSIP_CHARACTERISTIC → broadcast "token seen" messages
 */
public final class BleConstants {

    private BleConstants() {}

    // -----------------------------------------------------------------------
    // GATT Service & Characteristic UUIDs  (custom 128-bit, SOTPN-specific)
    // -----------------------------------------------------------------------

    /** Primary SOTPN service — advertised so peers can filter non-SOTPN devices */
    public static final UUID SERVICE_UUID =
            UUID.fromString("12345678-1234-1234-1234-1234567890AB");

    /** Receiver exposes this; sender writes raw transaction JSON chunks here */
    public static final UUID TX_CHARACTERISTIC_UUID =
            UUID.fromString("12345678-1234-1234-1234-1234567890CD");

    /** Sender exposes this; receiver writes the signed ACK back here */
    public static final UUID ACK_CHARACTERISTIC_UUID =
            UUID.fromString("12345678-1234-1234-1234-1234567890EF");

    /** Both sides expose this; gossip "token seen" broadcasts are written here */
    public static final UUID GOSSIP_CHARACTERISTIC_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789012");

    /** Client Characteristic Configuration Descriptor — required for notifications */
    public static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // -----------------------------------------------------------------------
    // MTU & chunking
    // -----------------------------------------------------------------------

    /**
     * Default BLE MTU is 23 bytes (3 bytes overhead → 20 usable).
     * We request a larger MTU; if granted we use it.
     * Chunk size is always MTU - 3 to leave room for ATT overhead.
     */
    public static final int DEFAULT_MTU         = 23;
    public static final int REQUESTED_MTU        = 512;
    public static final int ATT_OVERHEAD         = 3;
    public static final int DEFAULT_CHUNK_SIZE   = DEFAULT_MTU  - ATT_OVERHEAD; // 20 bytes
    public static final int MAX_CHUNK_SIZE       = REQUESTED_MTU - ATT_OVERHEAD; // 509 bytes

    // -----------------------------------------------------------------------
    // Packet framing — first byte of every chunk signals its role
    // -----------------------------------------------------------------------

    /** First chunk of a multi-chunk message */
    public static final byte FRAME_START  = 0x01;
    /** Middle chunk */
    public static final byte FRAME_CONT   = 0x02;
    /** Final chunk — reassemble after receiving this */
    public static final byte FRAME_END    = 0x03;
    /** Message fits in a single chunk */
    public static final byte FRAME_SINGLE = 0x04;

    // -----------------------------------------------------------------------
    // Timeouts (ms)
    // -----------------------------------------------------------------------

    /** How long to scan for peers before reporting results */
    public static final long SCAN_DURATION_MS       = 10_000;

    /** Max time to wait for GATT connection to be established */
    public static final long CONNECT_TIMEOUT_MS     = 8_000;

    /** Max time to wait for MTU negotiation response */
    public static final long MTU_TIMEOUT_MS         = 3_000;

    /** Max time to wait for a single chunk write to complete */
    public static final long WRITE_TIMEOUT_MS       = 3_000;

    /** Max time to wait for a full ACK from receiver */
    public static final long ACK_TIMEOUT_MS         = 15_000;

    // -----------------------------------------------------------------------
    // Advertising
    // -----------------------------------------------------------------------

    /** BLE advertise TX power — MEDIUM balances range vs battery */
    public static final int  ADVERTISE_TX_POWER     = 0; // AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM = 0

    /** Name prefix embedded in the advertise record for quick identification */
    public static final String DEVICE_NAME_PREFIX   = "SOTPN_";
}
