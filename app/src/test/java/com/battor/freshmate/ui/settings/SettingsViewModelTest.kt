package com.battor.freshmate.ui.settings

import com.battor.freshmate.data.DEFAULT_DIGEST_TIMES
import com.battor.freshmate.data.PushMode
import com.battor.freshmate.data.SettingsRepository
import com.battor.freshmate.data.ThemeMode
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

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `主题初值来自仓库`() {
        val vm = SettingsViewModel(FakeRepo(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun `设置主题写入仓库`() = runTest(dispatcher) {
        val repo = FakeRepo(ThemeMode.SYSTEM)
        val vm = SettingsViewModel(repo)
        vm.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.value)
    }
}
