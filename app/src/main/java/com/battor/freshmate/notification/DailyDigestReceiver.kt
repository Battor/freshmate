package com.battor.freshmate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.battor.freshmate.R
import com.battor.freshmate.data.DataStoreSettingsRepository
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.data.PushMode
import com.battor.freshmate.util.digestTiers
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 每日摘要接收器：触发时实时查库分桶（数据永远新鲜，spec 方案 A），发 1 天/3 天两档通知，
 * 每档通知 id 固定（新替旧，通知栏最多 2 条摘要），最后 applyPushConfig 自续明天。
 */
class DailyDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("BCAST digest")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val settings = DataStoreSettingsRepository(context)
                    // 切到逐个模式后残留的 PendingIntent：不自续、直接返回
                    if (settings.pushMode.first() != PushMode.DIGEST) {
                        Timber.i("BCAST digest 逐个模式，跳过且不自续")
                        return@runCatching
                    }
                    val items = FoodItemDatabase.get(context).foodItemDao().getAllOnce()
                        .filter { it.deletedAt == null }
                    val tiers = digestTiers(items, LocalDateTime.now())
                    postTier(context, slot = 0, items = tiers.dueWithin1d, labelRes = R.string.status_due_1d)
                    postTier(context, slot = 1, items = tiers.dueWithin3d, labelRes = R.string.status_due_3d)
                    applyPushConfig(context) // 自续：按设置重装全部时间点
                }.onFailure { Timber.w(it, "BCAST digest 处理失败") }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 发一档通知；档为空则清残留旧通知（通知不自动消失，不清会永远陈述过期信息）。 */
    private fun postTier(
        context: Context,
        slot: Int,
        items: List<FoodItem>,
        labelRes: Int,
    ) {
        // from(context) 内联构造（与 ReminderBroadcastReceiver 同款写法）
        val nm = NotificationManagerCompat.from(context)
        val id = DigestIds.notificationId(slot)
        if (items.isEmpty()) {
            nm.cancel(id)
            return
        }
        val builder = NotificationCompat.Builder(context, ReminderIds.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(
                context.getString(R.string.digest_title, context.getString(labelRes), items.size),
            )
            .setContentText(
                items.joinToString(context.getString(R.string.digest_name_separator)) { it.name },
            )
            .setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    items.forEach { item ->
                        val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
                        val remaining = Duration.between(LocalDateTime.now(), expiry)
                        style.addLine(
                            context.getString(
                                R.string.digest_line,
                                item.name,
                                context.getString(
                                    R.string.card_status_remaining,
                                    formatRemaining(context.resources, remaining),
                                ),
                            ),
                        )
                    }
                },
            )
            .setAutoCancel(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            builder.setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    slot,
                    launch,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        // from(context) 链式调用 + 同函数守卫：与 ReminderBroadcastReceiver 同款写法（lint 可见权限已检查）
        if (nm.areNotificationsEnabled()) {
            nm.notify(id, builder.build())
            Timber.i("NOTIFY digest 档位%d %d 项", slot, items.size)
        }
    }
}
