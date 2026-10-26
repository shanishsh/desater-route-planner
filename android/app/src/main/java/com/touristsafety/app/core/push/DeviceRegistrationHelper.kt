package com.touristsafety.app.core.push

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.DeviceRegisterRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DeviceRegistrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) {

    companion object {
        private const val TAG = "DeviceRegistration"
        private const val PREFS_NAME = "device_push_prefs"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getInstallationId(): String {
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id == null) {
            id = "inst_" + UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        return id
    }

    fun getStoredFcmToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    fun storeFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    private suspend fun Task<String>.awaitToken(): String? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { token ->
                if (cont.isActive) cont.resume(token)
            }
            addOnFailureListener { exc ->
                Log.w(TAG, "Firebase Cloud Messaging token retrieval failed: ${exc.message}")
                if (cont.isActive) cont.resume(null)
            }
            addOnCanceledListener {
                if (cont.isActive) cont.cancel()
            }
        }

    /**
     * Retrieves the genuine FCM registration token from Firebase Messaging SDK.
     */
    suspend fun getOrFetchFcmToken(): String? {
        return try {
            val token = FirebaseMessaging.getInstance().token.awaitToken()
            if (!token.isNullOrBlank()) {
                storeFcmToken(token)
                token
            } else {
                getStoredFcmToken()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error querying FirebaseMessaging token: ${t.message}")
            getStoredFcmToken()
        }
    }

    /**
     * Updates and syncs a newly refreshed FCM registration token with the backend.
     */
    suspend fun updateFcmToken(newToken: String): Result<Unit> {
        storeFcmToken(newToken)
        return registerDeviceWithBackend(tokenOverride = newToken)
    }

    /**
     * Registers the Android installation ID and genuine FCM token with the backend.
     */
    suspend fun registerDeviceWithBackend(tokenOverride: String? = null): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val instId = getInstallationId()
                val fcmToken = tokenOverride ?: getOrFetchFcmToken()

                val maskedToken = if (!fcmToken.isNullOrBlank() && fcmToken.length > 10) {
                    "${fcmToken.take(6)}...${fcmToken.takeLast(4)}"
                } else {
                    "(none)"
                }

                val req = DeviceRegisterRequestDto(
                    installation_id = instId,
                    fcm_token = fcmToken,
                    platform = "android",
                    app_version = "1.0.0"
                )

                val response = apiService.registerDevice(req)
                if (response.isSuccessful) {
                    Log.i(TAG, "Device $instId registered with backend. FCM Token: $maskedToken")
                    Result.success(Unit)
                } else {
                    Log.w(TAG, "Backend rejected device registration: HTTP ${response.code()}")
                    Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Offline: Could not register device with backend (will retry when online)", e)
                Result.failure(e)
            }
        }
    }
}
