package com.battor.freshmate.notification

import android.content.Context
import com.battor.freshmate.data.DataStoreSettingsRepository
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.PushMode
import com.battor.freshmate.util.computeReminderTimes
import com.battor.freshmate.util.expiryDateTime
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

/** 未过期排提醒，已过期取消——保持调度与数据状态对称（主列表保存路径）。 */
fun ReminderScheduling.scheduleOrCancel(item: FoodItem, now: LocalDateTime) {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    if (expiry > now) schedule(item) else cancel(item.id)
}

/**
 * 还原专用（撤销删除/历史还原）：保存快照的时点可能已全部流逝（调度器只排未来时点），
 * 此时以还原时刻重算提醒——等价此刻新建，否则未过期条目还原后收不到任何到期提醒。
 */
fun ReminderScheduling.scheduleRestored(item: FoodItem, now: LocalDateTime) {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    when {
        expiry <= now -> cancel(item.id)
        item.reminderTimes.any { it > now } -> schedule(item)
        else -> schedule(item.copy(reminderTimes = computeReminderTimes(expiry, item.shelfLifeDays, now)))
    }
}

/**
 * 按当前设置重装摘要闹钟（幂等）：统一 → arm（今天已过的时间点自然排到明天）；
 * 逐个 → cancelAll（物品闹钟由保存/开机路径自行维护）。
 * 设置页切换、启动兜底、摘要自续、开机重排共用。
 */
suspend fun applyPushConfig(context: Context) {
    val settings = DataStoreSettingsRepository(context)
    val digest = DigestScheduler(context)
    if (settings.pushMode.first() == PushMode.DIGEST) {
        digest.arm(settings.digestTimes.first())
    } else {
        digest.cancelAll()
    }
}
