package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
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
 * 未编辑：+ 号展开 4 项菜单（手动/语音/图片/完成，“完成”仅编辑中可用）。
 * 编辑中：对勾（保存）/ 叉号（放弃）两个按钮。
 */
@Composable
fun FabMenu(
    isEditing: Boolean,
    onStartInput: (InputMethodId) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (isEditing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallFloatingActionButton(
                onClick = onDiscard,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(end = 12.dp),
            ) { Icon(Icons.Filled.Close, contentDescription = "放弃") }
            SmallFloatingActionButton(onClick = onSave) {
                Icon(Icons.Filled.Check, contentDescription = "保存")
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
                DropdownMenuItem(
                    text = { Text("完成") },
                    leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) },
                    enabled = false, // 未在编辑中无表单可完成
                    onClick = {},
                )
            }
        }
    }
}
