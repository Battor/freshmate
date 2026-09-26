package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.battor.freshmate.R
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.inputmethod.InputMethods

/**
 * 新增入口：+ 号菜单，三种录入方式（手动/语音/图片）任选其一新开表单。
 * 编辑态的返回/保存按钮已上移顶栏（需求-7），编辑态不再渲染 FAB。
 */
@Composable
fun FabMenu(
    onStartInput: (InputMethodId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        // 走查反馈：FAB 用品牌主色（浅色主题即 APP 图标的深绿）+ onPrimary 前景；
        // 深色主题沿用 primary = 亮绿变体，保持与深底的对比
        FloatingActionButton(
            onClick = { expanded = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InputMethods.all.forEach { method ->
                DropdownMenuItem(
                    text = { Text(stringResource(method.menuLabelRes)) },
                    leadingIcon = { Icon(method.menuIcon, contentDescription = null) },
                    onClick = { expanded = false; onStartInput(method.id) },
                )
            }
        }
    }
}
