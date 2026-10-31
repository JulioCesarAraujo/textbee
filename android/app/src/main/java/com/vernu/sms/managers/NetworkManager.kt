package com.vernu.sms.managers;

import android.content.Context
import android.content.SharedPreferences

class NetworkManager {
    private val prefs: SharedPreferences = Context.getSharedPreferences("network_prefs", Context.MODE_PRIVATE)
    
    fun getServerUrl(): String {
        // Check if offline mode is enabled
        if (isOfflineModeEnabled()) {
            // If offline mode is enabled, use offline mode API URL
            val offlineModeApiUrl = getOfflineModeApiUrl()
            if (!offlineModeApiUrl.isEmpty()) {
                return offlineModeApiUrl
            }
        }
        
        // Otherwise, use production API URL
        return getProductionApiUrl()
    }
    
    fun getProductionApiUrl(): String {
        return BuildConfig.BASE_URL
    }
    
    fun getOfflineModeApiUrl(): String {
        return prefs.getString("OFFLINE_MODE_API_URL", "") ?: ""
    }
    
    fun isOfflineModeEnabled(): Boolean {
        return prefs.getBoolean("OFFLINE_MODE_ENABLED", false)
    }
    
    fun isEmailVerificationSkipped(): Boolean {
        return prefs.getBoolean("OFFLINE_MODE_SKIP_EMAIL_VERIFICATION", false)
    }
    
    fun isLocalWebSocketFallback(): Boolean {
        return prefs.getBoolean("OFFLINE_MODE_LOCAL_WS_FALLBACK", false)
    }
    
    fun setOfflineModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("OFFLINE_MODE_ENABLED", enabled).apply()
    }
    
    fun setOfflineModeApiUrl(url: String) {
        prefs.edit().putString("OFFLINE_MODE_API_URL", url).apply()
    }
    
    fun setEmailVerificationSkipped(skip: Boolean) {
        prefs.edit().putBoolean("OFFLINE_MODE_SKIP_EMAIL_VERIFICATION", skip).apply()
    }
    
    fun setLocalWebSocketFallback(fallback: Boolean) {
        prefs.edit().putBoolean("OFFLINE_MODE_LOCAL_WS_FALLBACK", fallback).apply()
    }
}