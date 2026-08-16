package com.battor.freshmate.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String?,
)

/** 纯函数解析（设计文档 §5.1）；仅用运行时库，无需编译插件。 */
object ManifestParser {
    fun parse(json: String): UpdateManifest {
        val obj = Json.parseToJsonElement(json).jsonObject
        fun str(key: String) = obj.getValue(key).jsonPrimitive.content
        return UpdateManifest(
            versionCode = obj.getValue("versionCode").jsonPrimitive.int,
            versionName = str("versionName"),
            apkUrl = str("apkUrl"),
            sha256 = obj["sha256"]?.jsonPrimitive?.contentOrNull,
            notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
        )
    }
}

fun isUpdateAvailable(manifest: UpdateManifest, currentVersionCode: Int): Boolean =
    manifest.versionCode > currentVersionCode
