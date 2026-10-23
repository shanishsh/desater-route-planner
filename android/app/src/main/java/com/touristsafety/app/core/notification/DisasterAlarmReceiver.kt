package com.touristsafety.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DisasterAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: DisasterNotificationManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_STOP_ALARM) {
            notificationManager.stopDisasterSiren()
        }
    }

    companion object {
        const val ACTION_STOP_ALARM = "com.touristsafety.app.ACTION_STOP_DISASTER_ALARM"
    }
}

