package com.battor.freshmate.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test fun `空列表序列化为空串且往返一致`() {
        assertEquals("", converters.localDateTimeListToString(emptyList()))
        assertEquals(emptyList<LocalDateTime>(), converters.stringToLocalDateTimeList(""))
    }

    @Test fun `多元素往返一致且顺序保留`() {
        val times = listOf(
            LocalDateTime.of(2026, 8, 19, 10, 0),
            LocalDateTime.of(2026, 8, 21, 10, 0),
        )
        assertEquals(times, converters.stringToLocalDateTimeList(converters.localDateTimeListToString(times)))
    }

    @Test fun `null入参读回空列表`() {
        assertEquals(emptyList<LocalDateTime>(), converters.stringToLocalDateTimeList(null))
    }
}
