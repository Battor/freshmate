package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.battor.freshmate.R
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.inputmethod.InputMethods

/**
 * 两态 FAB：
 * - 表单打开：←（放弃返回）；表单有内容时追加 ✓（新增 = 暂存并继续；编辑 = 保存）。
 * - 无表单：+ 号菜单，三种录入方式（手动/语音/图片）任选其一新开表单。
 */
@Composable
fun FabMenu(
    formOpen: Boolean,
    isAddForm: Boolean,
    showSave: Boolean,
    onStartInput: (InputMethodId) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (formOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            SmallFloatingActionButton(
                onClick = onBack,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                // 48dp 触摸目标；end padding 保证与 ✓ 按钮间距
                modifier = Modifier
                    .padding(end = 12.dp)
                    .minimumInteractiveComponentSize(),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.discard_back)) }
            if (showSave) {
                SmallFloatingActionButton(
                    onClick = onSave,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(
                            if (isAddForm) R.string.stash_and_continue else R.string.save,
                        ),
                    )
                }
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = modifier) {
            FloatingActionButton(onClick = { expanded = true }) {
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
}
