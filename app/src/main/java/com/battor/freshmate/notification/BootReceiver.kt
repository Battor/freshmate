package com.battor.freshmate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.battor.freshmate.data.DataStoreSettingsRepository
import com.battor.freshmate.data.PushMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        Timber.i("BCAST %s", action)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    // 统一 → applyPushConfig 装摘要闹钟；逐个 → 清摘要闹钟 + 重排物品闹钟
                    // （重启后闹钟清空，无需再取消另一类）
                    applyPushConfig(context)
                    if (DataStoreSettingsRepository(context).pushMode.first() != PushMode.DIGEST) {
                        ReminderScheduler(context).rescheduleAll()
                    }
                }
                    .onSuccess { Timber.i("BCAST %s 重排完成", action) }
                    .onFailure { Timber.w(it, "BCAST %s 重排失败", action) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
