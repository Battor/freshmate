package com.battor.freshmate.logging

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 按天写日志文件（设计文档 §4.2）：dir/yyyy-MM-dd.txt。
 * - 追加写，synchronized 线程安全；跨天首写切新文件并清理超期文件；
 * - 单文件达 [maxFileBytes] 时截断，保留后半段（约一半体积）。
 * 日期解析失败的文件不动（不是我们的日志）。
 */
class DailyFileWriter(
    private val dir: File,
    private val retentionDays: Long = 7,
    private val maxFileBytes: Long = 2L * 1024 * 1024,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val lock = Any()
    private var currentDate: LocalDate? = null
    private var writer: BufferedWriter? = null

    init {
        dir.mkdirs()
    }

    fun append(line: String) = synchronized(lock) {
        val today = nowProvider().toLocalDate()
        if (currentDate != today) {
            writer?.close()
            currentDate = today
            cleanup(today)
            writer = newWriter(today)
        }
        val file = fileFor(today)
        if (file.length() >= maxFileBytes) {
            // 先关句柄再截断（append 模式的偏移量在外部重写后不可信）
            writer!!.close()
            truncate(file)
            writer = newWriter(today)
        }
        writer!!.write(line)
        writer!!.newLine()
        writer!!.flush()
    }

    /** 可查看的日志日期，新→旧。 */
    fun availableDates(): List<LocalDate> =
        dir.listFiles().orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it.name.removeSuffix(".txt")) }.getOrNull() }
            .sortedDescending()

    fun read(date: LocalDate): List<String> =
        fileFor(date).takeIf { it.exists() }?.readLines() ?: emptyList()

    private fun fileFor(date: LocalDate) = File(dir, "$date.txt")

    private fun newWriter(date: LocalDate): BufferedWriter = BufferedWriter(
        OutputStreamWriter(FileOutputStream(fileFor(date), true), Charsets.UTF_8),
    )

    private fun cleanup(today: LocalDate) {
        val cutoff = today.minusDays(retentionDays)
        dir.listFiles()?.forEach { f ->
            val date = runCatching { LocalDate.parse(f.name.removeSuffix(".txt")) }.getOrNull()
            if (date != null && date.isBefore(cutoff)) f.delete()
        }
    }

    private fun truncate(file: File) {
        val lines = file.readLines()
        val kept = ArrayList<String>()
        var size = 0L
        for (i in lines.indices.reversed()) {
            val lineBytes = lines[i].toByteArray().size + 1L
            if (size + lineBytes > maxFileBytes / 2) break
            kept.add(lines[i])
            size += lineBytes
        }
        kept.reverse()
        file.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }
}
