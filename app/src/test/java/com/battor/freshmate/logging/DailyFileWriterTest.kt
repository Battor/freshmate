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

    @Test fun `打开新文件失败后下一次append重试`() {
        val dir = tmp.newFolder()
        val w = DailyFileWriter(dir, nowProvider = { now })
        w.append("ok")
        now = now.plusDays(1)
        // 制造失败：把跨天目标文件路径做成目录，newWriter 必然抛 FileNotFoundException
        File(dir, "2026-08-17.txt").mkdirs()
        val boom = runCatching { w.append("boom") }
        assertTrue(boom.isFailure)
        // 恢复后，同一天内再次 append 也应自愈重试成功（而非永远失败在已关闭的旧 writer 上）
        File(dir, "2026-08-17.txt").delete()
        w.append("recovered")
        assertEquals(listOf("recovered"), w.read(LocalDate.of(2026, 8, 17)))
    }
}
