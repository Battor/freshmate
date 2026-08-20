package com.battor.freshmate.notification

object ReminderIds {
    const val CHANNEL_ID = "expiry_reminders"

    /** 快照时点合并后最多 6 个（比例 3 + 绝对 3）。 */
    const val REMINDER_COUNT = 6

    /** requestCode = itemId * 10 + index（index 0..5），同一通知 id 也用它。 */
    fun requestCode(itemId: Long, index: Int): Int = (itemId * 10 + index).toInt()
}
