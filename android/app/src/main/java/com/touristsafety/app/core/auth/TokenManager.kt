package com.touristsafety.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "tourist_auth_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "jwt_access_token"
        private const val KEY_USER_EMAIL = "auth_user_email"
        private const val KEY_USER_ROLE = "auth_user_role"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_PERMISSIONS_ONBOARDED = "permissions_onboarded"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(isLoggedIn())
    val authState: StateFlow<Boolean> = _authState.asStateFlow()

    fun saveSession(token: String, email: String, role: String, userId: String? = null) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_USER_ID, userId)
            .apply()
        _authState.value = true
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, "tourist")

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun isLoggedIn(): Boolean {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        return !token.isNullOrBlank()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_ID)
            .apply()
        _authState.value = false
    }

    fun hasCompletedPermissionsOnboarding(): Boolean {
        return prefs.getBoolean(KEY_PERMISSIONS_ONBOARDED, false)
    }

    fun setPermissionsOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_PERMISSIONS_ONBOARDED, completed).apply()
    }
}
