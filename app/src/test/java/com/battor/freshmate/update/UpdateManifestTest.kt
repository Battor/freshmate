package com.battor.freshmate.update

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val json = """
        {"versionCode": 2, "versionName": "1.1.0",
         "apkUrl": "https://host/apk-1.1.0.apk", "sha256": "AbCd12", "notes": "修复与历史页"}
    """.trimIndent()

    @Test fun `解析完整清单`() {
        val m = ManifestParser.parse(json)
        assertEquals(2, m.versionCode)
        assertEquals("1.1.0", m.versionName)
        assertEquals("https://host/apk-1.1.0.apk", m.apkUrl)
        assertEquals("AbCd12", m.sha256)
        assertEquals("修复与历史页", m.notes)
    }

    @Test fun `sha256与notes可省略`() {
        val m = ManifestParser.parse("""{"versionCode":1,"versionName":"1.0","apkUrl":"u"}""")
        assertNull(m.sha256)
        assertNull(m.notes)
    }

    @Test fun `坏JSON抛异常`() {
        try {
            ManifestParser.parse("not json")
            throw AssertionError("应抛异常")
        } catch (e: SerializationException) {
            // 预期
        } catch (e: IllegalArgumentException) {
            // 预期（Json 解析错误的另一种包装）
        }
    }

    @Test fun `版本比对只在versionCode更大时有更新`() {
        val m = ManifestParser.parse(json)
        assertTrue(isUpdateAvailable(m, 1))
        assertFalse(isUpdateAvailable(m, 2))
        assertFalse(isUpdateAvailable(m, 3))
    }
}
