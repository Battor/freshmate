package com.battor.freshmate.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battor.freshmate.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    updateState: UpdateViewModel.UiState,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(updateState.notice) {
        updateState.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("检查更新") },
                    leadingContent = {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    },
                    trailingContent = {
                        if (updateState.checking) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    modifier = Modifier.clickable(
                        enabled = !updateState.busy,
                        onClick = onCheckUpdate,
                    ),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("查看日志") },
                    leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenLogs),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("关于") },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                )
            }
        }
    }

    updateState.manifest?.let { m ->
        AlertDialog(
            // 下载中禁止点外部/返回关闭：对话框是下载进度与安装入口的唯一载体
            onDismissRequest = { if (!updateState.downloading) onDismissUpdate() },
            title = { Text("发现新版本 ${m.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.notes?.let { Text(it) }
                    if (updateState.downloading) {
                        val p = updateState.progress
                        if (p != null) {
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            // 服务器未给总长度：不定进度条
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    updateState.downloading -> TextButton(onClick = {}) {
                        val p = updateState.progress
                        Text(if (p != null) "下载中 ${(p * 100).toInt()}%" else "下载中…")
                    }
                    updateState.apkReady -> TextButton(onClick = onInstall) { Text("安装") }
                    else -> TextButton(onClick = onDownload) { Text("下载更新") }
                }
            },
            dismissButton = {
                if (!updateState.downloading) {
                    TextButton(onClick = onDismissUpdate) { Text("取消") }
                }
            },
        )
    }
}
