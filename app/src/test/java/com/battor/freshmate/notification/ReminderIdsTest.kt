package com.battor.freshmate.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderIdsTest {
    @Test
    fun `同一食品不同序号的请求码不同`() {
        assertEquals(120L, ReminderIds.requestCode(12L, 0).toLong())
        assertEquals(121L, ReminderIds.requestCode(12L, 1).toLong())
        assertEquals(122L, ReminderIds.requestCode(12L, 2).toLong())
    }

    @Test
    fun `不同食品请求码不冲突`() {
        assertEquals(130L, ReminderIds.requestCode(13L, 0).toLong())
    }
}
