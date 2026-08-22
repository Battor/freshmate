package com.battor.freshmate.util

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric 默认 locale 是 en-US，会解析 values-en；钉 zh-CN 回落到 values/（简体中文）
@Config(sdk = [34], qualifiers = "zh-rCN")
class ExpiryStatusTest {
    private val now = LocalDateTime.of(2026, 8, 20, 10, 0)
    private val res: Resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun `过期即EXPIRED`() {
        val e = now.minusHours(1)
        assertEquals(ExpiryStatus.EXPIRED, expiryStatus(e, now))
    }

    @Test fun `恰好到期即EXPIRED`() {
        assertEquals(ExpiryStatus.EXPIRED, expiryStatus(now, now))
    }

    @Test fun `恰在24h边界归DUE_1D`() =
        assertEquals(ExpiryStatus.DUE_1D, expiryStatus(now.plusHours(24), now))

    @Test fun `恰在72h边界归DUE_3D`() =
        assertEquals(ExpiryStatus.DUE_3D, expiryStatus(now.plusHours(72), now))

    @Test fun `恰在168h边界归DUE_7D`() =
        assertEquals(ExpiryStatus.DUE_7D, expiryStatus(now.plusHours(168), now))

    @Test fun `恰在336h边界归DUE_14D`() =
        assertEquals(ExpiryStatus.DUE_14D, expiryStatus(now.plusHours(336), now))

    @Test fun `超过14天SAFE`() =
        assertEquals(ExpiryStatus.SAFE, expiryStatus(now.plusHours(337), now))

    @Test fun `剩余2天归DUE_3D而非按旧比例`() {
        // 旧比例式：保质期 30 天剩 2 天已是 CRITICAL；新绝对式：2 天 = DUE_3D
        assertEquals(ExpiryStatus.DUE_3D, expiryStatus(now.plusDays(2), now))
    }

    @Test
    fun `剩余文案 - 天加小时`() =
        assertEquals("3 天 17 小时", formatRemaining(res, Duration.ofMinutes(3 * 1440 + 17 * 60 + 29)))

    @Test
    fun `剩余文案 - 不足半小时`() =
        assertEquals("不足 30 分钟", formatRemaining(res, Duration.ofMinutes(20)))

    @Test
    fun `剩余文案 - 半小时`() =
        assertEquals("30 分钟", formatRemaining(res, Duration.ofMinutes(45)))

    @Test
    fun `过期文案 - 天`() =
        assertEquals("已过期 2 天", "已过期 " + formatExpired(res, Duration.ofDays(2)))

    @Test
    fun `过期文案 - 小时`() =
        assertEquals("已过期 5 小时", "已过期 " + formatExpired(res, Duration.ofHours(5)))

    // 断言不变量而非复述枚举表：比例 ∈ (0,1]、EXPIRED/SAFE 取满、due 梯子单调不减
    @Test
    fun `桶头进度比例 - 不变量`() {
        ExpiryStatus.entries.forEach {
            assertTrue(it.windowProgress > 0f && it.windowProgress <= 1f)
        }
        assertEquals(1f, ExpiryStatus.EXPIRED.windowProgress)
        assertEquals(1f, ExpiryStatus.SAFE.windowProgress)
        val progresses = ExpiryStatus.entries.drop(1).map { it.windowProgress }
        assertEquals(progresses.sorted(), progresses)
    }
}
