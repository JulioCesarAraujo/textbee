import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.vernu.sms.database.DatabaseClient;
import com.vernu.sms.database.local.LocalMessageDatabase;
import com.vernu.sms.channel.LocalChannel;
import com.vernu.sms.models.OfflineMode;
import com.vernu.sms.helpers.NotificationHelper;

public class LocalChannelService extends Service {
    private static final String TAG = "LocalChannelService";
    private static final String CHANNEL_ID = "local_channel_service";
    private static final int NOTIFICATION_ID = 1001;

    private LocalChannel localChannel;
    private DatabaseClient databaseClient;
    private OfflineMode.OfflineModeListener offlineModeListener;

    public LocalChannelService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "LocalChannelService created");

        databaseClient = DatabaseClient.getInstance(this);
        localChannel = new LocalChannel(this, databaseClient.getLocalMessageDatabase());

        // Set up message handler
        localChannel.setMessageHandler(message -> {
            Log.d(TAG, "Message received via LocalChannel: " + message.type);
            handleReceivedMessage(message);
        });

        // Set up connection listener
        localChannel.setConnectionListener(new LocalChannel.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "LocalChannel connected");
                showNotification("Local WebSocket connected", "Local channel service is active");
            }

            @Override
            public() {
                Log.d(TAG, "LocalChannel disconnected");
                showNotification("Local WebSocket disconnected", "Local channel service is inactive");
            }

            @Override
            public void onConnectionFailed(String error) {
                Log.e(TAG, "LocalChannel connection failed: " + error);
                showNotification("Local WebSocket connection failed", error);
            }

            @Override
            public void onMessageReceived(Message message) {
                // Handled by message handler
            }
        });
    }

        private void showNotification(String title, String content) {
        NotificationHelper.showNotification(this, title, content);
    }

    private void cancelNotification() {
        NotificationHelper.cancelNotification(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (OfflineMode.ACTION_START_OFFLINE_MODE.equals(action)) {
                startLocalChannel();
            } else if (OfflineMode.ACTION_STOP_OFFLINE_MODE.equals(action)) {
                stopLocalChannel();
            }
        }
        return START_STICKY;
    }

    private void startLocalChannel() {
        Log.d(TAG, "Starting LocalChannel service");
        if (!localChannel.isConnected()) {
            // Get server URL from shared preferences or default
            String serverUrl = "http://localhost:8080"; // TODO: Get from config
            String authToken = "dummy_token"; // TODO: Get from secure storage

            localChannel.connect("device_123", serverUrl, authToken);
            showNotification("Local WebSocket service started", "Offline mode active");
        }
    }

    private void stopLocalChannel() {
        Log.d(TAG, "Stopping LocalChannel service");
        localChannel.disconnect();
        stopSelf();
        showNotification("Local WebSocket service stopped", "Offline mode inactive");
    }

    private void handleReceivedMessage(Message message) {
        Log.d(TAG, "Handling received message: " + message.type);
        // TODO: Process received message
        // Save to local database
        // Send ACK if needed
        // Handle different message types (SEND_SMS, ACK, RESULT)
    }

    private void showNotification(String title, String content) {
        NotificationHelper.showNotification(this, title, content);
    }

    private void cancelNotification() {
        NotificationHelper.cancelNotification(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        localChannel.destroy();
        Log.d(TAG, "LocalChannelService destroyed");
    }

    public void setOfflineModeListener(OfflineMode.OfflineModeListener listener) {
        this.offlineModeListener = listener;
    }
}