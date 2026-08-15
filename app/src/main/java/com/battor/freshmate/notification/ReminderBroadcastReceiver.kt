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

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = FoodItemDatabase.get(context).foodItemDao().getById(itemId)
                    ?: return@launch
                val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
                val remaining = Duration.between(LocalDateTime.now(), expiry)
                if (remaining.isNegative || remaining.isZero) return@launch
                val notification = NotificationCompat.Builder(context, ReminderIds.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_reminder)
                    .setContentTitle("食刻 FreshMate")
                    .setContentText("「${item.name}」还有 ${formatRemaining(remaining)} 到期")
                    .setAutoCancel(true)
                    .build()
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    NotificationManagerCompat.from(context)
                        .notify(ReminderIds.requestCode(itemId, 0), notification)
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
