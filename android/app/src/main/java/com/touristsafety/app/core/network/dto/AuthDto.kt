package com.touristsafety.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserLoginRequestDto(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String
)

@Serializable
data class UserRegisterRequestDto(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("role") val role: String = "tourist"
)

@Serializable
data class AuthTokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("role") val role: String = "tourist",
    @SerialName("expires_in_minutes") val expiresInMinutes: Int = 10080,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("email") val email: String? = null
)

@Serializable
data class UserMeResponseDto(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("role") val role: String = "tourist",
    @SerialName("is_active") val isActive: Boolean = true
)
