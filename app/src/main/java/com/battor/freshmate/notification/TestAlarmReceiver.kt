package com.battor.freshmate.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.battor.freshmate.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

/** 测试页状态区时间戳格式（HH:mm:ss）。 */
val TestTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** 本次测试通知的状态（无历史，只反映最近一次；receiver 与页面同进程，进程被杀回落 Idle）。 */
sealed interface TestAlarmState {
    data object Idle : TestAlarmState

    /** 已排：到点触发；UI 侧超过 [atMillis]+10 秒宽限仍是本态 → 判「闹钟未触发」。 */
    data class Scheduled(val atMillis: Long) : TestAlarmState
    data class Delivered(val firedAtMillis: Long) : TestAlarmState
    data class Failed(@StringRes val reasonRes: Int, val firedAtMillis: Long) : TestAlarmState
}

object TestAlarmTracker {
    val state = MutableStateFlow<TestAlarmState>(TestAlarmState.Idle)
}

/** 便捷格式化：epoch 毫秒 → HH:mm:ss（状态区展示用）。 */
fun formatTestTime(epochMillis: Long): String =
    TestTimeFormatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

/**
 * 测试页专用闹钟接收器：与过期提醒同渠道同外观（渠道/图标/标题一致），
 * 独立 component——requestCode 与 item 的 itemId*10+index 编码天然不冲突，
 * FLAG_UPDATE_CURRENT 使重复排布自然覆盖（重排不叠闹钟）。
 */
class TestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("ALARM test fired")
        val builder = NotificationCompat.Builder(context, ReminderIds.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.test_notification_body))
            .setAutoCancel(true)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        val firedAt = System.currentTimeMillis()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(REQUEST_CODE, builder.build())
            TestAlarmTracker.state.value = TestAlarmState.Delivered(firedAt)
            Timber.i("NOTIFY 测试通知已发")
        } else {
            // 权限未开不发通知，但把失败原因送到状态区——用户在页面上直接看到断在哪
            TestAlarmTracker.state.value =
                TestAlarmState.Failed(R.string.test_status_failed_perm, firedAt)
            Timber.w("NOTIFY 测试通知未发：通知权限未开")
        }
    }

    companion object {
        private const val REQUEST_CODE = 0

        /** 排测试闹钟：精确可用走 setExactAndAllowWhileIdle，否则回落非精确（与 ReminderScheduler 同策略）。 */
        fun schedule(context: Context, atMillis: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, TestAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
            TestAlarmTracker.state.value = TestAlarmState.Scheduled(atMillis)
            Timber.i("ALARM test schedule +%dms 精确=%b", atMillis - System.currentTimeMillis(), canExact)
        }
    }
}
