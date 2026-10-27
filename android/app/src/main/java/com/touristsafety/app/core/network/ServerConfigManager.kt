package com.touristsafety.app.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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
        const val PRESET_RENDER = "https://desater-route-planner.onrender.com/api/v1/"
        const val PRESET_WIFI_LAN = "http://172.168.10.238:8000/api/v1/"
        const val PRESET_EMULATOR = "http://10.0.2.2:8000/api/v1/"
        const val PRESET_USB_REVERSE = "http://127.0.0.1:8000/api/v1/"

        fun isRunningOnEmulator(): Boolean {
            val fingerprint = Build.FINGERPRINT ?: ""
            val model = Build.MODEL ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""
            val hardware = Build.HARDWARE ?: ""
            val product = Build.PRODUCT ?: ""
            return (fingerprint.startsWith("generic")
                    || fingerprint.startsWith("unknown")
                    || model.contains("google_sdk", ignoreCase = true)
                    || model.contains("Emulator", ignoreCase = true)
                    || model.contains("Android SDK built for x86", ignoreCase = true)
                    || manufacturer.contains("Genymotion", ignoreCase = true)
                    || hardware.contains("goldfish", ignoreCase = true)
                    || hardware.contains("ranchu", ignoreCase = true)
                    || product.startsWith("sdk_google")
                    || product.startsWith("google_sdk")
                    || product.contains("vbox86p", ignoreCase = true)
                    || product.contains("emulator", ignoreCase = true)
                    || product.contains("simulator", ignoreCase = true))
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(resolveInitialUrl())
    open val baseUrl: StateFlow<String> get() = _baseUrl.asStateFlow()

    private fun resolveInitialUrl(): String {
        val saved = prefs.getString(KEY_BASE_URL, null)
        val isEmulator = isRunningOnEmulator()

        // If on physical device:
        if (!isEmulator) {
            // If never set, or set to an emulator/LAN loopback, force Render production
            if (saved.isNullOrBlank() || saved.contains("10.0.2.2") || saved.contains("127.0.0.1")) {
                prefs.edit().putString(KEY_BASE_URL, PRESET_RENDER).apply()
                return PRESET_RENDER
            }
            return saved
        }
        // Even on emulator, default to PRESET_RENDER so cloud endpoints work out of the box
        if (saved.isNullOrBlank()) {
            return PRESET_RENDER
        }
        return saved
    }

    open fun getCurrentUrl(): String {
        return _baseUrl.value
    }

    open fun setBaseUrl(newUrl: String) {
        val sanitized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        prefs.edit().putString(KEY_BASE_URL, sanitized).apply()
        _baseUrl.value = sanitized
    }

    open fun resetToRender() {
        setBaseUrl(PRESET_RENDER)
    }
}
