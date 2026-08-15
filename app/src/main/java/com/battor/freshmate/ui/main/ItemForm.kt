package com.battor.freshmate.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.battor.freshmate.data.Category
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.inputmethod.InputMethods
import com.battor.freshmate.ui.main.MainViewModel.EditingState
import com.battor.freshmate.util.ShelfLifeUnit
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.formatExpired
import com.battor.freshmate.util.formatRemaining
import com.battor.freshmate.util.shelfLifeToDays
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 快捷保质期：30天 / 3个月 / 6个月 / 1年（2026-08-15 用户反馈去掉 3天/7天） */
private val QuickShelfLives = listOf(
    Triple("30天", 30, ShelfLifeUnit.DAY),
    Triple("3个月", 3, ShelfLifeUnit.MONTH),
    Triple("6个月", 6, ShelfLifeUnit.MONTH),
    Triple("1年", 1, ShelfLifeUnit.YEAR),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ItemForm(
    state: EditingState,
    onStateChange: (EditingState) -> Unit,
    onPlaceholderHint: (String) -> Unit,
) {
    val inputMethod = InputMethods.byId(state.inputMethod)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            inputMethod.extraAction?.let { extra ->
                ExtraActionRow(state.inputMethod, extra.icon, extra.label, onPlaceholderHint)
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { onStateChange(state.copy(name = it, nameError = false)) },
                label = { Text("食品名称 *") },
                isError = state.nameError,
                supportingText = {
                    if (state.nameError) Text("请输入名称", color = MaterialTheme.colorScheme.error)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("分类")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = state.category == c,
                        onClick = { onStateChange(state.copy(category = c)) },
                        label = { Text(c.label) },
                    )
                }
            }

            ProductionDateField(
                productionDate = state.productionDate,
                onChange = { onStateChange(state.copy(productionDate = it)) },
            )

            OutlinedTextField(
                value = state.shelfLifeValue,
                onValueChange = { text ->
                    onStateChange(
                        state.copy(
                            shelfLifeValue = text.filter { it in '0'..'9' }.take(4),
                            shelfLifeError = false,
                        ),
                    )
                },
                label = { Text("保质期 *") },
                isError = state.shelfLifeError,
                supportingText = {
                    if (state.shelfLifeError) {
                        Text("请输入大于 0 的数字", color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ShelfLifeUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = state.shelfLifeUnit == unit,
                        onClick = { onStateChange(state.copy(shelfLifeUnit = unit)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index, ShelfLifeUnit.entries.size,
                        ),
                    ) { Text(unit.label) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickShelfLives.forEach { (label, value, unit) ->
                    AssistChip(
                        onClick = {
                            onStateChange(
                                state.copy(
                                    shelfLifeValue = value.toString(),
                                    shelfLifeUnit = unit,
                                    shelfLifeError = false,
                                ),
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }

            OutlinedTextField(
                value = state.quantity,
                onValueChange = { onStateChange(state.copy(quantity = it)) },
                label = { Text("数量（可选，如 2 / 500g）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExpiryPreview(state)
        }
    }
}

@Composable
private fun ExpiryPreview(state: EditingState) {
    // 数值必须乘上单位（1 + 年 = 365 天），与 save() 的换算保持一致
    val days = state.shelfLifeValue.toIntOrNull()
        ?.takeIf { it > 0 }
        ?.let { shelfLifeToDays(it, state.shelfLifeUnit) }
        ?: return
    val expiry = expiryDateTime(state.productionDate, state.createdAt, days)
    val now = remember { LocalDateTime.now() }
    val remaining = Duration.between(now, expiry)
    val text = if (remaining.isNegative || remaining.isZero) {
        "已过期 ${formatExpired(remaining.negated())}，保存后将不再提醒"
    } else {
        "还有 ${formatRemaining(remaining)} 到期"
    }
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductionDateField(
    productionDate: LocalDate?,
    onChange: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    // 只读输入框不响应 onValueChange，通过 interactionSource 捕获点击弹出日历
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showPicker = true
        }
    }

    OutlinedTextField(
        value = productionDate?.toString() ?: "",
        onValueChange = {},
        readOnly = true,
        interactionSource = interactionSource,
        label = { Text("生产日期") },
        placeholder = { Text("不填则按录入日起算") },
        singleLine = true,
        trailingIcon = if (productionDate != null) {
            {
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除生产日期")
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = productionDate
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(date)
                        }
                        showPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** 语音/图片的附加操作占位（设计文档 §5.3，v1 不接识别）。 */
@Composable
private fun ExtraActionRow(
    id: InputMethodId,
    icon: ImageVector,
    label: String,
    onPlaceholderHint: (String) -> Unit,
) {
    var recording by remember { mutableStateOf(false) }
    // 仅在录音时创建无限动画，避免非录音状态下持续产生动画帧
    val scale = if (recording) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label = "scale",
        ).value
    } else {
        1f
    }
    TextButton(
        onClick = {
            when {
                id == InputMethodId.IMAGE -> onPlaceholderHint("图片识别即将上线")
                // 录音中普通点击不打断录音（长按可结束）
                recording -> Unit
                else -> onPlaceholderHint("语音识别即将上线")
            }
        },
        modifier = if (id == InputMethodId.VOICE) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { recording = !recording })
            }
        } else {
            Modifier
        },
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier
                .padding(end = 6.dp)
                .scale(scale),
        )
        Text(
            when {
                id == InputMethodId.IMAGE -> label
                recording -> "录音中…（占位，再按一次结束）"
                else -> "$label（长按）"
            },
        )
    }
}
