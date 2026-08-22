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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.battor.freshmate.R
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

/** 快捷保质期：30天 / 3个月 / 6个月 / 1年（2026-08-15 用户反馈去掉 3天/7天）；labelRes 带格式参数 %1$d。 */
private data class QuickShelfLife(
    @StringRes val labelRes: Int,
    val value: Int,
    val unit: ShelfLifeUnit,
)

private val QuickShelfLives = listOf(
    QuickShelfLife(R.string.quick_shelf_days, 30, ShelfLifeUnit.DAY),
    QuickShelfLife(R.string.quick_shelf_months, 3, ShelfLifeUnit.MONTH),
    QuickShelfLife(R.string.quick_shelf_months, 6, ShelfLifeUnit.MONTH),
    QuickShelfLife(R.string.quick_shelf_years, 1, ShelfLifeUnit.YEAR),
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
        shape = MaterialTheme.shapes.large,
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
                ExtraActionRow(state.inputMethod, extra.icon, extra.labelRes, onPlaceholderHint)
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { onStateChange(state.copy(name = it, nameError = false)) },
                label = { Text(stringResource(R.string.field_name)) },
                isError = state.nameError,
                supportingText = {
                    if (state.nameError) {
                        Text(
                            stringResource(R.string.error_name_required),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.label_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = state.category == c,
                        onClick = { onStateChange(state.copy(category = c)) },
                        label = { Text(stringResource(c.labelRes)) },
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
                label = { Text(stringResource(R.string.field_shelf_life)) },
                isError = state.shelfLifeError,
                supportingText = {
                    if (state.shelfLifeError) {
                        Text(
                            stringResource(R.string.error_shelf_life),
                            color = MaterialTheme.colorScheme.error,
                        )
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
                    ) { Text(stringResource(unit.labelRes)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickShelfLives.forEach { quick ->
                    AssistChip(
                        onClick = {
                            onStateChange(
                                state.copy(
                                    shelfLifeValue = quick.value.toString(),
                                    shelfLifeUnit = quick.unit,
                                    shelfLifeError = false,
                                ),
                            )
                        },
                        label = { Text(stringResource(quick.labelRes, quick.value)) },
                    )
                }
            }

            OutlinedTextField(
                value = state.quantity,
                onValueChange = { onStateChange(state.copy(quantity = it)) },
                label = { Text(stringResource(R.string.field_quantity)) },
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
        stringResource(
            R.string.expiry_preview_expired,
            formatExpired(LocalContext.current.resources, remaining.negated()),
        )
    } else {
        stringResource(
            R.string.expiry_preview_remaining,
            formatRemaining(LocalContext.current.resources, remaining),
        )
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
        label = { Text(stringResource(R.string.field_production_date)) },
        placeholder = { Text(stringResource(R.string.hint_production_date)) },
        singleLine = true,
        trailingIcon = if (productionDate != null) {
            {
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_production_date))
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
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** 语音/图片的附加操作占位（设计文档 §5.3，v1 不接识别）。 */
@Composable
private fun ExtraActionRow(
    id: InputMethodId,
    icon: ImageVector,
    @StringRes labelRes: Int,
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
    val label = stringResource(labelRes)
    // 提前解析：stringResource 不能在 onClick 等非 Composable 上下文调用
    val imageHint = stringResource(R.string.placeholder_image)
    val voiceHint = stringResource(R.string.placeholder_voice)
    TextButton(
        onClick = {
            when {
                id == InputMethodId.IMAGE -> onPlaceholderHint(imageHint)
                // 录音中普通点击不打断录音（长按可结束）
                recording -> Unit
                else -> onPlaceholderHint(voiceHint)
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
                recording -> stringResource(R.string.recording)
                else -> stringResource(R.string.extra_with_long_press, label)
            },
        )
    }
}
