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
    fun 默认跟随系统_写入后可读回() = runBlocking {
        // 同一方法内先断言默认再写读：preferencesDataStore 委托是文件级单例，
        // 跨测试方法共享实例，方法拆开会有执行顺序耦合
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
    }
}
