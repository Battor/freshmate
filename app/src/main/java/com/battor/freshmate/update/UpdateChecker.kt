package com.battor.freshmate.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class UpdateChecker(
    private val manifestUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    /** 结果从不抛异常——失败走 Result（设计文档 §8：启动静默/手动提示）。 */
    suspend fun fetch(): Result<UpdateManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url(manifestUrl).build()).execute()
            response.use {
                if (!it.isSuccessful) error("HTTP ${it.code}")
                ManifestParser.parse(it.body!!.string())
            }
        }.onFailure { Timber.i(it, "UPDATE 检查失败") }
            .onSuccess {
                Timber.i("UPDATE 检查 ← versionCode=%d versionName=%s", it.versionCode, it.versionName)
            }
    }
}
