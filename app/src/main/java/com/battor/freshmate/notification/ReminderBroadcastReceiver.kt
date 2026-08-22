package com.battor.freshmate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) return
        Timber.i("BCAST reminder itemId=%d", itemId)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    // 软删除条目不发通知（闹钟竞态时仍可能收到过期 PendingIntent）
                    val item = FoodItemDatabase.get(context).foodItemDao().getById(itemId)
                        ?.takeIf { it.deletedAt == null } ?: return@runCatching
                    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
                    val remaining = Duration.between(LocalDateTime.now(), expiry)
                    if (remaining.isNegative || remaining.isZero) return@runCatching
                    val builder = NotificationCompat.Builder(context, ReminderIds.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_reminder)
                        .setContentTitle(context.getString(R.string.notification_title))
                        .setContentText(
                            context.getString(
                                R.string.notification_body,
                                item.name,
                                formatRemaining(context.resources, remaining),
                            ),
                        )
                        .setAutoCancel(true)
                    val launchIntent =
                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launchIntent != null) {
                        val contentPi = android.app.PendingIntent.getActivity(
                            context,
                            itemId.toInt(),
                            launchIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                                android.app.PendingIntent.FLAG_IMMUTABLE,
                        )
                        builder.setContentIntent(contentPi)
                    }
                    if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        NotificationManagerCompat.from(context)
                            .notify(ReminderIds.requestCode(itemId, 0), builder.build())
                        Timber.i("NOTIFY 提醒已发 itemId=%d", itemId)
                    }
                }.onFailure {
                    Timber.w(it, "NOTIFY 处理提醒 itemId=%d 失败", itemId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        fun intent(context: Context, itemId: Long): Intent =
            Intent(context, ReminderBroadcastReceiver::class.java)
                .putExtra(EXTRA_ITEM_ID, itemId)
    }
}
