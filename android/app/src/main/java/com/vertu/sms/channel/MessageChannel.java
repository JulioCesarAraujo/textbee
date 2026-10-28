package com.vernu.sms.channel;

import com.vernu.sms.Message;

public interface MessageChannel {
    void connect(String deviceId, String serverUrl, String authenticationToken);
    void disconnect();
    boolean isConnected();
    void sendMessage(Message message);
    void setMessageHandler(MessageHandler handler);
    void setConnectionListener(ConnectionListener listener);
    boolean isConnectionListenerRegistered();
    void registerConnectionListener(ConnectionListener listener);
    void unregisterConnectionListener();

    interface MessageHandler {
        void onMessageReceived(Message message);
    }

    interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onConnectionFailed(String error);
        void onMessageReceived(Message message);
    }
}