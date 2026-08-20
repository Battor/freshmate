package com.battor.freshmate.notification

object ReminderIds {
    const val CHANNEL_ID = "expiry_reminders"

    /** 快照时点合并后最多 6 个（比例 3 + 绝对 3）。 */
    const val REMINDER_COUNT = 6

    /**
     * requestCode = itemId * 10 + index（index 0..5），同一通知 id 也用它。
     * itemId 上界由 require 保证：超出后 Long→Int 截断会与其它条目碰撞。
     */
    fun requestCode(itemId: Long, index: Int): Int {
        require(itemId in 0..Int.MAX_VALUE / 10L) { "itemId 超出 *10 编码安全范围: $itemId" }
        return (itemId * 10 + index).toInt()
    }
}
