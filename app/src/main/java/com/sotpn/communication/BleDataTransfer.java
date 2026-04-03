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

    private BluetoothGatt      gatt;
    private int                currentMtu = BleConstants.DEFAULT_CHUNK_SIZE;

    private final Queue<byte[]> sendQueue   = new LinkedList<>();
    private       boolean       isSending   = false;
    private       String        sendingTxId = null;

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
        sendingTxId = tx.getTxId();
        try {
            String json = tx.toJson().toString();
            enqueueChunks(json.getBytes(StandardCharsets.UTF_8),
                    BleConstants.TX_CHARACTERISTIC_UUID.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialise transaction", e);
            callback.onTransactionSendFailed(tx.getTxId(), e.getMessage());
        }
    }

    public void sendAck(TransactionAck ack) {
        try {
            String json = ack.toJson().toString();
            enqueueChunks(json.getBytes(StandardCharsets.UTF_8),
                    BleConstants.ACK_CHARACTERISTIC_UUID.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialise ACK", e);
            callback.onAckSendFailed(ack.getTxId(), e.getMessage());
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

        if (!isSending) {
            writeNextChunk();
        }
    }

    private void writeNextChunk() {
        if (sendQueue.isEmpty()) {
            isSending = false;
            final String txId = sendingTxId;
            if (txId != null) {
                mainHandler.post(() -> callback.onTransactionSent(txId));
            }
            sendingTxId = null;
            return;
        }

        isSending = true;
        byte[] chunk = sendQueue.poll();

        BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
        if (service == null) {
            callback.onBleError("SOTPN service not found");
            return;
        }

        BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(BleConstants.TX_CHARACTERISTIC_UUID);
        if (characteristic == null) {
            callback.onBleError("TX characteristic not found");
            return;
        }

        characteristic.setValue(chunk);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(characteristic);
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
                mainHandler.post(() -> callback.onGossipReceived(json, fromMac));
                return;
            }

            JSONObject obj = new JSONObject(json);

            if (obj.has("status")) {
                // It's a TransactionAck
                TransactionAck ack = TransactionAck.fromJson(obj);
                mainHandler.post(() -> callback.onAckReceived(ack));
            } else if (obj.has("tokenId")) {
                // It's a Transaction
                Transaction tx = Transaction.fromJson(obj);
                mainHandler.post(() -> callback.onTransactionReceived(tx, fromMac));
            } else {
                // Fallback to gossip
                mainHandler.post(() -> callback.onGossipReceived(json, fromMac));
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
                mainHandler.post(() -> callback.onDisconnected(mac, "Disconnected"));
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu - BleConstants.ATT_OVERHEAD;
            } else {
                currentMtu = BleConstants.DEFAULT_CHUNK_SIZE;
            }
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String mac = g.getDevice().getAddress();
                mainHandler.post(() -> callback.onConnected(mac, currentMtu));
            } else {
                callback.onBleError("Service discovery failed");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeNextChunk();
            } else {
                isSending = false;
                if (sendingTxId != null) {
                    callback.onTransactionSendFailed(sendingTxId, "GATT write error");
                }
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
                                      int status) {}
    };
}
