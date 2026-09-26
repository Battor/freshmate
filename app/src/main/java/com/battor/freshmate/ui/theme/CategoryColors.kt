package com.battor.freshmate.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.battor.freshmate.data.Category

/** 分类色系统（多巴胺配色，specs/2026-09-26）：图标按分类染色，表单底随分类联动。 */
private data class CategoryColors(
    val iconLight: Color,
    val iconDark: Color,
    val formLight: Color,
    val formDark: Color,
)

// 图标浅色 = 饱和正色（压粉彩卡底）；图标深色 = 同色相提亮降饱和（压深色卡底）；
// 表单底 = 极淡色调（深色版 = 图标深色 12% 混入 #242620，预计算）
// exhaustive when：新增 Category 枚举时编译期报错，与 StatusColor.kt 同款风格
private fun categoryColors(category: Category): CategoryColors = when (category) {
    Category.FRUITS_VEG -> CategoryColors(Color(0xFF2E7D32), Color(0xFF81C784), Color(0xFFE3F0DC), Color(0xFF2F392C))
    Category.MEAT_EGG -> CategoryColors(Color(0xFFC62828), Color(0xFFE57373), Color(0xFFFAE3DC), Color(0xFF3B2F2C))
    Category.DAIRY -> CategoryColors(Color(0xFF1E88E5), Color(0xFF64B5F6), Color(0xFFE1EDF9), Color(0xFF2C373A))
    Category.DRINK -> CategoryColors(Color(0xFF00ACC1), Color(0xFF4DD0E1), Color(0xFFDCF2F5), Color(0xFF293A37))
    Category.SNACK -> CategoryColors(Color(0xFFF57C00), Color(0xFFFFB74D), Color(0xFFFDE6DC), Color(0xFF3E3725))
    Category.STAPLE -> CategoryColors(Color(0xFF795548), Color(0xFFA1887F), Color(0xFFEFE8E4), Color(0xFF33322B))
    Category.FROZEN -> CategoryColors(Color(0xFF78909C), Color(0xFFB0BEC5), Color(0xFFDFF4F8), Color(0xFF353834))
    Category.CONDIMENT -> CategoryColors(Color(0xFFAFB42B), Color(0xFFDCE775), Color(0xFFF3F2D6), Color(0xFF3A3D2A))
}

/** 分类图标色：全 app 一致表达"分类身份"，不再借用状态 on 色；深浅变体随已解析主题（LocalDarkTheme）。 */
@Composable
fun categoryIconColor(category: Category): Color =
    if (LocalDarkTheme.current) categoryColors(category).iconDark
    else categoryColors(category).iconLight

/** 表单卡底色：随所选分类联动的极淡色调（新增默认果蔬淡绿；编辑按条目分类着色）。 */
@Composable
fun categoryFormColor(category: Category): Color =
    if (LocalDarkTheme.current) categoryColors(category).formDark
    else categoryColors(category).formLight
