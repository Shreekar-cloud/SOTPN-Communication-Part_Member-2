package com.sotpn.communication;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

/**
 * GATT Server implementation for SOTPN.
 * The Receiver hosts this server so the Sender can write transactions to it.
 */
@SuppressLint("MissingPermission")
public class BleServer {
    private static final String TAG = "BleServer";

    private final Context context;
    private final BleCallback callback;
    private final Handler mainHandler;

    private BluetoothGattServer gattServer;
    private byte[] receiveBuffer = new byte[0];
    private byte[] lastChunkReceived = null;

    // Notification Queueing
    private final Queue<byte[]> sendQueue = new LinkedList<>();
    private BluetoothDevice connectedDevice;
    private BluetoothGattCharacteristic activeNotifyChar;
    private int incomingChunkCount = 0;
    private boolean isNotifying = false;
    private int currentMtu = BleConstants.DEFAULT_MTU;

    public BleServer(Context context, BleCallback callback) {
        this.context = context;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return;

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.e(TAG, "Failed to open GATT server");
            return;
        }

        setupServices();
        Log.i(TAG, "GATT Server started and SOTPN service added");
    }

    public void stop() {
        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }
    }

    private void setupServices() {
        BluetoothGattService service = new BluetoothGattService(
                BleConstants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // TX Characteristic (Sender writes transaction chunks here)
        BluetoothGattCharacteristic txChar = new BluetoothGattCharacteristic(
                BleConstants.TX_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        // ACK Characteristic (Receiver can write ACKs here, or Sender can read/notify)
        BluetoothGattCharacteristic ackChar = new BluetoothGattCharacteristic(
                BleConstants.ACK_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // Add CCCD Descriptor to ACK characteristic - REQUIRED for notifications
        BluetoothGattDescriptor ackDescriptor = new BluetoothGattDescriptor(
                BleConstants.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        ackChar.addDescriptor(ackDescriptor);

        // Gossip Characteristic
        BluetoothGattCharacteristic gossipChar = new BluetoothGattCharacteristic(
                BleConstants.GOSSIP_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // Add CCCD Descriptor to Gossip characteristic
        BluetoothGattDescriptor gossipDescriptor = new BluetoothGattDescriptor(
                BleConstants.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        gossipChar.addDescriptor(gossipDescriptor);

        service.addCharacteristic(txChar);
        service.addCharacteristic(ackChar);
        service.addCharacteristic(gossipChar);

        gattServer.addService(service);
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d(TAG, "Connection state changed: " + device.getAddress() + " -> " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device;
                receiveBuffer = new byte[0]; // Clear buffer for new session
                mainHandler.post(() -> {
                    Toast.makeText(context, "Sender connected", Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onConnected(device.getAddress(), currentMtu);
                });
            } else {
                connectedDevice = null;
                mainHandler.post(() -> {
                    if (callback != null) callback.onDisconnected(device.getAddress(), "Disconnected");
                });
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.i(TAG, "MTU for " + device.getAddress() + " is " + mtu);
            currentMtu = Math.min(mtu, 60); // INDESTRUCTIBLE CAP: 60 bytes
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            
            if (characteristic.getUuid().equals(BleConstants.TX_CHARACTERISTIC_UUID)) {
                // Clone BEFORE sending response or processing
                byte[] cloned = (value != null) ? value.clone() : null;
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                }
                handleIncomingChunk(cloned, device.getAddress());
            } else if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            
            if (responseNeeded) {
                boolean sent = gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                Log.v(TAG, "Sent GATT Descriptor Response: " + sent);
            }
            Log.d(TAG, "Descriptor write from " + device.getAddress() + " for " + descriptor.getUuid());
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Log.v(TAG, "Notification sent to " + device.getAddress() + ", status: " + status);
            mainHandler.post(() -> notifyNextChunk());
        }
    };

    public void sendAck(TransactionAck ack) {
        try {
            byte[] data = ack.toJson().toString().getBytes(StandardCharsets.UTF_8);
            BluetoothGattService service = gattServer.getService(BleConstants.SERVICE_UUID);
            if (service == null) return;
            BluetoothGattCharacteristic ackChar = service.getCharacteristic(BleConstants.ACK_CHARACTERISTIC_UUID);
            prepareAndSend(ackChar, data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize ACK", e);
        }
    }

    public void sendGossip(String gossip) {
        byte[] data = gossip.getBytes(StandardCharsets.UTF_8);
        BluetoothGattService service = gattServer.getService(BleConstants.SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic gossipChar = service.getCharacteristic(BleConstants.GOSSIP_CHARACTERISTIC_UUID);
        prepareAndSend(gossipChar, data);
    }

    private void prepareAndSend(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (connectedDevice == null) {
            Log.w(TAG, "Cannot send notification: No device connected");
            return;
        }

        activeNotifyChar = characteristic;
        sendQueue.clear();

        int chunkSize = currentMtu - BleConstants.ATT_OVERHEAD;
        if (data.length <= chunkSize) {
            byte[] frame = new byte[data.length + 1];
            frame[0] = BleConstants.FRAME_SINGLE;
            System.arraycopy(data, 0, frame, 1, data.length);
            sendQueue.add(frame);
        } else {
            for (int i = 0; i < data.length; i += chunkSize) {
                int len = Math.min(chunkSize, data.length - i);
                byte[] frame = new byte[len + 1];
                if (i == 0) frame[0] = BleConstants.FRAME_START;
                else if (i + len >= data.length) frame[0] = BleConstants.FRAME_END;
                else frame[0] = BleConstants.FRAME_CONT;
                
                System.arraycopy(data, i, frame, 1, len);
                sendQueue.add(frame);
            }
        }

        if (!isNotifying) {
            notifyNextChunk();
        }
    }

    private void notifyNextChunk() {
        if (sendQueue.isEmpty()) {
            isNotifying = false;
            return;
        }

        isNotifying = true;
        byte[] chunk = sendQueue.poll();
        if (chunk == null) {
            isNotifying = false;
            return;
        }
        
        activeNotifyChar.setValue(chunk);
        // TRIPLE-NOTIFY SOTPN SYNC FIX:
        // We notify 3 times for the final end-frame to guarantee delivery if radio is busy.
        boolean isEndFrame = (chunk.length > 0 && (chunk[0] == BleConstants.FRAME_END || chunk[0] == BleConstants.FRAME_SINGLE));
        int attempts = isEndFrame ? 3 : 1;
        
        for (int i = 0; i < attempts; i++) {
            mainHandler.postDelayed(() -> {
                if (gattServer != null && connectedDevice != null && activeNotifyChar != null) {
                    gattServer.notifyCharacteristicChanged(connectedDevice, activeNotifyChar, false);
                }
            }, i * 200);
        }
    }

    private final Object reassemblyLock = new Object();

    private void handleIncomingChunk(byte[] chunk, String fromMac) {
        if (chunk == null || chunk.length == 0) return;
        
        synchronized (reassemblyLock) {
            try {
                // DEDUPLICATION FILTER: Ignore hardware-level repeat deliveries
                if (java.util.Arrays.equals(chunk, lastChunkReceived)) {
                    Log.v(TAG, "Ignoring duplicate chunk delivery");
                    return;
                }
                lastChunkReceived = chunk.clone();

                incomingChunkCount++;
                final int chunkId = incomingChunkCount;
                mainHandler.post(() -> {
                    android.widget.Toast.makeText(context, "SOTPN: Received Chunk " + chunkId, android.widget.Toast.LENGTH_SHORT).show();
                    // RESET WATCHDOG: Signal that data is arriving
                    callback.onGossipReceived("PING_WATCHDOG", fromMac); 
                });
                
                byte frameType = chunk[0];
                byte[] data    = java.util.Arrays.copyOfRange(chunk, 1, chunk.length);

                switch (frameType) {
                    case BleConstants.FRAME_SINGLE:
                        receiveBuffer = data;
                        processReceivedMessage(receiveBuffer, fromMac);
                        receiveBuffer = new byte[0];
                        incomingChunkCount = 0;
                        break;

                    case BleConstants.FRAME_START:
                        receiveBuffer = data;
                        incomingChunkCount = 1; // RESET count on new stream
                        lastChunkReceived = chunk.clone(); // re-record to allow same data in next tx
                        break;

                    case BleConstants.FRAME_CONT:
                        receiveBuffer = concat(receiveBuffer, data);
                        break;

                    case BleConstants.FRAME_END:
                        receiveBuffer = concat(receiveBuffer, data);
                        Log.i(TAG, "Message Reassembled. Parsing...");
                        mainHandler.post(() -> android.widget.Toast.makeText(context, "SOTPN: Reassembled!", android.widget.Toast.LENGTH_SHORT).show());
                        processReceivedMessage(receiveBuffer, fromMac);
                        receiveBuffer = new byte[0];
                        incomingChunkCount = 0;
                        lastChunkReceived = null; // Clear for next tx
                        break;
                }
            } catch (Throwable t) {
                Log.e(TAG, "CRASH in handleIncomingChunk", t);
                mainHandler.post(() -> android.widget.Toast.makeText(context, "RECEIVE CRASH: " + t.getClass().getSimpleName(), android.widget.Toast.LENGTH_LONG).show());
            }
        }
    }

    private void processReceivedMessage(byte[] fullPayload, String fromMac) {
        try {
            String json = new String(fullPayload, StandardCharsets.UTF_8);
            Log.d(TAG, "Received full message: " + json);

            if (!json.trim().startsWith("{")) {
                mainHandler.post(() -> callback.onGossipReceived(json, fromMac));
                return;
            }

            JSONObject obj = new JSONObject(json);

            if (obj.has("ack_signature")) {
                TransactionAck ack = TransactionAck.fromJson(obj);
                mainHandler.post(() -> callback.onAckReceived(ack));
            } else if (obj.has("sender")) {
                Transaction tx = Transaction.fromJson(obj);
                mainHandler.post(() -> callback.onTransactionReceived(tx, fromMac));
            }
        } catch (Throwable t) {
            Log.e(TAG, "CRASH in processReceivedMessage", t);
            mainHandler.post(() -> android.widget.Toast.makeText(context, "SOTPN CRASH: " + t.getClass().getSimpleName(), android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] res = new byte[a.length + b.length];
        System.arraycopy(a, 0, res, 0, a.length);
        System.arraycopy(b, 0, res, a.length, b.length);
        return res;
    }
}
