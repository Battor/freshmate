package com.battor.freshmate.update

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class ApkDownloader(client: OkHttpClient? = null) {

    // ★ 统一 15 秒总超时：慢/挂死服务器不会无限占用 IO 协程（默认 callTimeout 无上限）
    private val http = client ?: OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 清单之外的 apkUrl 必须是 https，防止明文传输被篡改。 */
    fun validateApkUrl(url: String) {
        require(url.startsWith("https://")) { "apkUrl 必须为 https: $url" }
    }

    /** 流式下载到 dest；onProgress(已下载字节, 总字节[未知为 -1])。 */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        validateApkUrl(url)
        dest.parentFile?.mkdirs()
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
        Timber.i("UPDATE 下载完成 %s (%d bytes)", dest.name, dest.length())
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
