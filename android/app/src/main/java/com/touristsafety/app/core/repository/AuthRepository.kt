package com.touristsafety.app.core.repository

import com.touristsafety.app.core.auth.TokenManager
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.AuthTokenResponseDto
import com.touristsafety.app.core.network.dto.UserLoginRequestDto
import com.touristsafety.app.core.network.dto.UserMeResponseDto
import com.touristsafety.app.core.network.dto.UserRegisterRequestDto
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    val authState: StateFlow<Boolean> = tokenManager.authState

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    fun getUserEmail(): String? = tokenManager.getUserEmail()

    fun hasCompletedPermissionsOnboarding(): Boolean = tokenManager.hasCompletedPermissionsOnboarding()

    fun setPermissionsOnboardingCompleted(completed: Boolean = true) {
        tokenManager.setPermissionsOnboardingCompleted(completed)
    }

    suspend fun login(email: String, password: String): Result<AuthTokenResponseDto> {
        return try {
            val response = apiService.login(UserLoginRequestDto(email.trim(), password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.saveSession(
                    token = body.accessToken,
                    email = body.email ?: email.trim(),
                    role = body.role,
                    userId = body.userId
                )
                Result.success(body)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string())
                    ?: "Login failed (${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String): Result<AuthTokenResponseDto> {
        return try {
            val response = apiService.register(UserRegisterRequestDto(email.trim(), password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.saveSession(
                    token = body.accessToken,
                    email = body.email ?: email.trim(),
                    role = body.role,
                    userId = body.userId
                )
                Result.success(body)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string())
                    ?: "Registration failed (${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMe(): Result<UserMeResponseDto> {
        return try {
            val response = apiService.getMe()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch user profile (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        tokenManager.clearSession()
    }

    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            if (json.has("detail")) {
                val detail = json.get("detail")
                detail.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

