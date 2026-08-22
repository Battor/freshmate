package com.battor.freshmate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.battor.freshmate.data.DataStoreSettingsRepository
import com.battor.freshmate.data.ThemeMode
import com.battor.freshmate.ui.navigation.FreshMateNavGraph
import com.battor.freshmate.ui.theme.FreshMateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** AppCompatActivity：per-app 语言（需求-4）依赖 appcompat 委托；主题经 Compose 状态即时切换。 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = DataStoreSettingsRepository(applicationContext)
        // 首帧前的同步初值：collectAsState 的异步首播会令固定深/浅色用户闪一帧系统主题
        val initialMode = runBlocking { settings.themeMode.first() }
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = initialMode)
            FreshMateTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) { FreshMateNavGraph() }
        }
    }
}
