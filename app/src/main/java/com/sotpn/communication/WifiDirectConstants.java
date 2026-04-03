package com.sotpn.communication;

/**
 * Configuration constants for the SOTPN Wi-Fi Direct layer.
 */
public final class WifiDirectConstants {

    private WifiDirectConstants() {}

    public static final int TRANSACTION_PORT = 8988;
    public static final int ACK_PORT = 8989;
    public static final int GOSSIP_PORT = 8990;

    public static final int DISCOVERY_TIMEOUT_MS = 10_000;
    public static final int CONNECTION_TIMEOUT_MS = 15_000;
    public static final int SERVER_SOCKET_TIMEOUT_MS = 20_000;
    public static final int CLIENT_CONNECT_TIMEOUT_MS = 10_000;
    public static final int SOCKET_READ_TIMEOUT_MS = 10_000;

    public static final int BUFFER_SIZE = 4096;
    public static final int LENGTH_PREFIX_BYTES = 4;

    public static final String MSG_TYPE_TRANSACTION = "TX:";
    public static final String MSG_TYPE_ACK         = "ACK:";
    public static final String MSG_TYPE_GOSSIP       = "GOSSIP:";

    public static final String SERVICE_TYPE         = "_sotpn._tcp";
    public static final String SERVICE_KEY          = "sotpn";
    public static final String SERVICE_VALUE        = "1";
}
