package com.battor.freshmate.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import timber.log.Timber

/** 下一次出现时刻：今天的该时刻严格晚于 now → 今天，否则（含恰好等于）→ 明天。 */
fun nextOccurrence(time: LocalTime, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today > now) today else today.plusDays(1)
}

interface DigestScheduling {
    fun arm(times: List<LocalTime>)
    fun cancelAll()
}

/** 摘要时间点槽位与 requestCode/通知 id（负数，与物品闹钟 itemId*10+index 恒 ≥0 天然不冲突）。 */
object DigestIds {
    /** 摘要时间点槽位数（最多 2 个推送时间）。 */
    const val SLOT_COUNT = 2

    fun requestCode(slot: Int): Int = -1 - slot
    fun notificationId(slot: Int): Int = requestCode(slot)
}

/** 每日摘要闹钟：setExactAndAllowWhileIdle 一次性，由 DailyDigestReceiver/各兜底点自续。 */
class DigestScheduler(private val context: Context) : DigestScheduling {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun arm(times: List<LocalTime>) {
        // 先清全部槽位再装，槽位减少（2→1）时不留孤儿闹钟；FLAG_UPDATE_CURRENT 天然覆盖
        cancelAll()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        Timber.i("ALARM digest arm 时点数=%d 精确=%b", times.size, canExact)
        times.take(DigestIds.SLOT_COUNT).forEachIndexed { slot, time ->
            val at = nextOccurrence(time, LocalDateTime.now())
            val atMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, broadcast(slot))
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, broadcast(slot))
            }
        }
    }

    override fun cancelAll() {
        Timber.i("ALARM digest cancelAll")
        (0 until DigestIds.SLOT_COUNT).forEach { slot -> alarmManager.cancel(broadcast(slot)) }
    }

    private fun broadcast(slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DigestIds.requestCode(slot),
            Intent(context, DailyDigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
