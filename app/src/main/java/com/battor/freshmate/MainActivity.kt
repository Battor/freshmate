package com.battor.freshmate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
            val darkTheme = themeMode.resolvesDark(isSystemInDarkTheme())
            // enableEdgeToEdge 只在 onCreate 按系统模式定系统栏图标对比度；
            // 强制主题下需随 darkTheme 同步，否则深色 app 配浅色系统时状态栏图标不可见
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            FreshMateTheme(darkTheme = darkTheme) { FreshMateNavGraph() }
        }
    }
}
