package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * 四种状态（2026-08-15 批量添加模式）：
 * - 普通模式：+ 号菜单（手动/语音/图片/完成，“完成”禁用）。
 * - 批量表单中（新条目）：←（返回输入方式选择）×（放弃并结束批量）✓（暂存并清空继续）。
 * - 批量空闲（会话在、表单关）：+ 号菜单，“完成（已加 N 条）”启用。
 * - 编辑已有条目：对勾（保存）/ 叉号（放弃），单条语义不变。
 */
@Composable
fun FabMenu(
    formOpen: Boolean,
    isBatchForm: Boolean,
    batchActive: Boolean,
    stagedCount: Int,
    onStartInput: (InputMethodId) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onBackToSelection: () -> Unit,
    onFinishBatch: () -> Unit,
) {
    if (formOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isBatchForm) {
                SmallFloatingActionButton(
                    onClick = onBackToSelection,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(end = 12.dp),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回输入方式选择") }
            }
            SmallFloatingActionButton(
                onClick = onDiscard,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = if (isBatchForm) "结束批量" else "放弃",
                )
            }
            SmallFloatingActionButton(onClick = onSave) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (isBatchForm) "暂存并继续" else "保存",
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
                DropdownMenuItem(
                    text = { Text(if (batchActive) "完成（已加 $stagedCount 条）" else "完成") },
                    leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) },
                    enabled = batchActive, // 批量会话进行中才可结束
                    onClick = { expanded = false; onFinishBatch() },
                )
            }
        }
    }
}
