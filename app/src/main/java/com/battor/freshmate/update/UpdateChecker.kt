package com.battor.freshmate.update

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class UpdateChecker(
    private val manifestUrl: String,
    client: OkHttpClient? = null,
) {
    // ★ 统一 15 秒总超时：慢/挂死服务器不会无限占用 IO 协程（默认 callTimeout 无上限）
    private val http = client ?: OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 结果从不抛异常——失败走 Result（设计文档 §8：启动静默/手动提示）。 */
    suspend fun fetch(): Result<UpdateManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val response = http.newCall(Request.Builder().url(manifestUrl).build()).execute()
            response.use {
                if (!it.isSuccessful) error("HTTP ${it.code}")
                val body = it.body ?: error("空响应体")
                ManifestParser.parse(body.string())
            }
        }.onFailure { Timber.w(it, "UPDATE 检查失败") }
            .onSuccess {
                Timber.i("UPDATE 检查 ← versionCode=%d versionName=%s", it.versionCode, it.versionName)
            }
    }
}
