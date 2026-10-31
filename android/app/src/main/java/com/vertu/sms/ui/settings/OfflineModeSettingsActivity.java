package com.vertu.sms.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.vernu.sms.R;
import com.vernu.sms.managers.NetworkManager;
import com.vernu.sms.managers.OfflineModeManager;

public class OfflineModeSettingsActivity extends AppCompatActivity {
    private Switch offlineModeSwitch;
    private EditText serverUrlEditText;
    private Switch skipEmailVerificationSwitch;
    private Switch localWebSocketFallbackSwitch;
    private Button saveButton;
    private SharedPreferences sharedPreferences;

    private OfflineModeManager offlineModeManager;

    private static final String PREFS_NAME = "offline_mode_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_OFFLINE_MODE_ENABLED = "offline_mode_enabled";
    private static final String KEY_SKIP_EMAIL_VERIFICATION = "skip_email_verification";
    private static final String KEY_LOCAL_WS_FALLBACK = "local_ws_fallback";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_mode_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        offlineModeManager = new OfflineModeManager(this);

        offlineModeSwitch = findViewById(R.id.offline_mode_switch);
        serverUrlEditText = findViewById(R.id.server_url_edit_text);
        skipEmailVerificationSwitch = findViewById(R.id.skip_email_verification_switch);
        localWebSocketFallbackSwitch = findViewById(R.id.local_web_socket_fallback_switch);
        saveButton = findViewById(R.id.save_button);

        loadSettings();

        offlineModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            serverUrlEditText.setEnabled(isChecked);
            skipEmailVerificationSwitch.setEnabled(isChecked);
            localWebSocketFallbackSwitch.setEnabled(isChecked);

            if (!isChecked) {
                showOfflineModeConfirmationDialog();
            }
        });

        saveButton.setOnClickListener(v -> {
            saveSettings();
        });
    }

    private void loadSettings() {
        boolean isOfflineModeEnabled = offlineModeManager.isOfflineModeEnabled();
        String serverUrl = offlineModeManager.getOfflineModeApiUrl();
        boolean skipEmailVerification = offlineModeManager.isEmailVerificationSkipped();
        boolean localWebSocketFallback = offlineModeManager.isLocalWebSocketFallback();

        offlineModeSwitch.setChecked(isOfflineModeEnabled);
        serverUrlEditText.setText(serverUrl);
        serverUrlEditText.setEnabled(isOfflineModeEnabled);
        skipEmailVerificationSwitch.setChecked(skipEmailVerification);
        skipEmailVerificationSwitch.setEnabled(isOfflineModeEnabled);
        localWebSocketFallbackSwitch.setChecked(localWebSocketFallback);
        localWebSocketFallbackSwitch.setEnabled(isOfflineModeEnabled);
    }

    private void saveSettings() {
        boolean isOfflineModeEnabled = offlineModeSwitch.isChecked();
        String serverUrl = serverUrlEditText.getText().toString().trim();
        boolean skipEmailVerification = skipEmailVerificationSwitch.isChecked();
        boolean localWebSocketFallback = localWebSocketFallbackSwitch.isChecked();

        if (isOfflineModeEnabled) {
            if (serverUrl.isEmpty()) {
                serverUrlEditText.setError("Server URL is required");
                Toast.makeText(this, "Server URL is required when offline mode is enabled", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!skipEmailVerification && !localWebSocketFallback) {
                showAdditionalOptionsWarningDialog();
                return;
            }
        }

        offlineModeManager.setOfflineModeEnabled(isOfflineModeEnabled);
        offlineModeManager.setOfflineModeApiUrl(serverUrl);
        offlineModeManager.setEmailVerificationSkipped(skipEmailVerification);
        offlineModeManager.setLocalWebSocketFallback(localWebSocketFallback);

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void showOfflineModeConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Disable Offline Mode")
                .setMessage("Disabling offline mode will stop the local WebSocket connection and may interrupt ongoing message deliveries. Continue?")
                .setPositiveButton("Disable", (dialog, which) -> {
                    offlineModeSwitch.setChecked(false);
                    skipEmailVerificationSwitch.setChecked(false);
                    localWebSocketFallbackSwitch.setChecked(false);
                    updateOfflineMode(false, serverUrlEditText.getText().toString().trim());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAdditionalOptionsWarningDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Additional Options Available")
                .setMessage("You have offline mode enabled but skipped email verification and WebSocket fallback are both disabled. These options provide additional functionality for offline mode. Would you like to enable them?")
                .setPositiveButton("Enable Defaults", (dialog, which) -> {
                    skipEmailVerificationSwitch.setChecked(true);
                    localWebSocketFallbackSwitch.setChecked(true);
                    saveSettings();
                })
                .setNegativeButton("Keep Current", (dialog, which) -> {
                    saveSettings();
                })
                .show();
    }

    private void updateOfflineMode(boolean enabled, String serverUrl) {
        // Update NetworkManager with the new configuration
        NetworkManager networkManager = new NetworkManager();
        networkManager.setOfflineModeEnabled(enabled);
        networkManager.setOfflineModeApiUrl(serverUrl);
        networkManager.setEmailVerificationSkipped(skipEmailVerificationSwitch.isChecked());
        networkManager.setLocalWebSocketFallback(localWebSocketFallbackSwitch.isChecked());
        
        // Notify the system about the configuration change
        // This will trigger the app to use the new configuration
        // The actual communication with the server will be handled by the NetworkManager
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
    }
}
