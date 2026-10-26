package com.touristsafety.app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.touristsafety.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated safety siren player.
 * Plays the bundled safety_alert.wav dual-tone alarm with USAGE_ALARM,
 * requests audio focus, triggers distinctive emergency vibration,
 * and automatically times out after 20 seconds.
 */
@Singleton
class SafetyAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)

    @Synchronized
    fun playSafetyAlert(soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        stopSafetyAlert()

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        if (soundEnabled) {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                // Request Audio Focus
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .build()
                    focusRequest = req
                    audioManager?.requestAudioFocus(req)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.requestAudioFocus(
                        null,
                        AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                }

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    val afd = context.resources.openRawResourceFd(R.raw.safety_alert)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (t: Throwable) {
                android.util.Log.e("SafetyAlertPlayer", "Failed to start safety alert audio", t)
            }
        }

        if (vibrationEnabled) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(vibrationPattern, 0)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(vibrationPattern, 0)
                }
            } catch (t: Throwable) {
                android.util.Log.e("SafetyAlertPlayer", "Failed to start safety alert vibration", t)
            }
        }

        // 20-second safety timeout
        timeoutJob = scope.launch {
            delay(20_000L)
            stopSafetyAlert()
        }
    }

    @Synchronized
    fun stopSafetyAlert() {
        timeoutJob?.cancel()
        timeoutJob = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (t: Throwable) {
            android.util.Log.e("SafetyAlertPlayer", "Error releasing MediaPlayer", t)
        }
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (t: Throwable) {
            android.util.Log.e("SafetyAlertPlayer", "Error canceling vibrator", t)
        }
        vibrator = null

        // Abandon audio focus
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (t: Throwable) {
            android.util.Log.e("SafetyAlertPlayer", "Error abandoning audio focus", t)
        }
        focusRequest = null
    }

    fun isAlertActive(): Boolean {
        return mediaPlayer?.isPlaying == true || timeoutJob != null
    }
}

