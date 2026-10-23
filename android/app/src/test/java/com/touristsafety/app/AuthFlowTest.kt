package com.touristsafety.app

import com.touristsafety.app.core.network.dto.AuthTokenResponseDto
import com.touristsafety.app.core.network.dto.UserLoginRequestDto
import com.touristsafety.app.core.network.dto.UserMeResponseDto
import com.touristsafety.app.core.network.dto.UserRegisterRequestDto
import com.touristsafety.app.core.repository.AuthRepository
import com.touristsafety.app.feature.auth.AuthNavigationEvent
import com.touristsafety.app.feature.auth.AuthViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        viewModel = AuthViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAuthDtoSerialization() {
        val loginReq = UserLoginRequestDto(email = "hiker@mountains.org", password = "secretPassword")
        val jsonStr = json.encodeToString(loginReq)
        assertTrue(jsonStr.contains("hiker@mountains.org"))
        assertTrue(jsonStr.contains("secretPassword"))

        val tokenResp = json.decodeFromString<AuthTokenResponseDto>(
            """{"access_token":"jwt-12345","token_type":"bearer","role":"tourist","user_id":"u-99","email":"hiker@mountains.org"}"""
        )
        assertEquals("jwt-12345", tokenResp.accessToken)
        assertEquals("tourist", tokenResp.role)
        assertEquals("u-99", tokenResp.userId)

        val meResp = json.decodeFromString<UserMeResponseDto>(
            """{"id":"u-99","email":"hiker@mountains.org","role":"tourist","is_active":true}"""
        )
        assertEquals("u-99", meResp.id)
        assertEquals("hiker@mountains.org", meResp.email)
        assertTrue(meResp.isActive)
    }

    @Test
    fun testLoginValidationRejectsInvalidEmail() {
        viewModel.onEmailChanged("not-an-email")
        viewModel.onPasswordChanged("validPassword123")
        viewModel.login()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("valid email"))
    }

    @Test
    fun testLoginValidationRejectsBlankPassword() {
        viewModel.onEmailChanged("hiker@mountains.org")
        viewModel.onPasswordChanged("")
        viewModel.login()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("password"))
    }

    @Test
    fun testRegisterValidationRejectsShortPassword() {
        viewModel.onEmailChanged("hiker@mountains.org")
        viewModel.onPasswordChanged("123")
        viewModel.onConfirmPasswordChanged("123")
        viewModel.register()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("at least 6 characters"))
    }

    @Test
    fun testRegisterValidationRejectsMismatchedPasswords() {
        viewModel.onEmailChanged("hiker@mountains.org")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("different123")
        viewModel.register()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("do not match"))
    }

    @Test
    fun testSuccessfulLoginNavigatesToOnboardingWhenNotOnboarded() = runTest(testDispatcher) {
        every { authRepository.hasCompletedPermissionsOnboarding() } returns false
        coEvery { authRepository.login(any(), any()) } returns Result.success(
            AuthTokenResponseDto(accessToken = "valid-token", role = "tourist")
        )

        var emittedEvent: AuthNavigationEvent? = null
        val job = launch {
            viewModel.navigationEvents.collect {
                emittedEvent = it
            }
        }

        viewModel.onEmailChanged("hiker@mountains.org")
        viewModel.onPasswordChanged("password123")
        viewModel.login()
        advanceUntilIdle()

        assertEquals(AuthNavigationEvent.NavigateToOnboarding, emittedEvent)
        job.cancel()
    }

    @Test
    fun testSuccessfulLoginNavigatesToHomeWhenAlreadyOnboarded() = runTest(testDispatcher) {
        every { authRepository.hasCompletedPermissionsOnboarding() } returns true
        coEvery { authRepository.login(any(), any()) } returns Result.success(
            AuthTokenResponseDto(accessToken = "valid-token", role = "tourist")
        )

        var emittedEvent: AuthNavigationEvent? = null
        val job = launch {
            viewModel.navigationEvents.collect {
                emittedEvent = it
            }
        }

        viewModel.onEmailChanged("hiker@mountains.org")
        viewModel.onPasswordChanged("password123")
        viewModel.login()
        advanceUntilIdle()

        assertEquals(AuthNavigationEvent.NavigateToHome, emittedEvent)
        job.cancel()
    }

    @Test
    fun testCompleteOnboardingUpdatesRepository() = runTest(testDispatcher) {
        var emittedEvent: AuthNavigationEvent? = null
        val job = launch {
            viewModel.navigationEvents.collect {
                emittedEvent = it
            }
        }

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify { authRepository.setPermissionsOnboardingCompleted(true) }
        assertEquals(AuthNavigationEvent.NavigateToHome, emittedEvent)
        job.cancel()
    }
}

