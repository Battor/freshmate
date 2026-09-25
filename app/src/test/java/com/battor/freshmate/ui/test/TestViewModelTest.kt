package com.battor.freshmate.ui.test

import com.battor.freshmate.FakeRepository
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.DEFAULT_DIGEST_TIMES
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.PushMode
import com.battor.freshmate.data.SettingsRepository
import com.battor.freshmate.data.ThemeMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelTest {
    private val now = LocalDateTime.of(2026, 9, 20, 12, 0)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepository
    private lateinit var settings: FakeSettings

    private class FakeSettings : SettingsRepository {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val pushMode = MutableStateFlow(PushMode.DIGEST)
        override val digestTimes = MutableStateFlow(DEFAULT_DIGEST_TIMES)
        override val onboardingCompleted = MutableStateFlow(true)
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setPushMode(mode: PushMode) {}
        override suspend fun setDigestTimes(times: List<LocalTime>) {}
        override suspend fun setOnboardingCompleted() {}
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepository()
        settings = FakeSettings()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(name: String, vararg times: LocalDateTime) = FoodItem(
        name = name,
        category = Category.DAIRY,
        productionDate = LocalDate.of(2026, 9, 20),
        shelfLifeDays = 7,
        quantity = null,
        createdAt = now,
        reminderTimes = times.toList(),
    )

    /** productionDate=null → 到期 = createdAt + shelfLifeDays；hoursToExpiry 控制剩余时长。 */
    private fun expiresIn(name: String, hoursToExpiry: Long) = FoodItem(
        name = name, category = Category.DAIRY, productionDate = null,
        shelfLifeDays = 30, quantity = null,
        createdAt = now.plusHours(hoursToExpiry).minusDays(30),
    )

    @Test fun `过滤已过时点只留未来`() = runTest(dispatcher) {
        val past = now.minusHours(1)
        val future = now.plusHours(2)
        repo.items.value = listOf(item("牛奶", past, future))
        val vm = TestViewModel(repo, settings) { now }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.pending.size)
        assertEquals(future, vm.uiState.value.pending[0].time)
    }

    @Test fun `多条目展平并按时间升序`() = runTest(dispatcher) {
        val a1 = now.plusDays(3)
        val b1 = now.plusDays(1)
        val a2 = now.plusDays(5)
        repo.items.value = listOf(
            item("牛奶", a1, a2),
            item("酸奶", b1),
        )
        val vm = TestViewModel(repo, settings) { now }
        advanceUntilIdle()
        assertEquals(
            listOf(b1 to "酸奶", a1 to "牛奶", a2 to "牛奶"),
            vm.uiState.value.pending.map { it.time to it.itemName },
        )
    }

    @Test fun `空列表与无未来时点都显示空态`() = runTest(dispatcher) {
        repo.items.value = listOf(item("过期面包", now.minusDays(1)))
        val vm = TestViewModel(repo, settings) { now }
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.pending.size)
        assertEquals(now, vm.uiState.value.now)
    }

    @Test fun `摘要预览按档分列且排除远档`() = runTest(dispatcher) {
        repo.items.value = listOf(
            expiresIn("半天", 12),  // 1 天档
            expiresIn("两天", 48),  // 3 天档
            expiresIn("十天", 240), // 14 天档：不在预览
        )
        val vm = TestViewModel(repo, settings) { now }
        advanceUntilIdle()
        assertEquals(listOf("半天"), vm.uiState.value.digestPreview.dueWithin1d.map { it.name })
        assertEquals(listOf("两天"), vm.uiState.value.digestPreview.dueWithin3d.map { it.name })
    }

    @Test fun `模式与时间状态来自设置流`() = runTest(dispatcher) {
        val vm = TestViewModel(repo, settings) { now }
        advanceUntilIdle()
        assertEquals(PushMode.DIGEST, vm.uiState.value.pushMode)
        settings.pushMode.value = PushMode.INDIVIDUAL
        settings.digestTimes.value = listOf(LocalTime.of(8, 0), LocalTime.of(21, 0))
        advanceUntilIdle()
        assertEquals(PushMode.INDIVIDUAL, vm.uiState.value.pushMode)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(21, 0)), vm.uiState.value.digestTimes)
    }
}
