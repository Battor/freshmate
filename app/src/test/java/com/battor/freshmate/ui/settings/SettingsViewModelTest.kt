package com.battor.freshmate.ui.settings

import com.battor.freshmate.FakeScheduler
import com.battor.freshmate.data.DEFAULT_DIGEST_TIMES
import com.battor.freshmate.data.PushMode
import com.battor.freshmate.data.SettingsRepository
import com.battor.freshmate.data.ThemeMode
import com.battor.freshmate.notification.DigestScheduling
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepo(initial: ThemeMode) : SettingsRepository {
        override val themeMode = MutableStateFlow(initial)
        override val pushMode = MutableStateFlow(PushMode.DIGEST)
        override val digestTimes = MutableStateFlow(DEFAULT_DIGEST_TIMES)
        override val onboardingCompleted = MutableStateFlow(true)
        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }
        override suspend fun setPushMode(mode: PushMode) {
            pushMode.value = mode
        }
        override suspend fun setDigestTimes(times: List<LocalTime>) {
            digestTimes.value = times
        }
        override suspend fun setOnboardingCompleted() {
            onboardingCompleted.value = true
        }
    }

    private class FakeDigestScheduler : DigestScheduling {
        val armed = mutableListOf<List<LocalTime>>()
        var cancelled = false
        override fun arm(times: List<LocalTime>) { armed.add(times) }
        override fun cancelAll() { cancelled = true }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `主题初值来自仓库`() {
        val vm = SettingsViewModel(FakeRepo(ThemeMode.DARK), FakeDigestScheduler(), FakeScheduler())
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun `设置主题写入仓库`() = runTest(dispatcher) {
        val repo = FakeRepo(ThemeMode.SYSTEM)
        val vm = SettingsViewModel(repo, FakeDigestScheduler(), FakeScheduler())
        vm.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.value)
    }

    @Test
    fun `切到统一推送按当前时间重装摘要闹钟`() = runTest(dispatcher) {
        val repo = FakeRepo(ThemeMode.SYSTEM)
        val digest = FakeDigestScheduler()
        val vm = SettingsViewModel(repo, digest, FakeScheduler())
        repo.digestTimes.value = listOf(LocalTime.of(8, 0), LocalTime.of(21, 0))
        vm.setPushMode(PushMode.DIGEST)
        assertEquals(listOf(listOf(LocalTime.of(8, 0), LocalTime.of(21, 0))), digest.armed)
    }

    @Test
    fun `切到逐个推送清摘要闹钟并还原语义重排物品`() = runTest(dispatcher) {
        val repo = FakeRepo(ThemeMode.SYSTEM)
        val digest = FakeDigestScheduler()
        val reminder = FakeScheduler()
        val vm = SettingsViewModel(repo, digest, reminder)
        vm.setPushMode(PushMode.INDIVIDUAL)
        assertTrue(digest.cancelled)
        assertTrue(reminder.rescheduledAllRestored)
    }

    @Test
    fun `统一模式下改推送时间立即重装`() = runTest(dispatcher) {
        val repo = FakeRepo(ThemeMode.SYSTEM)
        val digest = FakeDigestScheduler()
        val vm = SettingsViewModel(repo, digest, FakeScheduler())
        vm.setDigestTimes(listOf(LocalTime.of(9, 0)))
        assertEquals(listOf(listOf(LocalTime.of(9, 0))), digest.armed)
    }

    @Test
    fun `逐个模式下改时间只入库不重装`() = runTest(dispatcher) {
        val repo = FakeRepo(ThemeMode.SYSTEM)
        val digest = FakeDigestScheduler()
        val vm = SettingsViewModel(repo, digest, FakeScheduler())
        vm.setPushMode(PushMode.INDIVIDUAL)
        digest.armed.clear()
        vm.setDigestTimes(listOf(LocalTime.of(9, 0)))
        assertTrue(digest.armed.isEmpty())
        assertEquals(listOf(LocalTime.of(9, 0)), repo.digestTimes.value)
    }
}
