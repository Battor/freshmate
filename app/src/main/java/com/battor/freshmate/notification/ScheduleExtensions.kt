package com.battor.freshmate.notification

import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.computeReminderTimes
import com.battor.freshmate.util.expiryDateTime
import java.time.LocalDateTime

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
