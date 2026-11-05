package com.vernu.sms.managers;

import android.content.Context
import android.content.SharedPreferences

class OfflineModeManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("offline_mode_prefs", Context.MODE_PRIVATE)
    
    fun isOfflineModeEnabled(): Boolean {
        return prefs.getBoolean("OFFLINE_MODE_ENABLED", false)
    }
    
    fun setOfflineModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("OFFLINE_MODE_ENABLED", enabled).apply()
        
        // When offline mode is enabled, clear production API URL and save offline API URL
        if (enabled) {
            val currentOfflineUrl = getOfflineModeApiUrl()
            if (currentOfflineUrl.isNotEmpty()) {
                // Save the current API URL as offline mode URL
                prefs.edit().putString("OFFLINE_MODE_API_URL", currentOfflineUrl).apply()
            }
        }
    }
    
    fun getOfflineModeApiUrl(): String {
        return prefs.getString("OFFLINE_MODE_API_URL", "") ?: ""
    }
    
    fun setOfflineModeApiUrl(url: String) {
        prefs.edit().putString("OFFLINE_MODE_API_URL", url).apply()
    }
    
    fun isEmailVerificationSkipped(): Boolean {
        return prefs.getBoolean("OFFLINE_MODE_SKIP_EMAIL_VERIFICATION", false)
    }
    
    fun setEmailVerificationSkipped(skip: Boolean) {
        prefs.edit().putBoolean("OFFLINE_MODE_SKIP_EMAIL_VERIFICATION", skip).apply()
    }
    
    fun isLocalWebSocketFallback(): Boolean {
        return prefs.getBoolean("OFFLINE_MODE_LOCAL_WS_FALLBACK", false)
    }
    
    fun setLocalWebSocketFallback(fallback: Boolean) {
        prefs.edit().putBoolean("OFFLINE_MODE_LOCAL_WS_FALLBACK", fallback).apply()
    }
    
    fun hasValidOfflineModeConfig(): Boolean {
        return isOfflineModeEnabled() && 
               isEmailVerificationSkipped() && 
               getOfflineModeApiUrl().isNotEmpty()
    }
}