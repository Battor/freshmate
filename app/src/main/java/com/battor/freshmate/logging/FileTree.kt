package com.battor.freshmate.logging

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import timber.log.Timber

/**
 * 写文件的 Timber 树（设计文档 §4.2）：`MM-dd HH:mm:ss.SSS [tag] message`。
 * 继承 DebugTree 以获得调用类名的 tag 推断（基类 Tree 只认显式 Timber.tag()，否则为 null）；
 * 堆栈已由 Timber prepareLog 拼进 message，这里不再重复追加。
 * 覆写 log() 且不调 super——不写 logcat（logcat 由 FreshMateApp 另种的 DebugTree 承担）；
 * 一切失败静默——日志绝不能把 App 搞崩。
 */
class FileTree(private val writer: DailyFileWriter) : Timber.DebugTree() {
    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        runCatching {
            val time = LocalDateTime.now().format(timeFormat)
            writer.append("$time [$tag] $message")
        }
    }
}
