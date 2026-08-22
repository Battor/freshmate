package com.battor.freshmate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.battor.freshmate.ui.main.DarkStatusPalette
import com.battor.freshmate.ui.main.LightStatusPalette
import com.battor.freshmate.ui.main.LocalStatusColors

private val LightColors = lightColorScheme(
    primary = GreenDark,          // 深绿，配白字对比度 ~6.6:1
    onPrimary = Color.White,
    primaryContainer = LightGreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = SkyDark,
    onSecondary = Color.White,
    secondaryContainer = SkyContainer,
    onSecondaryContainer = SkyOnContainer,
    tertiary = PeachDark,
    onTertiary = Color.White,
    tertiaryContainer = PeachContainer,
    onTertiaryContainer = PeachOnContainer,
    background = LightBackground,
    surface = LightBackground,
    // 需求-6：编辑区消费的容器色阶，从品牌暖底派生（默认回落偏紫灰，与暖底冲突）
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    onSurfaceVariant = LightOnSurfaceVariant,
)

/** 形状集中（8/12/16dp 恰为 M3 默认档位）：全应用统一经 MaterialTheme.shapes 引用。 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = GreenOnLight,
    primaryContainer = GreenDark,
    onPrimaryContainer = LightGreenContainer,
    secondary = SkyLight,
    onSecondary = SkyOnContainer,
    secondaryContainer = SkyDark,
    onSecondaryContainer = SkyContainer,
    tertiary = PeachLight,
    onTertiary = PeachOnContainer,
    tertiaryContainer = PeachDark,
    onTertiaryContainer = PeachContainer,
    background = DarkBackground,
    surface = DarkBackground,
    // 需求-6：编辑区消费的容器色阶，从品牌深底派生（默认回落偏紫灰，与偏绿深底冲突）
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    onSurfaceVariant = DarkOnSurfaceVariant,
)

@Composable
fun FreshMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ 动态取色；传 false 可强制使用品牌配色
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val palette = if (darkTheme) DarkStatusPalette else LightStatusPalette
    CompositionLocalProvider(LocalStatusColors provides palette) {
        MaterialTheme(colorScheme = colorScheme, shapes = AppShapes, content = content)
    }
}
