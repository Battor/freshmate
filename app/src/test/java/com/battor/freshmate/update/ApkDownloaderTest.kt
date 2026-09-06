package com.battor.freshmate.update

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `sha256与已知值一致`() {
        val f = tmp.newFile().apply { writeText("hello") }
        // sha256("hello") 公认值
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            runBlocking { ApkDownloader().sha256(f) },
        )
    }

    @Test fun `httpsUrl校验拒绝http`() {
        val e1 = kotlin.runCatching { ApkDownloader().validateApkUrl("https://host/a.apk") }.exceptionOrNull()
        assertNull(e1) // https 通过，无异常
        val e2 = kotlin.runCatching { ApkDownloader().validateApkUrl("http://host/a.apk") }.exceptionOrNull()
        assertNotNull(e2) // http 被拒
        val bad = kotlin.runCatching { ApkDownloader().validateApkUrl("ftp://host/a.apk") }.exceptionOrNull()
        assertNotNull(bad)
    }
}
