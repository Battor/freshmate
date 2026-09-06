package com.battor.freshmate.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException

/**
 * 一次性 Snackbar：message 非 null 才展示，展示结束或中途取消都回调 [onShown] 清掉
 * （防离开页面回来重复弹），可选动作按钮（主页「查看更新」用）。
 * 评审修复：此模式原先在 Main/Settings/History 三屏手抄，只有主页保留了
 * CancellationException 守卫——取消泄漏导致一次性提示残留、重进再弹。
 */
@Composable
fun OneShotSnackbar(
    message: UiText?,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    // asString 需 Composable 上下文，先解析再进 effect
    val text = message?.asString()
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        try {
            val result = snackbarHostState.showSnackbar(text, actionLabel = actionLabel)
            onShown()
            if (result == SnackbarResult.ActionPerformed) onAction()
        } catch (e: CancellationException) {
            // 展示中途离开本页（Snackbar 协程被取消）：也清一次性提示，防回来重复弹
            onShown()
            throw e
        }
    }
}
