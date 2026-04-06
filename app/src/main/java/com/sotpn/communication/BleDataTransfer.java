package com.sotpn.communication;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Handles chunked GATT communication between two SOTPN devices.
 */
public class BleDataTransfer {

    private static final String TAG = "BleDataTransfer";

    private final Context      context;
    private final BleCallback  callback;
    private final Handler      mainHandler;

    private volatile BluetoothGatt      gatt;
    private volatile BluetoothGattService cachedService;
    private volatile BluetoothGattCharacteristic cachedTxChar;
    private int                currentMtu = BleConstants.DEFAULT_CHUNK_SIZE;

    private final Queue<byte[]> sendQueue   = new LinkedList<>();
    private       boolean       isSending   = false;
    private       String        sendingTxId = null;
    private       Transaction   pendingTransaction = null;
    private       Runnable      sendWatchdogRunnable = null;
    private       int           totalChunksToSend = 0;
    private       int           chunksSentCount = 0;

    private       byte[]        receiveBuffer = new byte[0];

    public BleDataTransfer(Context context, BleCallback callback) {
        this.context     = context;
        this.callback    = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void connect(BluetoothDevice device) {
        Log.d(TAG, "Connecting to " + device.getAddress());
        gatt = device.connectGatt(context, false, gattCallback);
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
        }
    }

    public void sendTransaction(Transaction tx) {
        if (cachedTxChar == null) {
            Log.i(TAG, "Queueing transaction: SOTPN service not discovered yet");
            pendingTransaction = tx;
            Toast.makeText(context, "SOTPN: Waiting for peer to initialize...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        sendingTxId = tx.getTxId();
        try {
            String json = tx.toJson().toString();
            enqueueChunks(json.getBytes(StandardCharsets.UTF_8),
                    BleConstants.TX_CHARACTERISTIC_UUID.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialise transaction", e);
            if (callback != null) callback.onTransactionSendFailed(tx.getTxId(), e.getMessage());
        }
    }

    public void sendAck(TransactionAck ack) {
        try {
            String json = ack.toJson().toString();
            enqueueChunks(json.getBytes(StandardCharsets.UTF_8),
                    BleConstants.ACK_CHARACTERISTIC_UUID.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialise ACK", e);
            if (callback != null) callback.onAckSendFailed(ack.getTxId(), e.getMessage());
        }
    }

    public void sendGossip(String gossipMessage) {
        enqueueChunks(gossipMessage.getBytes(StandardCharsets.UTF_8),
                BleConstants.GOSSIP_CHARACTERISTIC_UUID.toString());
    }

    private void enqueueChunks(byte[] payload, String characteristicUuidStr) {
        int chunkDataSize = currentMtu - 1;
        int totalChunks   = (int) Math.ceil((double) payload.length / chunkDataSize);

        if (totalChunks == 1) {
            byte[] chunk = new byte[payload.length + 1];
            chunk[0] = BleConstants.FRAME_SINGLE;
            System.arraycopy(payload, 0, chunk, 1, payload.length);
            sendQueue.add(chunk);
        } else {
            for (int i = 0; i < totalChunks; i++) {
                int start  = i * chunkDataSize;
                int end    = Math.min(start + chunkDataSize, payload.length);
                int dataLen = end - start;

                byte frameType;
                if      (i == 0)              frameType = BleConstants.FRAME_START;
                else if (i == totalChunks - 1) frameType = BleConstants.FRAME_END;
                else                           frameType = BleConstants.FRAME_CONT;

                byte[] chunk = new byte[dataLen + 1];
                chunk[0] = frameType;
                System.arraycopy(payload, start, chunk, 1, dataLen);
                sendQueue.add(chunk);
            }
        }
        
        totalChunksToSend = sendQueue.size();
        chunksSentCount = 0;
        
        if (!isSending) {
            writeNextChunk();
        }
    }

    private void enableNotifications(BluetoothGatt g) {
        BluetoothGattService service = g.getService(BleConstants.SERVICE_UUID);
        if (service == null) return;

        BluetoothGattCharacteristic ackChar = service.getCharacteristic(BleConstants.ACK_CHARACTERISTIC_UUID);
        if (ackChar != null) {
            g.setCharacteristicNotification(ackChar, true);
            BluetoothGattDescriptor descriptor = ackChar.getDescriptor(BleConstants.CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(descriptor);
                Log.d(TAG, "Subscribed to ACK notifications");
            }
        }

        BluetoothGattCharacteristic gossipChar = service.getCharacteristic(BleConstants.GOSSIP_CHARACTERISTIC_UUID);
        if (gossipChar != null) {
            g.setCharacteristicNotification(gossipChar, true);
        }
    }

    private void writeNextChunk() {
        if (sendQueue.isEmpty()) {
            isSending = false;
            final String txId = sendingTxId;
            if (txId != null) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onTransactionSent(txId);
                });
            }
            sendingTxId = null;
            return;
        }

        isSending = true;
        byte[] chunk = sendQueue.poll();

        if (cachedTxChar == null) {
            if (callback != null) callback.onBleError("SOTPN service not initialized");
            return;
        }

        BluetoothGattCharacteristic characteristic = cachedTxChar;

        characteristic.setValue(chunk);
        // Switch back to DEFAULT (With-Response) for guaranteed hardware delivery
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        
        if (gatt == null) {
            isSending = false;
            return;
        }
        
        boolean started = gatt.writeCharacteristic(characteristic);
        if (!started) {
            Log.e(TAG, "GATT write failed (radio busy). Retrying in 250ms...");
            ((java.util.LinkedList<byte[]>)sendQueue).addFirst(chunk);
            mainHandler.postDelayed(() -> writeNextChunk(), 250);
            return;
        }
        
        startSendWatchdog(chunk);
        chunksSentCount++;
        
        Log.d(TAG, "Writing chunk (" + chunk.length + " bytes) to characteristic. Count: " + chunksSentCount + "/" + totalChunksToSend);

        final int currentCount = chunksSentCount;
        final int totalCount = totalChunksToSend;
        mainHandler.post(() -> Toast.makeText(context, "SOTPN: Sending chunk " + currentCount + " of " + totalCount, Toast.LENGTH_SHORT).show());
    }

    private void startSendWatchdog(byte[] currentChunk) {
        cancelSendWatchdog();
        sendWatchdogRunnable = () -> {
            Log.w(TAG, "Send watchdog fired! No ACK for chunk. Retrying current chunk...");
            ((java.util.LinkedList<byte[]>)sendQueue).addFirst(currentChunk);
            writeNextChunk();
        };
        mainHandler.postDelayed(sendWatchdogRunnable, 2000);
    }

    private void cancelSendWatchdog() {
        if (sendWatchdogRunnable != null) {
            mainHandler.removeCallbacks(sendWatchdogRunnable);
            sendWatchdogRunnable = null;
        }
    }

    private void handleIncomingChunk(byte[] chunk, String fromMac) {
        if (chunk == null || chunk.length == 0) return;

        byte frameType = chunk[0];
        byte[] data    = Arrays.copyOfRange(chunk, 1, chunk.length);

        switch (frameType) {
            case BleConstants.FRAME_SINGLE:
                receiveBuffer = data;
                processReceivedMessage(receiveBuffer, fromMac);
                receiveBuffer = new byte[0];
                break;

            case BleConstants.FRAME_START:
                receiveBuffer = data;
                break;

            case BleConstants.FRAME_CONT:
                receiveBuffer = concat(receiveBuffer, data);
                break;

            case BleConstants.FRAME_END:
                receiveBuffer = concat(receiveBuffer, data);
                processReceivedMessage(receiveBuffer, fromMac);
                receiveBuffer = new byte[0];
                break;
        }
    }

    private void processReceivedMessage(byte[] fullPayload, String fromMac) {
        try {
            String json = new String(fullPayload, StandardCharsets.UTF_8);
            
            if (!json.trim().startsWith("{")) {
                // Treat as gossip (plain string)
                mainHandler.post(() -> {
                    if (callback != null) callback.onGossipReceived(json, fromMac);
                });
                return;
            }

            JSONObject obj = new JSONObject(json);

            if (obj.has("ack_signature")) {
                // It's a TransactionAck
                TransactionAck ack = TransactionAck.fromJson(obj);
                mainHandler.post(() -> {
                    if (callback != null) callback.onAckReceived(ack);
                });
            } else if (obj.has("sender")) {
                // It's a Transaction
                Transaction tx = Transaction.fromJson(obj);
                mainHandler.post(() -> {
                    if (callback != null) callback.onTransactionReceived(tx, fromMac);
                });
            } else {
                // Fallback to gossip
                mainHandler.post(() -> {
                    if (callback != null) callback.onGossipReceived(json, fromMac);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse message", e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt = g;
                gatt.requestMtu(BleConstants.REQUESTED_MTU);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                String mac = g.getDevice().getAddress();
                g.close();
                mainHandler.post(() -> {
                    if (callback != null) callback.onDisconnected(mac, "Disconnected");
                });
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = Math.min(mtu, 60); // INDESTRUCTIBLE MTU: 60
                Log.i(TAG, "MTU Negotiated: " + mtu + ". Using safe cap: " + currentMtu);
            } else {
                currentMtu = BleConstants.DEFAULT_CHUNK_SIZE;
            }
            
            // DELAY BEFORE DISCOVERY: Some phones need time to settle the MTU
            mainHandler.postDelayed(() -> g.discoverServices(), 600);
        }

        private int discoveryRetryCount = 0;

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cachedService = g.getService(BleConstants.SERVICE_UUID);
                if (cachedService == null) {
                    if (discoveryRetryCount < 5) {
                        discoveryRetryCount++;
                        Log.w(TAG, "SOTPN Service not found, retrying discovery (" + discoveryRetryCount + ")...");
                        mainHandler.postDelayed(() -> g.discoverServices(), 2000);
                        return;
                    }
                    Log.e(TAG, "SOTPN Service not found after retries");
                    if (callback != null) callback.onBleError("SOTPN Service not found");
                    return;
                }

                cachedTxChar = cachedService.getCharacteristic(BleConstants.TX_CHARACTERISTIC_UUID);
                if (cachedTxChar == null) {
                    if (callback != null) callback.onBleError("TX characteristic not found");
                    return;
                }

                discoveryRetryCount = 0;
                Log.d(TAG, "Services discovered and cached. Enabling notifications...");
                enableNotifications(g);
                String mac = g.getDevice().getAddress();
                mainHandler.post(() -> {
                    Toast.makeText(context, "SOTPN Service Initialized", Toast.LENGTH_SHORT).show();
                    if (pendingTransaction != null) {
                        Log.i(TAG, "Triggering queued transaction after discovery");
                        sendTransaction(pendingTransaction);
                        pendingTransaction = null;
                    }
                    if (callback != null) callback.onConnected(mac, currentMtu);
                });
            } else {
                if (callback != null) callback.onBleError("Service discovery failed");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g,
                                          BluetoothGattCharacteristic characteristic, int status) {
            cancelSendWatchdog();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // INCREASED BREATHER: 150ms for radio stability
                mainHandler.postDelayed(() -> writeNextChunk(), 150);
            } else {
                Log.e(TAG, "GATT write failed with status: " + status);
                final int finalStatus = status;
                mainHandler.post(() -> android.widget.Toast.makeText(context, "SOTPN: Packet Error " + finalStatus, android.widget.Toast.LENGTH_LONG).show());
                
                // Retry the same chunk after a 200ms breather instead of failing
                mainHandler.postDelayed(() -> writeNextChunk(), 200);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g,
                                            BluetoothGattCharacteristic characteristic) {
            byte[] chunk  = characteristic.getValue();
            String from   = g.getDevice().getAddress();
            handleIncomingChunk(chunk, from);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor,
                                      int status) {
            Log.d(TAG, "Descriptor write status: " + status);
        }
    };
}
