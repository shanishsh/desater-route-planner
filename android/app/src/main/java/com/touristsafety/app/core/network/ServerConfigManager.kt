package com.touristsafety.app.core.network

import android.content.Context
import android.content.SharedPreferences
import com.touristsafety.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ServerConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PREFS_NAME = "tourist_server_config"
        const val KEY_BASE_URL = "backend_base_url"

        // Default Presets
        const val PRESET_WIFI_LAN = "http://172.168.10.238:8000/api/v1/"
        const val PRESET_EMULATOR = "http://10.0.2.2:8000/api/v1/"
        const val PRESET_USB_REVERSE = "http://127.0.0.1:8000/api/v1/"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(
        prefs.getString(KEY_BASE_URL, PRESET_WIFI_LAN) ?: PRESET_WIFI_LAN
    )
    open val baseUrl: StateFlow<String> get() = _baseUrl.asStateFlow()

    open fun getCurrentUrl(): String {
        return _baseUrl.value
    }

    open fun setBaseUrl(newUrl: String) {
        val sanitized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        prefs.edit().putString(KEY_BASE_URL, sanitized).apply()
        _baseUrl.value = sanitized
    }
}
