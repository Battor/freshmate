package com.battor.freshmate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.battor.freshmate.logging.DailyFileWriter
import com.battor.freshmate.logging.FileTree
import com.battor.freshmate.R
import com.battor.freshmate.notification.ReminderIds
import java.io.File
import timber.log.Timber

class FreshMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(FileTree(DailyFileWriter(File(filesDir, "logs"))))
        Timber.plant(Timber.DebugTree())
        Timber.i("APP 启动 versionName=%s versionCode=%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        val channel = NotificationChannel(
            ReminderIds.CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
