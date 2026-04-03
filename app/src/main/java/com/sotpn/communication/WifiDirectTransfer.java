package com.sotpn.communication;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sotpn.model.Transaction;
import com.sotpn.model.TransactionAck;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles actual data transfer over Wi-Fi Direct using TCP sockets.
 */
public class WifiDirectTransfer {

    private static final String TAG = "WifiDirectTransfer";

    private final WifiDirectCallback callback;
    private final Handler            mainHandler;
    private final ExecutorService    executor;

    private ServerSocket serverSocket;
    private Socket       activeSocket;
    private boolean      isRunning = false;

    public WifiDirectTransfer(WifiDirectCallback callback) {
        this.callback    = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor    = Executors.newCachedThreadPool();
    }

    public void startServer() {
        isRunning = true;
        executor.execute(() -> {
            try {
                Log.i(TAG, "Starting TCP server on port " + WifiDirectConstants.TRANSACTION_PORT);
                serverSocket = new ServerSocket(WifiDirectConstants.TRANSACTION_PORT);
                serverSocket.setSoTimeout(WifiDirectConstants.SERVER_SOCKET_TIMEOUT_MS);

                Log.i(TAG, "Server waiting for client...");
                activeSocket = serverSocket.accept();
                activeSocket.setSoTimeout(WifiDirectConstants.SOCKET_READ_TIMEOUT_MS);
                Log.i(TAG, "Client connected: " + activeSocket.getInetAddress());

                listenForMessages(activeSocket);

            } catch (IOException e) {
                Log.e(TAG, "Server socket error", e);
                mainHandler.post(() ->
                        callback.onWiFiDirectError("Server failed: " + e.getMessage()));
            } finally {
                closeServerSocket();
            }
        });
    }

    public void connectToServer(InetAddress groupOwnerAddress) {
        isRunning = true;
        executor.execute(() -> {
            try {
                Log.i(TAG, "Connecting to server: " + groupOwnerAddress.getHostAddress());
                activeSocket = new Socket();
                activeSocket.connect(
                        new java.net.InetSocketAddress(
                                groupOwnerAddress,
                                WifiDirectConstants.TRANSACTION_PORT),
                        WifiDirectConstants.CLIENT_CONNECT_TIMEOUT_MS);
                activeSocket.setSoTimeout(WifiDirectConstants.SOCKET_READ_TIMEOUT_MS);
                Log.i(TAG, "Connected to Group Owner");

                listenForMessages(activeSocket);

            } catch (IOException e) {
                Log.e(TAG, "Client connection error", e);
                mainHandler.post(() ->
                        callback.onConnectionFailed("Client failed: " + e.getMessage()));
            }
        });
    }

    public void sendTransaction(Transaction transaction) {
        executor.execute(() -> {
            try {
                String json = WifiDirectConstants.MSG_TYPE_TRANSACTION
                        + transaction.toJson().toString();
                writeMessage(activeSocket, json);
                final String txId = transaction.getTxId();
                mainHandler.post(() -> callback.onTransactionSent(txId));
            } catch (Exception e) {
                final String txId = transaction.getTxId();
                mainHandler.post(() ->
                        callback.onTransactionSendFailed(txId, e.getMessage()));
            }
        });
    }

    public void sendAck(TransactionAck ack) {
        executor.execute(() -> {
            try {
                String json = WifiDirectConstants.MSG_TYPE_ACK
                        + ack.toJson().toString();
                writeMessage(activeSocket, json);
            } catch (Exception e) {
                final String txId = ack.getTxId();
                mainHandler.post(() -> callback.onAckSendFailed(txId, e.getMessage()));
            }
        });
    }

    public void sendGossip(String gossipMessage) {
        executor.execute(() -> {
            try {
                String tagged = WifiDirectConstants.MSG_TYPE_GOSSIP + gossipMessage;
                writeMessage(activeSocket, tagged);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send gossip", e);
            }
        });
    }

    public void stop() {
        isRunning = false;
        closeSocket(activeSocket);
        closeServerSocket();
        executor.shutdownNow();
    }

    private void listenForMessages(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());

            while (isRunning && !socket.isClosed()) {
                int length = in.readInt();
                if (length <= 0 || length > 1_000_000) continue;

                byte[] data = new byte[length];
                in.readFully(data);
                String raw = new String(data, StandardCharsets.UTF_8);

                parseAndDispatch(raw);
            }

        } catch (IOException e) {
            if (isRunning) {
                mainHandler.post(() ->
                        callback.onWiFiDirectError("Read error: " + e.getMessage()));
            }
        }
    }

    private void parseAndDispatch(String raw) {
        try {
            if (raw.startsWith(WifiDirectConstants.MSG_TYPE_TRANSACTION)) {
                String json = raw.substring(WifiDirectConstants.MSG_TYPE_TRANSACTION.length());
                Transaction tx = Transaction.fromJson(new JSONObject(json));
                mainHandler.post(() -> callback.onTransactionReceived(tx));

            } else if (raw.startsWith(WifiDirectConstants.MSG_TYPE_ACK)) {
                String json = raw.substring(WifiDirectConstants.MSG_TYPE_ACK.length());
                TransactionAck ack = TransactionAck.fromJson(new JSONObject(json));
                mainHandler.post(() -> callback.onAckReceived(ack));

            } else if (raw.startsWith(WifiDirectConstants.MSG_TYPE_GOSSIP)) {
                String gossip = raw.substring(WifiDirectConstants.MSG_TYPE_GOSSIP.length());
                mainHandler.post(() -> callback.onGossipReceived(gossip));

            }
        } catch (Exception e) {
            mainHandler.post(() ->
                    callback.onWiFiDirectError("Parse error: " + e.getMessage()));
        }
    }

    private void writeMessage(Socket socket, String message) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not connected");
        }
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    private void closeSocket(Socket s) {
        if (s != null && !s.isClosed()) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }
}
