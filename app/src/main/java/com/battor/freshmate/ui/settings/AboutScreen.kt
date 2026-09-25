package com.battor.freshmate.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.battor.freshmate.BuildConfig
import com.battor.freshmate.R
import com.battor.freshmate.ui.test.TestEntryCounter
import android.widget.Toast

/** 开源致谢条目：名称/主页/许可证（名称与 URL 非翻译内容，收在代码里）。 */
private data class OssEntry(val name: String, val url: String, val license: String)

private val OssEntries = listOf(
    OssEntry("Jetpack Compose · Material 3", "https://developer.android.com/compose", "Apache 2.0"),
    OssEntry("AndroidX（Room · DataStore · Navigation · Lifecycle · AppCompat）", "https://developer.android.com/jetpack", "Apache 2.0"),
    OssEntry("Kotlin Coroutines", "https://github.com/Kotlin/kotlinx.coroutines", "Apache 2.0"),
    OssEntry("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization", "Apache 2.0"),
    OssEntry("OkHttp", "https://square.github.io/okhttp/", "Apache 2.0"),
    OssEntry("Timber", "https://github.com/JakeWharton/timber", "Apache 2.0"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenTest: () -> Unit) {
    val context = LocalContext.current
    // 隐藏入口：连点 APP 名称 5 次进测试页；第 3 次起 Toast 提示剩余次数。
    // 用 Toast 而非 Snackbar：连点提示 1 秒内连发，Snackbar 的取消竞态会吞掉后一条
    // （平台开发者选项同为 Toast 模式）；计数状态随本页 remember——离开页面即重置。
    // 新提示先 cancel 旧的、解锁进测试页时清掉残留——Toast 不叠罗汉，测试页首屏干净
    var hintToast by remember { mutableStateOf<Toast?>(null) }
    val entryCounter = remember {
        TestEntryCounter(
            onHint = { remaining ->
                hintToast?.cancel()
                hintToast = Toast.makeText(
                    context,
                    context.getString(R.string.test_tap_hint, remaining),
                    Toast.LENGTH_SHORT,
                ).also { it.show() }
            },
            onUnlock = {
                hintToast?.cancel()
                onOpenTest()
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.clickable { entryCounter.onTap(System.currentTimeMillis()) },
                )
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.about_oss_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            OssEntries.forEach { entry ->
                ListItem(
                    headlineContent = { Text(entry.name, style = MaterialTheme.typography.bodyMedium) },
                    supportingContent = {
                        Text(
                            entry.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Text(
                            entry.license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.url))) },
                )
            }
        }
    }
}
