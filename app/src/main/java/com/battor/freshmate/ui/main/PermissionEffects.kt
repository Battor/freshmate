package com.battor.freshmate.ui.main

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import timber.log.Timber

/** 首次保存后请求通知权限（设计文档 §7）。 */
@Composable
fun NotificationPermissionEffect(
    request: Boolean,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        Timber.i(
            "PERM 通知权限 granted=%b",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        onHandled()
    }

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

private data class PermissionBlockState(val notificationsOff: Boolean, val exactAlarmOff: Boolean)

private fun readPermissionBlockState(context: Context) = PermissionBlockState(
    notificationsOff = !NotificationManagerCompat.from(context).areNotificationsEnabled(),
    exactAlarmOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
)

/** 顶部状态横幅：通知被关 / 精确闹钟未授予时提示。从系统设置返回（ON_RESUME）时重新读取权限状态。 */
@Composable
fun PermissionBanners() {
    val context = LocalContext.current
    var blockState by remember { mutableStateOf(readPermissionBlockState(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        blockState = readPermissionBlockState(context)
    }

    val notificationsOff = blockState.notificationsOff
    val exactOff = blockState.exactAlarmOff

    if (notificationsOff) {
        Banner(text = stringResource(com.battor.freshmate.R.string.banner_notifications_off)) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    } else if (exactOff) {
        Banner(text = stringResource(com.battor.freshmate.R.string.banner_exact_alarm_off)) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }
}

@Composable
private fun Banner(text: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onAction) { Text(stringResource(com.battor.freshmate.R.string.banner_action_go)) }
        }
    }
}
