package com.battor.freshmate.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * ViewModel 产出的本地化文案载体：UI 层经 asString() 用当前资源配置解析。
 * VM 不再持有裸中文字符串（需求-4 多语言）。
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList())

@Composable
fun UiText.asString(): String = stringResource(id, *args.toTypedArray())
