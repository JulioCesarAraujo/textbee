package com.vernu.sms.channel;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vernu.sms.Message;
import com.vernu.sms.database.local.LocalMessageDatabase;
import com.vernu.sms.models.MessageStatus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.OkHttpClient.Builder;

public class LocalChannel implements MessageChannel {
    private static final String TAG = "LocalChannel";
    private static final int RECONNECTION_ATTEMPTS = 5;
    private static final int BASE_RECONNECTION_DELAY_MS = 1000;
    private static final int MAX_RECONNECTION_DELAY_MS = 30000;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private Context context;
    private LocalMessageDatabase database;
    private String deviceId;
    private String serverUrl;
    private String authenticationToken;
    private OkHttpClient okHttpClient;
    private WebSocket webSocket;
    private Handler mainHandler;
    private ScheduledExecutorService reconnectionExecutor;

    private MessageHandler messageHandler;
    private ConnectionListener connectionListener;
    private boolean isConnectionListenerRegistered = false;

    private int reconnectionAttempts = 0;
    private int currentReconnectionDelayMs = BASE_RECONNECTION_DELAY_MS;
    private boolean isReconnectionScheduled = false;
    private long lastSuccessfulConnectionTime = 0;
    private boolean isNetworkAvailable = true;

    public LocalChannel(Context context, LocalMessageDatabase database) {
        this.context = context;
        this.database = database;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.reconnectionExecutor = Executors.newSingleThreadScheduledExecutor();
        this.okHttpClient = createOkHttpClient();
        setupConnectivityListener();
    }

    private void setupConnectivityListener() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(android.net.Network network) {
                super.onAvailable(network);
                isNetworkAvailable = true;
                if (webSocket == null || !webSocket.isOpen()) {
                    attemptReconnection();
                }
            }

            @Override
            public void onLost(android.net.Network network) {
                super.onLost(network);
                isNetworkAvailable = false;
                disconnect();
            }
        });

        isNetworkAvailable = connectivityManager.getActiveNetworkInfo() != null &&
                connectivityManager.getActiveNetworkInfo().isConnected();
    }

    private OkHttpClient createOkHttpClient() {
        Builder builder = new Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        return builder.build();
    }

    @Override
    public void connect(String deviceId, String serverUrl, String authenticationToken) {
        if (!isNetworkAvailable) {
            Log.w(TAG, "Network is not available, connection skipped");
            notifyConnectionFailed("Network is not available");
            return;
        }

        this.deviceId = deviceId;
        this.serverUrl = serverUrl;
        this.authenticationToken = authenticationToken;
        this.reconnectionAttempts = 0;
        this.currentReconnectionDelayMs = BASE_RECONNECTION_DELAY_MS;

        String wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://");
        Request request = new Request.Builder()
                .url(wsUrl + "/ws?deviceId=" + deviceId + "&token=" + authenticationToken)
                .build();

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                Log.d(TAG, "WebSocket connected successfully");
                mainHandler.post(() -> {
                    notifyConnected();
                    processQueuedMessages();
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    Message message = parseMessageFromJson(text);
                    Log.d(TAG, "Received message: " + message.type + ", id: " + message.id);

                    mainHandler.post(() -> {
                        notifyMessageReceived(message);
                        handleReceivedMessage(message);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse message: " + text, e);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
                disconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                mainHandler.post(() -> notifyDisconnected());
                scheduleReconnection();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket connection failed", t);
                mainHandler.post(() -> notifyConnectionFailed("Connection failed: " + t.getMessage()));
                scheduleReconnection();
            }
        });
    }

    @Override
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User initiated disconnect");
            webSocket = null;
        }
        isReconnectionScheduled = false;
    }

    @Override
    public boolean isConnected() {
        return webSocket != null && webSocket.isOpen();
    }

    @Override
    public void sendMessage(Message message) {
        if (isConnected()) {
            try {
                String jsonMessage = createJsonFromMessage(message);
                webSocket.send(jsonMessage);
                Log.d(TAG, "Sent message: " + message.type + ", id: " + message.id);
                saveMessageToDatabase(message, MessageStatus.SENT);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send message", e);
                saveMessageToDatabase(message, MessageStatus.FAILED);
            }
        } else {
            Log.w(TAG, "WebSocket not connected, queuing message: " + message.id);
            queueMessageForLaterSend(message);
        }
    }

    @Override
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
        isConnectionListenerRegistered = true;
    }

    @Override
    public boolean isConnectionListenerRegistered() {
        return isConnectionListenerRegistered;
    }

    @Override
    public void registerConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
        isConnectionListenerRegistered = true;
        if (isConnected()) {
            notifyConnected();
        }
    }

    @Override
    public void unregisterConnectionListener() {
        isConnectionListenerRegistered = false;
        connectionListener = null;
    }

    private void notifyConnected() {
        if (connectionListener != null) {
            connectionListener.onConnected();
        }
        lastSuccessfulConnectionTime = System.currentTimeMillis();
    }

    private void notifyDisconnected() {
        if (connectionListener != null) {
            connectionListener.onDisconnected();
        }
    }

    private void notifyConnectionFailed(String error) {
        if (connectionListener != null) {
            connectionListener.onConnectionFailed(error);
        }
    }

    private void notifyMessageReceived(Message message) {
        if (connectionListener != null) {
            connectionListener.onMessageReceived(message);
        }
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message);
        }
    }

    private void scheduleReconnection() {
        if (isReconnectionScheduled) {
            return;
        }

        if (reconnectionAttempts >= RECONNECTION_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached, giving up");
            notifyConnectionFailed("Max reconnection attempts reached");
            return;
        }

        isReconnectionScheduled = true;
        reconnectionExecutor.schedule(() -> {
            attemptReconnection();
        }, currentReconnectionDelayMs, TimeUnit.MILLISECONDS);

        int newDelay = (int) (currentReconnectionDelayMs * BACKOFF_MULTIPLIER);
        currentReconnectionDelayMs = Math.min(newDelay, MAX_RECONNECTION_DELAY_MS);
        reconnectionAttempts++;
    }

    private void attemptReconnection() {
        Log.d(TAG, "Attempting reconnection (attempt " + reconnectionAttempts + ")");
        if (isNetworkAvailable) {
            connect(deviceId, serverUrl, authenticationToken);
        }
        isReconnectionScheduled = false;
    }

    private Message parseMessageFromJson(String json) {
        // TODO: Implement JSON parsing using your preferred library
        // For now, create a simple mock message
        return new Message(
                "SEND_SMS",
                "msg_" + System.currentTimeMillis(),
                "1234567890",
                "Test message",
                System.currentTimeMillis()
        );
    }

    private String createJsonFromMessage(Message message) {
        // TODO: Implement JSON serialization using your preferred library
        // For now, create a simple JSON-like string
        return "{\"type\":\"" + message.type + "\",\"id\":\"" + message.id +
                "\",\"to\":\"" + message.to + "\",\"message\":\"" + message.message +
                "\",\"timestamp\":"" + message.timestamp + "\"}";
    }

    private void saveMessageToDatabase(Message message, MessageStatus status) {
        // TODO: Implement database save
        // This should save the message with the given status
        Log.d(TAG, "Saved message " + message.id + " with status " + status);
    }

    private void queueMessageForLaterSend(Message message) {
        // TODO: Implement message queuing
        // This should store the message for later delivery when connection is restored
        Log.d(TAG, "Queued message " + message.id + " for later send");
    }

    private void processQueuedMessages() {
        if (isConnected()) {
            Log.d(TAG, "Processing queued messages");
            // TODO: Implement queued message processing
            // Retrieve and send all queued messages
        }
    }

    public void destroy() {
        disconnect();
        isReconnectionScheduled = false;
        reconnectionExecutor.shutdown();
    }
}