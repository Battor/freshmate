package com.battor.freshmate.ui.main

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/** 首次保存后请求通知权限（设计文档 §7）。 */
@Composable
fun NotificationPermissionEffect(
    request: Boolean,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> onHandled() }

    LaunchedEffect(request) {
        if (!request) return@LaunchedEffect
        val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= 33 && !granted) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onHandled()
        }
    }
}

/** 顶部状态横幅：通知被关 / 精确闹钟未授予时提示。 */
@Composable
fun PermissionBanners() {
    val context = LocalContext.current
    val notificationsOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactOff = Build.VERSION.SDK_INT >= 31 &&
        !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    if (notificationsOff) {
        Banner(text = "通知未开启，将收不到过期提醒") {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    } else if (exactOff) {
        Banner(text = "精确提醒未开启，提醒时间可能偏差") {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }
}

@Composable
private fun Banner(text: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onAction) { Text("去开启") }
        }
    }
}
