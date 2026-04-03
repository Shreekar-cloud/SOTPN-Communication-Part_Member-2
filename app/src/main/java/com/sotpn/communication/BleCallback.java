package com.sotpn.communication;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

/**
 * Event interface implemented by TransactionManager (and anything else
 * that needs to react to BLE events).
 *
 * All callbacks are delivered on the main thread unless noted otherwise.
 */
public interface BleCallback {

    // -----------------------------------------------------------------------
    // Discovery
    // -----------------------------------------------------------------------

    /**
     * A nearby SOTPN device was found during scanning.
     * @param device Info about the discovered peer.
     */
    void onDeviceDiscovered(BleDeviceInfo device);

    /**
     * A previously discovered device is no longer seen.
     * @param macAddress MAC address of the lost device.
     */
    void onDeviceLost(String macAddress);

    // -----------------------------------------------------------------------
    // Connection
    // -----------------------------------------------------------------------

    /**
     * Successfully connected + MTU negotiated with a peer.
     * @param macAddress Target device MAC.
     * @param negotiatedMtu Actual MTU agreed upon (affects chunk size).
     */
    void onConnected(String macAddress, int negotiatedMtu);

    /**
     * Disconnected from a peer (cleanly or due to error).
     * @param macAddress Target device MAC.
     * @param reason     Human-readable disconnect reason.
     */
    void onDisconnected(String macAddress, String reason);

    // -----------------------------------------------------------------------
    // Sending (Sender side)
    // -----------------------------------------------------------------------

    /**
     * A transaction was fully sent and all chunks were acknowledged by GATT.
     * (Does NOT mean the receiver has validated it yet — that comes via ACK.)
     * @param txId Transaction ID that was sent.
     */
    void onTransactionSent(String txId);

    /**
     * Transaction send failed mid-way (connection dropped, write error, etc.).
     * @param txId    Transaction ID.
     * @param reason  What went wrong.
     */
    void onTransactionSendFailed(String txId, String reason);

    // -----------------------------------------------------------------------
    // Receiving (Receiver side)
    // -----------------------------------------------------------------------

    /**
     * A complete transaction has been received from a peer.
     * The TransactionEngine should now run Phase 2 (validation).
     * @param transaction The fully reassembled transaction.
     * @param senderMac   MAC address of the sender device.
     */
    void onTransactionReceived(Transaction transaction, String senderMac);

    /**
     * A signed ACK has been received from the receiver.
     * The sender's TransactionEngine can now run Phase 4 (commit).
     * @param ack The ACK object.
     */
    void onAckReceived(TransactionAck ack);

    /**
     * An ACK send failed (receiver side sending back to sender).
     * @param txId   Transaction this ACK was for.
     * @param reason What went wrong.
     */
    void onAckSendFailed(String txId, String reason);

    // -----------------------------------------------------------------------
    // Gossip
    // -----------------------------------------------------------------------

    /**
     * A gossip message was received from a nearby device.
     * Format: "TOKEN_SEEN:<tokenId>:<senderMac>:<timestamp>"
     * The adaptive delay logic uses this to detect potential double-spend.
     * @param gossipMessage Raw gossip string.
     * @param fromMac       Device that relayed this gossip.
     */
    void onGossipReceived(String gossipMessage, String fromMac);

    // -----------------------------------------------------------------------
    // General errors
    // -----------------------------------------------------------------------

    /**
     * BLE is not available or permission was denied.
     * @param reason  Description of the problem.
     */
    void onBleError(String reason);
}