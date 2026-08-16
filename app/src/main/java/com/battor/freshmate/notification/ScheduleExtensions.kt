package com.battor.freshmate.notification

import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.expiryDateTime
import java.time.LocalDateTime

/** 未过期排提醒，已过期取消——保持调度与数据状态对称（主列表与历史页还原共用）。 */
fun ReminderScheduling.scheduleOrCancel(item: FoodItem, now: LocalDateTime) {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    if (expiry > now) schedule(item) else cancel(item.id)
}
