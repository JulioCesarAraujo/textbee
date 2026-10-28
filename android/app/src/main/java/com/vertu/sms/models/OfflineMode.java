package com.vernu.sms.models;

import android.content.Context;
import android.content.SharedPreferences;

public class OfflineMode {
    public static final String ACTION_START_OFFLINE_MODE = "com.vernu.sms.action.START_OFFLINE_MODE";
    public static final String ACTION_STOP_OFFLINE_MODE = "com.vernu.sms.action.STOP_OFFLINE_MODE";

    public interface OfflineModeListener {
        void onOfflineModeEnabled();
        void onOfflineModeDisabled();
        void onOfflineModeError(String error);
    }

    public static void initialize(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("offline_mode", Context.MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("offline_mode_enabled", false);
        if (isEnabled) {
            startOfflineMode(context);
        }
    }

    public static void startOfflineMode(Context context) {
        context.startService(new Intent(context, LocalChannelService.class).setAction(ACTION_START_OFFLINE_MODE));
    }

    public static void stopOfflineMode(Context context) {
        context.startService(new Intent(context, LocalChannelService.class).setAction(ACTION_STOP_OFFLINE_MODE));
    }

    public static boolean isOfflineModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("offline_mode", Context.MODE_PRIVATE);
        return prefs.getBoolean("offline_mode_enabled", false);
    }

    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("offline_mode", Context.MODE_PRIVATE);
        return prefs.getString("server_url", "http://localhost:8080");
    }
}