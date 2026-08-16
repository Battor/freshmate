package com.battor.freshmate.logging

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DailyFileWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = LocalDateTime.of(2026, 8, 16, 10, 0)

    @Test fun `写入内容落到当天的文件`() {
        val w = DailyFileWriter(tmp.newFolder(), nowProvider = { now })
        w.append("hello")
        assertEquals(listOf("hello"), w.read(LocalDate.of(2026, 8, 16)))
    }

    @Test fun `跨天自动切新文件`() {
        val w = DailyFileWriter(tmp.newFolder(), nowProvider = { now })
        w.append("day1")
        now = now.plusDays(1)
        w.append("day2")
        assertEquals(listOf("day1"), w.read(LocalDate.of(2026, 8, 16)))
        assertEquals(listOf("day2"), w.read(LocalDate.of(2026, 8, 17)))
        assertEquals(
            listOf(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 16)),
            w.availableDates(),
        )
    }

    @Test fun `超过保留期的文件被清理`() {
        val dir = tmp.newFolder()
        File(dir, "2026-08-01.txt").writeText("old\n") // 距 08-16 超 7 天
        val w = DailyFileWriter(dir, nowProvider = { now })
        w.append("today")
        assertFalse(File(dir, "2026-08-01.txt").exists())
    }

    @Test fun `保留期内的文件不清理`() {
        val dir = tmp.newFolder()
        File(dir, "2026-08-09.txt").writeText("keep\n") // today-7，仍在保留期
        val w = DailyFileWriter(dir, nowProvider = { now })
        w.append("today")
        assertTrue(File(dir, "2026-08-09.txt").exists())
    }

    @Test fun `单文件超上限截断保留后半段`() {
        val w = DailyFileWriter(tmp.newFolder(), maxFileBytes = 60, nowProvider = { now })
        repeat(10) { w.append("line-$it-0123456789") } // 每行约 18 字节，累计超 60 后触发截断
        val lines = w.read(LocalDate.of(2026, 8, 16))
        assertTrue(lines.size in 1..5)
        assertEquals("line-9-0123456789", lines.last())
    }
}
