package com.battor.freshmate.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.R
import com.battor.freshmate.data.ThemeMode
import com.battor.freshmate.ui.common.asString
import com.battor.freshmate.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenGuide: () -> Unit,
    updateState: UpdateViewModel.UiState,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = updateState.notice?.asString()
    LaunchedEffect(updateState.notice) {
        noticeText?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            item { ThemeSettingItem(viewModel) }
            item { LanguageSettingItem(viewModel) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_onboarding)) },
                    leadingContent = { Icon(Icons.Filled.School, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenGuide),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.view_logs)) },
                    leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenLogs),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.check_update)) },
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
                    headlineContent = { Text(stringResource(R.string.about)) },
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
            title = { Text(stringResource(R.string.update_found_title, m.versionName)) },
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
                        Text(
                            if (p != null) {
                                stringResource(R.string.update_downloading_percent, (p * 100).toInt())
                            } else {
                                stringResource(R.string.update_downloading)
                            },
                        )
                    }
                    updateState.apkReady -> TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
                    else -> TextButton(onClick = onDownload) { Text(stringResource(R.string.update_download)) }
                }
            },
            dismissButton = {
                if (!updateState.downloading) {
                    TextButton(onClick = onDismissUpdate) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

/** 主题设置：UI 只有两档（浅色/深色）；SYSTEM 态显示系统当前模式（进入时检测）。 */
@Composable
private fun ThemeSettingItem(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    // SYSTEM 态跟随系统当前模式展示（isSystemInDarkTheme 变化会触发重组刷新）
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val lightLabel = stringResource(R.string.settings_theme_light)
    val darkLabel = stringResource(R.string.settings_theme_dark)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        leadingContent = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
        trailingContent = {
            Text(
                if (isDark) darkLabel else lightLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable { showDialog = true },
    )
    if (showDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(lightLabel to ThemeMode.LIGHT, darkLabel to ThemeMode.DARK),
            selected = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT,
            onSelect = {
                viewModel.setThemeMode(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** 语言设置：四档（跟随系统/简中/繁中/English）；切换经 per-app language 重建 Activity。 */
@Composable
private fun LanguageSettingItem(viewModel: SettingsViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val followSystem = stringResource(R.string.language_follow_system)
    val zhCn = stringResource(R.string.language_name_zh_cn)
    val zhTw = stringResource(R.string.language_name_zh_tw)
    val en = stringResource(R.string.language_name_en)
    val current = viewModel.currentLanguage()
    val currentLabel = when (current) {
        AppLanguage.SYSTEM -> followSystem
        AppLanguage.SIMPLIFIED_CHINESE -> zhCn
        AppLanguage.TRADITIONAL_CHINESE -> zhTw
        AppLanguage.ENGLISH -> en
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_language)) },
        leadingContent = { Icon(Icons.Filled.Language, contentDescription = null) },
        trailingContent = { Text(currentLabel) },
        modifier = Modifier.clickable { showDialog = true },
    )
    if (showDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf(
                followSystem to AppLanguage.SYSTEM,
                zhCn to AppLanguage.SIMPLIFIED_CHINESE,
                zhTw to AppLanguage.TRADITIONAL_CHINESE,
                en to AppLanguage.ENGLISH,
            ),
            selected = current,
            onSelect = {
                viewModel.setLanguage(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** M3 单选对话框：整行 selectable（Role.RadioButton），RadioButton 自身无独立点击语义。 */
@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(
                            label,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        // confirmButton 为必填槽位：本对话框只有取消动作，留空占位
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
