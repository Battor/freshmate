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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.battor.freshmate.BuildConfig
import com.battor.freshmate.R

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
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
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
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
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
