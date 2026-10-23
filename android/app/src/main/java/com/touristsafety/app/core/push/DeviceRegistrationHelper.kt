package com.touristsafety.app.core.push

import android.content.Context
import android.os.Build
import android.util.Log
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.DeviceRegisterRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRegistrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) {

    private val prefs = context.getSharedPreferences("device_push_prefs", Context.MODE_PRIVATE)

    fun getInstallationId(): String {
        var id = prefs.getString("installation_id", null)
        if (id == null) {
            id = "inst_" + UUID.randomUUID().toString()
            prefs.edit().putString("installation_id", id).apply()
        }
        return id
    }

    suspend fun registerDeviceWithBackend(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val instId = getInstallationId()
                val mockFcmToken = "fcm_token_${instId.takeLast(8)}_${Build.MODEL.replace(" ", "_")}"
                val req = DeviceRegisterRequestDto(
                    installation_id = instId,
                    fcm_token = mockFcmToken,
                    platform = "android",
                    app_version = "1.0.0"
                )
                val response = apiService.registerDevice(req)
                if (response.isSuccessful) {
                    Log.i("DeviceRegistration", "Registered device installation $instId with backend.")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.w("DeviceRegistration", "Offline: Could not register device with backend (will retry when online)", e)
                Result.failure(e)
            }
        }
    }
}

