package com.battor.freshmate.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class ApkDownloader(client: OkHttpClient? = null) {

    // 不设 callTimeout（它会封顶整个下载时长，大 APK 慢网必失败）；
    // 沿用默认 readTimeout（10s）：只在读挂死时中断，不限制总大小/时长
    private val http = client ?: OkHttpClient()

    /** 清单之外的 apkUrl 必须是 https，防止明文传输被篡改。 */
    fun validateApkUrl(url: String) {
        require(url.startsWith("https://", ignoreCase = true)) { "apkUrl 必须为 https: $url" }
    }

    /** 流式下载到 dest；onProgress(已下载字节, 总字节[未知为 -1])。失败时清理半截文件。 */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        validateApkUrl(url)
        dest.parentFile?.mkdirs()
        try {
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("空响应体")
                val total = body.contentLength()
                dest.outputStream().use { out ->
                    val input = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        onProgress(copied, total)
                    }
                }
            }
        } catch (e: Exception) {
            dest.delete() // 半截文件不可安装也不可续传，删掉防误装
            throw e
        }
        Timber.i("UPDATE 下载完成 %s (%d bytes)", dest.name, dest.length())
    }

    /** 整文件哈希是重 IO：必须挂 IO 调度器，调用方（viewModelScope=Main）直调会冻结 UI。 */
    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** 更新 APK 目录名（须与 res/xml/file_paths.xml 的 cache-path path 一致）。 */
        const val UPDATES_DIR = "updates"

        /** 更新 APK 的标准落盘位置：cacheDir/updates/freshmate-update.apk。 */
        fun apkFile(context: android.content.Context): File =
            File(File(context.cacheDir, UPDATES_DIR), "freshmate-update.apk")
    }
}
