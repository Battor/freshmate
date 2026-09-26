package com.battor.freshmate.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.battor.freshmate.R
import com.battor.freshmate.inputmethod.InputMethodId

/**
 * 新增入口：+ 号直接新开手动输入表单。
 * 语音/图片入口暂时移除（走查决定，2026-09-26）：InputMethods 框架与字符串保留，
 * 日后恢复只需把 DropdownMenu 菜单加回来遍历 InputMethods.all。
 * 编辑态的返回/保存按钮已上移顶栏（需求-7），编辑态不再渲染 FAB。
 */
@Composable
fun FabMenu(
    onStartInput: (InputMethodId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 走查反馈：FAB 用品牌主色（浅色主题即 APP 图标的深绿）+ onPrimary 前景；
    // 深色主题沿用 primary = 亮绿变体，保持与深底的对比
    FloatingActionButton(
        onClick = { onStartInput(InputMethodId.MANUAL) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
    }
}
