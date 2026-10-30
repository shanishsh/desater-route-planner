package com.touristsafety.app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Android TextToSpeech Assistant for active trek guidance and safety alerts.
 *
 * Speaks concise, walking-friendly voice alerts directly to the trekker.
 * Respects user mute preferences, audio focus, and enforces speech deduplication.
 */
@Singleton
class TrekVoiceAssistant @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TrekVoiceAssistant"
        private const val SPEECH_COOLDOWN_MS = 10 * 60 * 1000L // 10-minute suppression of identical phrases
    }

    private var tts: TextToSpeech? = null
    private var isInitialized: Boolean = false
    private var isVoiceEnabled: Boolean = true

    // Phrase deduplication map: key = text hash, value = last spoken timestamp
    private val speechCooldowns = ConcurrentHashMap<String, Long>()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to instantiate TextToSpeech: ${t.message}", t)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setPitch(1.0f)
                engine.setSpeechRate(0.95f) // Slightly slower for crisp trail audio

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                engine.setAudioAttributes(attributes)

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS speech started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS speech completed: $utteranceId")
                    }

                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS speech error on utterance: $utteranceId")
                    }
                })

                isInitialized = true
                Log.i(TAG, "TrekVoiceAssistant initialized successfully.")
            }
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    /**
     * Speaks a safety guidance message.
     * Critical alerts immediately flush the speech queue; standard updates append to it.
     */
    @Synchronized
    fun speakSafetyAlert(text: String, isCritical: Boolean = false, now: Long = System.currentTimeMillis()) {
        if (!isVoiceEnabled || text.isBlank() || !isInitialized) return

        val textHash = text.trim().lowercase()
        val lastSpoken = speechCooldowns[textHash] ?: 0L

        // Suppress repeated identical speech unless critical
        if (!isCritical && (now - lastSpoken) < SPEECH_COOLDOWN_MS) {
            Log.d(TAG, "Suppressed duplicate speech utterance within cooldown.")
            return
        }

        speechCooldowns[textHash] = now

        val queueMode = if (isCritical) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "trek_alert_${UUID.randomUUID()}"

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        try {
            tts?.speak(text, queueMode, params, utteranceId)
        } catch (t: Throwable) {
            Log.e(TAG, "TTS speak failed: ${t.message}", t)
        }
    }

    @Synchronized
    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping TTS: ${t.message}")
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        isVoiceEnabled = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    fun isVoiceEnabled(): Boolean = isVoiceEnabled

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (t: Throwable) {
            Log.w(TAG, "Error shutting down TTS: ${t.message}")
        }
    }
}

