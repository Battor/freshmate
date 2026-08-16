package com.battor.freshmate.logging

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import timber.log.Timber

/**
 * 写文件的 Timber 树（设计文档 §4.2）：`MM-dd HH:mm:ss.SSS [tag] message`。
 * IO 失败静默——日志绝不能把 App 搞崩（logcat 由 DebugTree 承担可见性）。
 */
class FileTree(private val writer: DailyFileWriter) : Timber.Tree() {
    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val time = LocalDateTime.now().format(timeFormat)
        val stack = t?.let { "\n" + it.stackTraceToString() } ?: ""
        runCatching { writer.append("$time [$tag] $message$stack") }
    }
}
