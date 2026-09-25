package com.battor.freshmate.data

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsParsingTest {
    @Test
    fun `推送方式损坏值回落统一`() {
        assertEquals(PushMode.DIGEST, parsePushMode(null))
        assertEquals(PushMode.DIGEST, parsePushMode("garbage"))
        assertEquals(PushMode.DIGEST, parsePushMode(""))
        assertEquals(PushMode.INDIVIDUAL, parsePushMode("INDIVIDUAL"))
    }

    @Test
    fun `时间串解析两段`() {
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(21, 5)),
            parseDigestTimes("08:00,21:05"),
        )
        assertEquals(listOf(LocalTime.of(16, 30)), parseDigestTimes("16:30"))
    }

    @Test
    fun `时间串任一段损坏整串回落默认`() {
        assertEquals(DEFAULT_DIGEST_TIMES, parseDigestTimes(null))
        assertEquals(DEFAULT_DIGEST_TIMES, parseDigestTimes(""))
        assertEquals(DEFAULT_DIGEST_TIMES, parseDigestTimes("08:00,bad"))
        assertEquals(DEFAULT_DIGEST_TIMES, parseDigestTimes("08:00,"))
    }
}
