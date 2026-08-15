package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battor.freshmate.inputmethod.InputMethodId

/**
 * 两态（2026-08-16 组容器模型）：
 * - 表单打开：←（放弃返回）/ ✓（新增表单 = 暂存并清空继续；编辑表单 = 保存退出）。
 * - 无表单：+ 号菜单（手动/语音/图片），新录入永远新开一组。
 */
@Composable
fun FabMenu(
    formOpen: Boolean,
    isAddForm: Boolean,
    onStartInput: (InputMethodId) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    if (formOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallFloatingActionButton(
                onClick = onBack,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(end = 12.dp),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "放弃返回") }
            SmallFloatingActionButton(onClick = onSave) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (isAddForm) "暂存并继续" else "保存",
                )
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }
        Box {
            FloatingActionButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("手动输入") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { expanded = false; onStartInput(InputMethodId.MANUAL) },
                )
                DropdownMenuItem(
                    text = { Text("语音输入") },
                    leadingIcon = { Icon(Icons.Filled.KeyboardVoice, contentDescription = null) },
                    onClick = { expanded = false; onStartInput(InputMethodId.VOICE) },
                )
                DropdownMenuItem(
                    text = { Text("图片输入") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    onClick = { expanded = false; onStartInput(InputMethodId.IMAGE) },
                )
            }
        }
    }
}
