package com.battor.freshmate.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = DataStoreSettingsRepository(context)

    @Test
    fun 未写入时默认跟随系统() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }

    @Test
    fun 写入后可读回() = runBlocking {
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
    }
}
