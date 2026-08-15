package com.battor.freshmate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.battor.freshmate.notification.ReminderIds

class FreshMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            ReminderIds.CHANNEL_ID,
            "过期提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
