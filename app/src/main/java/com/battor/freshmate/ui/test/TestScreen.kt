package com.battor.freshmate.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.R
import com.battor.freshmate.notification.TestAlarmReceiver
import com.battor.freshmate.notification.TestAlarmState
import com.battor.freshmate.notification.TestAlarmTracker
import com.battor.freshmate.notification.formatTestTime
import com.battor.freshmate.ui.main.PermissionBanners
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val TimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 到点后仍未触发的判定宽限（毫秒）：非精确闹钟回落时允许小幅偏差。 */
private const val FIRE_GRACE_MILLIS = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(viewModel: TestViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val alarmState by TestAlarmTracker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 「到点未触发」需要一次未来的重组：到点+宽限期后戳一下 nowTick
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alarmState) {
        val s = alarmState
        if (s is TestAlarmState.Scheduled) {
            delay((s.atMillis + FIRE_GRACE_MILLIS - System.currentTimeMillis()).coerceAtLeast(0))
            nowTick = System.currentTimeMillis()
        }
    }
    // 档位（毫秒, 标签）：点按即排闹钟，结果走状态区
    val delayOptions = listOf(
        15_000L to stringResource(R.string.test_delay_15s),
        60_000L to stringResource(R.string.test_delay_1m),
        300_000L to stringResource(R.string.test_delay_5m),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.test_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 复用主页横幅：通知/精确闹钟被关时提示，免得测了半天误判链路坏了
            PermissionBanners()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                delayOptions.forEach { (delayMillis, label) ->
                    OutlinedButton(
                        onClick = {
                            TestAlarmReceiver.schedule(context, System.currentTimeMillis() + delayMillis)
                        },
                    ) { Text(label) }
                }
            }
            TestStatusLine(alarmState, nowTick)
            if (state.pending.isEmpty()) {
                Text(
                    stringResource(R.string.test_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(state.pending) { reminder ->
                        ListItem(
                            headlineContent = { Text(reminder.time.format(TimeFormatter)) },
                            supportingContent = {
                                Text(
                                    reminder.itemName + " · " + formatRemaining(
                                        context.resources,
                                        Duration.between(state.now, reminder.time),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 本次测试的状态区：已排 → 已送达 / 未送达（原因）/ 闹钟未触发。 */
@Composable
private fun TestStatusLine(alarmState: TestAlarmState, nowTick: Long) {
    val (text, color) = when (val s = alarmState) {
        TestAlarmState.Idle -> return
        is TestAlarmState.Scheduled ->
            if (nowTick > s.atMillis + FIRE_GRACE_MILLIS) {
                stringResource(R.string.test_status_not_fired) to MaterialTheme.colorScheme.error
            } else {
                stringResource(R.string.test_status_scheduled, formatTestTime(s.atMillis)) to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }
        is TestAlarmState.Delivered ->
            stringResource(R.string.test_status_delivered, formatTestTime(s.firedAtMillis)) to
                MaterialTheme.colorScheme.primary
        is TestAlarmState.Failed ->
            stringResource(s.reasonRes, formatTestTime(s.firedAtMillis)) to
                MaterialTheme.colorScheme.error
    }
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}
