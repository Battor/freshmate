package com.battor.freshmate.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.futureReminderTimes
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface ReminderScheduling {
    fun schedule(item: FoodItem)
    fun cancel(itemId: Long)
    suspend fun rescheduleAll()
}

class ReminderScheduler(private val context: Context) : ReminderScheduling {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(item: FoodItem) {
        cancel(item.id)
        val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
        val times = futureReminderTimes(expiry, item.shelfLifeDays, LocalDateTime.now())
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        Timber.i("ALARM schedule itemId=%d 到期=%s 时点数=%d 精确=%b", item.id, expiry, times.size, canExact)
        times.forEachIndexed { index, time ->
            val pi = broadcast(item.id, index)
            val atMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        }
    }

    override fun cancel(itemId: Long) {
        Timber.i("ALARM cancel itemId=%d", itemId)
        (0 until ReminderIds.REMINDER_COUNT).forEach { index ->
            alarmManager.cancel(broadcast(itemId, index))
        }
    }

    override suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val items = FoodItemDatabase.get(context).foodItemDao().getAllOnce()
        Timber.i("ALARM rescheduleAll ← %d items", items.size)
        val now = LocalDateTime.now()
        items.forEach { scheduleOrCancel(it, now) }
    }

    private fun broadcast(itemId: Long, index: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderIds.requestCode(itemId, index),
            ReminderBroadcastReceiver.intent(context, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
