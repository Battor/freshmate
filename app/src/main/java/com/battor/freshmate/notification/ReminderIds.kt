package com.battor.freshmate.notification

object ReminderIds {
    const val CHANNEL_ID = "expiry_reminders"
    const val REMINDER_COUNT = 3

    /** requestCode = itemId * 10 + index（index 0..2），同一通知 id 也用它。 */
    fun requestCode(itemId: Long, index: Int): Int = (itemId * 10 + index).toInt()
}
