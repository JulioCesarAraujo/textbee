package com.vernu.sms.ui.settings;

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
import com.vernu.sms.service.LocalChannelService;
import com.vernu.sms.models.OfflineMode;

public class OfflineModeSettingsActivity extends AppCompatActivity {
    private Switch offlineModeSwitch;
    private EditText serverUrlEditText;
    private Button saveButton;
    private SharedPreferences sharedPreferences;

    private LocalChannelService localChannelService;

    private static final String PREFS_NAME = "offline_mode_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_OFFLINE_MODE_ENABLED = "offline_mode_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_mode_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        offlineModeSwitch = findViewById(R.id.offline_mode_switch);
        serverUrlEditText = findViewById(R.id.server_url_edit_text);
        saveButton = findViewById(R.id.save_button);

        loadSettings();

        offlineModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            serverUrlEditText.setEnabled(isChecked);
            if (!isChecked) {
                showOfflineModeConfirmationDialog();
            }
        });

        saveButton.setOnClickListener(v -> {
            saveSettings();
        });
    }

    private void loadSettings() {
        boolean isOfflineModeEnabled = sharedPreferences.getBoolean(KEY_OFFLINE_MODE_ENABLED, false);
        String serverUrl = sharedPreferences.getString(KEY_SERVER_URL, "http://localhost:8080");

        offlineModeSwitch.setChecked(isOfflineModeEnabled);
        serverUrlEditText.setText(serverUrl);
        serverUrlEditText.setEnabled(isOfflineModeEnabled);
    }

    private void saveSettings() {
        boolean isOfflineModeEnabled = offlineModeSwitch.isChecked();
        String serverUrl = serverUrlEditText.getText().toString().trim();

        if (isOfflineModeEnabled && serverUrl.isEmpty()) {
            serverUrlEditText.setError("Server URL is required");
            Toast.makeText(this, "Server URL is required when offline mode is enabled", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_OFFLINE_MODE_ENABLED, isOfflineModeEnabled);
        editor.putString(KEY_SERVER_URL, serverUrl);
        editor.apply();

        updateOfflineMode(isOfflineModeEnabled, serverUrl);
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void showOfflineModeConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Disable Offline Mode")
                .setMessage("Disabling offline mode will stop the local WebSocket connection and may interrupt ongoing message deliveries. Continue?")
                .setPositiveButton("Disable", (dialog, which) -> {
                    offlineModeSwitch.setChecked(false);
                    updateOfflineMode(false, serverUrlEditText.getText().toString().trim());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateOfflineMode(boolean enabled, String serverUrl) {
        Intent intent = new Intent(this, LocalChannelService.class);
        if (enabled) {
            intent.setAction(OfflineMode.ACTION_START_OFFLINE_MODE);
            intent.putExtra("server_url", serverUrl);
            startService(intent);
        } else {
            intent.setAction(OfflineMode.ACTION_STOP_OFFLINE_MODE);
            startService(intent);
        }
    }
}