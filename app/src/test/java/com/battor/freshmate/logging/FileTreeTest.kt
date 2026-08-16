package com.battor.freshmate.logging

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber

class FileTreeTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `tag为调用类名且堆栈不重复`() {
        val dir = tmp.newFolder()
        Timber.plant(FileTree(DailyFileWriter(dir)))
        try {
            Timber.i("普通消息")
            Timber.i(RuntimeException("爆炸"), "带异常消息")
        } finally {
            Timber.uprootAll()
        }
        val lines = DailyFileWriter(dir).read(LocalDate.now())
        val normal = lines.first { it.contains("普通消息") }
        assertTrue("应含调用类名 tag: $normal", normal.contains("[FileTreeTest]"))
        assertFalse("tag 不应为 null: $normal", normal.contains("[null]"))
        // 堆栈含换行，会被 read() 拆成多行，因此统计整个文件中的出现次数（恰一次 = 未重复追加）
        val withError = lines.first { it.contains("带异常消息") }
        assertTrue("异常消息行应存在", withError.isNotBlank())
        val stackCount = Regex("java\\.lang\\.RuntimeException").findAll(lines.joinToString("\n")).count()
        assertTrue("堆栈应恰好出现一次，实际 $stackCount 次", stackCount == 1)
    }
}
