package com.battor.freshmate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                runCatching { ReminderScheduler(context).rescheduleAll() }
                    .onSuccess { Timber.i("BCAST %s 重排完成", action) }
                    .onFailure { Timber.w(it, "BCAST %s 重排失败", action) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
