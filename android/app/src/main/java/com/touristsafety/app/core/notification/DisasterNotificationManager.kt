package com.touristsafety.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.touristsafety.app.MainActivity
import com.touristsafety.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisasterNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "disaster_alerts_critical_v1"
        const val CHANNEL_NAME = "Critical Disaster & Hazard Alerts"
        const val CHANNEL_DESC = "High priority warnings for flash floods, landslides, and hazardous weather."
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
                setSound(soundUri, audioAttributes)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
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
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                manager.notify(alertId.hashCode(), notification)
            }
        } catch (e: SecurityException) {
            // Handled gracefully if notification permission is denied
        }
    }
}

