package com.battor.freshmate.ui.theme

import androidx.compose.ui.graphics.Color

// Android 10–11 的明快"多巴胺"配色（Android 12+ 使用动态取色，见 Theme.kt）
val GreenDark = Color(0xFF1E6B2A)
val LightGreenContainer = Color(0xFFDCF5CE)
val LightBackground = Color(0xFFFDFBF3)
val DarkBackground = Color(0xFF1A1C18)

// 浅色方案（角色组：主色 + on 主色 + 容器 + on 容器）
val SkyDark = Color(0xFF1F5C7A)
val PeachDark = Color(0xFFB84A1F)
val GreenOnContainer = Color(0xFF1E4D17)
val SkyContainer = Color(0xFFD3ECF7)
val SkyOnContainer = Color(0xFF0E3A52)
val PeachContainer = Color(0xFFFFDBC8)
val PeachOnContainer = Color(0xFF5C2410)

// 深色方案
val GreenLight = Color(0xFF8FE09A)
val GreenOnLight = Color(0xFF0F3D0A)
val SkyLight = Color(0xFF8CCBEF)
val PeachLight = Color(0xFFFFB59A)

// 需求-6：品牌回退主题的 surface 容器色阶与 onSurfaceVariant——
// 从 LightBackground/DarkBackground 的暖调派生；M3 默认回落是偏紫灰，与品牌暖底色相冲突
val LightSurfaceLowest = Color(0xFFFFFFFF)
val LightSurfaceLow = Color(0xFFF8F6ED)
val LightSurfaceHigh = Color(0xFFEDEBE0)
val LightSurfaceHighest = Color(0xFFE7E5D8)
val LightOnSurfaceVariant = Color(0xFF46483F)
val DarkSurfaceLowest = Color(0xFF0F110E)
val DarkSurfaceLow = Color(0xFF191B17)
val DarkSurfaceHigh = Color(0xFF242620)
val DarkSurfaceHighest = Color(0xFF2E3029)
val DarkOnSurfaceVariant = Color(0xFFC4C9BE)
