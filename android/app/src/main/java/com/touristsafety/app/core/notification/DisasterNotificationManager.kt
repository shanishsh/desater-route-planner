package com.touristsafety.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.touristsafety.app.MainActivity
import com.touristsafety.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisasterNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DisasterNotification"
        const val CHANNEL_ID = "disaster_alerts_siren_alarm_v3"
        const val CHANNEL_NAME = "🚨 Disaster Emergency Sirens & Warnings"
        const val CHANNEL_DESC = "Maximum priority emergency sirens, alarm tones, and flash flood/landslide warnings."
        const val TEST_ALARM_NOTIFICATION_ID = 99991
        @Deprecated("Use TEST_ALARM_NOTIFICATION_ID")
        const val DUMMY_NOTIFICATION_ID = TEST_ALARM_NOTIFICATION_ID
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeRingtone: Ringtone? = null
    private var activeVibrator: Vibrator? = null
    private var sirenTimeoutJob: Job? = null

    private val _isSirenPlaying = MutableStateFlow(false)
    val isSirenPlaying: StateFlow<Boolean> = _isSirenPlaying.asStateFlow()

    init {
        createNotificationChannel()
    }

    private fun getEmergencySoundUri(): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = getEmergencySoundUri()
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 1200)
                setSound(soundUri, audioAttributes)
                setBypassDnd(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Plays a loud emergency alarm ring sound and vibration pattern.
     * Rings continuously on the ALARM stream until stopped or after 25s timeout.
     */
    fun playDisasterSiren() {
        try {
            stopDisasterSiren() // Stop any previous playback

            val soundUri = getEmergencySoundUri()
            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            ringtone?.audioAttributes = audioAttributes
            ringtone?.play()
            activeRingtone = ringtone

            // Trigger emergency vibration pattern
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            activeVibrator = vibrator

            val pattern = longArrayOf(0, 800, 300, 800, 300, 1200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // repeat index 0
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }

            _isSirenPlaying.value = true

            // Auto-timeout after 25 seconds to protect battery and user comfort
            sirenTimeoutJob = scope.launch {
                delay(25_000L)
                stopDisasterSiren()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing disaster siren ring sound: ${e.message}", e)
        }
    }

    /**
     * Stops the audible siren ring sound and cancels vibration.
     */
    fun stopDisasterSiren() {
        try {
            sirenTimeoutJob?.cancel()
            sirenTimeoutJob = null

            activeRingtone?.stop()
            activeRingtone = null

            activeVibrator?.cancel()
            activeVibrator = null

            _isSirenPlaying.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping disaster siren: ${e.message}", e)
        }
    }

    /**
     * Triggers a diagnostic emergency alarm test with audible siren and vibration pattern
     * to verify audio stream and notification permissions. Clearly marked as [TEST ALARM].
     */
    fun triggerTestDisasterAlarm(
        title: String = "Emergency Siren Audio & Vibration Test",
        instructions: String = "Device diagnostic test in progress. Audio alarm stream and vibration pattern verified. This is an intentional device hardware check; no active disaster."
    ) {
        // 1. Play loud siren ring sound & vibration
        playDisasterSiren()

        // 2. Build and post diagnostic notification
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_ALERT_ID", "test-alarm-diagnostic")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            TEST_ALARM_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action to silence siren right from the notification drawer
        val silenceIntent = Intent(context, DisasterAlarmReceiver::class.java).apply {
            action = DisasterAlarmReceiver.ACTION_STOP_ALARM
        }
        val silencePendingIntent = PendingIntent.getBroadcast(
            context,
            TEST_ALARM_NOTIFICATION_ID + 1,
            silenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullBody = "🧪 [DIAGNOSTIC TEST - NOT A REAL DISASTER]\nAction: $instructions\n\nEmergency siren and vibration channel verified operational."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🧪 [TEST ALARM] $title")
            .setContentText(fullBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "SILENCE ALARM", silencePendingIntent)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                manager.notify(TEST_ALARM_NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted for test alarm: ${e.message}")
        }
    }

    @Deprecated("Use triggerTestDisasterAlarm()", ReplaceWith("triggerTestDisasterAlarm(title, instructions)"))
    fun triggerDummyDisasterAlert(
        title: String = "Emergency Siren Audio & Vibration Test",
        instructions: String = "Device diagnostic test in progress. Audio alarm stream and vibration pattern verified. This is an intentional device hardware check; no active disaster."
    ) {
        triggerTestDisasterAlarm(title, instructions)
    }

    fun showDisasterNotification(
        alertId: String,
        title: String,
        instructions: String,
        category: String,
        issuingAuthority: String,
        isCached: Boolean = false,
        isSimulated: Boolean = false,
        isTestAlarm: Boolean = false
    ) {
        // If critical or test alarm, start ring sound
        if (!isCached || isTestAlarm) {
            playDisasterSiren()
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_ALERT_ID", alertId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val silenceIntent = Intent(context, DisasterAlarmReceiver::class.java).apply {
            action = DisasterAlarmReceiver.ACTION_STOP_ALARM
        }
        val silencePendingIntent = PendingIntent.getBroadcast(
            context,
            alertId.hashCode() + 100,
            silenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = when {
            isTestAlarm -> "🧪 [TEST ALARM; NO REAL DISASTER] $title"
            isCached -> "⚠️ [CACHED HAZARD WARNING] $title"
            isSimulated -> "🧪 [SIMULATION] $title"
            else -> "🚨 [DISASTER WARNING] $title"
        }

        val footer = if (isCached) {
            "\n\n[OFFLINE CACHED WARNING]: Saved from $issuingAuthority. Newer conditions might not be known offline."
        } else {
            "\n\nSource: $issuingAuthority"
        }

        val fullBody = "Hazard: ${category.uppercase()}\nAction: $instructions$footer"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(displayTitle)
            .setContentText(fullBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "SILENCE ALARM", silencePendingIntent)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                manager.notify(alertId.hashCode(), notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
        }
    }
}
