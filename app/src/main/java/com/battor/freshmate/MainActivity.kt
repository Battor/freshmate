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

/** AppCompatActivity：per-app 语言（需求-4）依赖 appcompat 委托；主题经 Compose 状态即时切换。 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = DataStoreSettingsRepository(applicationContext)
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
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
