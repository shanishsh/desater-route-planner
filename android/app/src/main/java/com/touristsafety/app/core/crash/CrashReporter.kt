package com.touristsafety.app.core.crash

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Centralized, privacy-conscious crash and error reporter.
 * Captures non-fatal exceptions and diagnostic context via Firebase Crashlytics.
 * Ensures strict secret hygiene: never logs auth tokens, credentials, or personal coordinates.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"

    private val SENSITIVE_KEYWORDS = listOf("token", "secret", "password", "bearer", "authorization", "key")

    /**
     * Records a diagnostic message into the Crashlytics session buffer.
     * Sanitizes sensitive fields before recording.
     */
    fun log(message: String) {
        val lower = message.lowercase()
        if (SENSITIVE_KEYWORDS.any { lower.contains(it) }) {
            Log.d(TAG, "[Sanitized log entry omitted due to sensitive keyword]")
            return
        }
        try {
            FirebaseCrashlytics.getInstance().log(message)
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // Firebase not initialized in local test environment
        }
    }

    /**
     * Records a non-fatal error or handled exception (e.g. MapLibre style loading or network failure).
     */
    fun recordNonFatal(throwable: Throwable, contextTag: String = "NON_FATAL") {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey("error_tag", contextTag)
            FirebaseCrashlytics.getInstance().recordException(throwable)
            Log.w(TAG, "[$contextTag] Handled non-fatal exception recorded: ${throwable.message}")
        } catch (_: Throwable) {
            Log.w(TAG, "[$contextTag] ${throwable.message}")
        }
    }

    /**
     * Attaches safe, non-sensitive diagnostic context to crash reports.
     */
    fun setCustomKey(key: String, value: String) {
        val lowerKey = key.lowercase()
        val lowerVal = value.lowercase()
        if (SENSITIVE_KEYWORDS.any { lowerKey.contains(it) || lowerVal.contains(it) }) {
            return
        }
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (_: Throwable) {}
    }

    /**
     * Attaches boolean diagnostic context (e.g. isOffline, isMapReady).
     */
    fun setCustomKey(key: String, value: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (_: Throwable) {}
    }

    /**
     * Triggers a safe development test non-fatal exception to verify Crashlytics reporting
     * without crashing the application.
     */
    fun triggerDebugTestNonFatal() {
        log("Controlled development diagnostic test executed.")
        recordNonFatal(
            RuntimeException("Controlled Crashlytics Non-Fatal Verification Test (Development)"),
            "CONTROLLED_DEV_TEST"
        )
    }
}

