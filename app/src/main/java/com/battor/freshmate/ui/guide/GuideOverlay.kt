package com.battor.freshmate.ui.guide

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.battor.freshmate.R
import com.battor.freshmate.ui.common.GuideKeys
import com.battor.freshmate.ui.common.GuideStateHolder
import kotlin.math.roundToInt

/** 引导步骤定义：targetKey = null 表示无聚光（居中欢迎卡）。 */
data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val targetKey: String?,
    /** 本步追加通知权限提示（声明式标记，不耦合 targetKey 字符串）。 */
    val appendPermissionNote: Boolean = false,
)

internal val GuideSteps = listOf(
    GuideStep(R.string.guide_welcome_title, R.string.guide_welcome_body, targetKey = null),
    GuideStep(R.string.guide_fab_title, R.string.guide_fab_body, GuideKeys.FAB),
    GuideStep(R.string.guide_card_title, R.string.guide_card_body, GuideKeys.FIRST_CARD),
    GuideStep(R.string.guide_buckets_title, R.string.guide_buckets_body, GuideKeys.BUCKET_AREA, appendPermissionNote = true),
    GuideStep(R.string.guide_topbar_title, R.string.guide_topbar_body, GuideKeys.TOPBAR),
)

private val ScrimColor = Color.Black.copy(alpha = 0.55f)

/**
 * 说明卡纵向落点（纯函数，边界单测护住）：目标在屏上半 → 卡贴镂空下方；否则贴上方。
 * 两头钳制在屏内：目标特大时上方/下方都可能放不下，宁贴边不裁切。
 */
internal fun cardOffsetY(hole: Rect, overlayHeight: Int, cardHeight: Int, gapPx: Float): Int {
    val raw = if (hole.center.y < overlayHeight / 2f) {
        hole.bottom + gapPx
    } else {
        hole.top - gapPx - cardHeight
    }
    return raw.roundToInt().coerceIn(0, (overlayHeight - cardHeight).coerceAtLeast(0))
}

/**
 * 自绘引导层（spec 2026-09-03）：全屏遮罩 + 圆角镂空聚光 + 说明卡。
 * 覆盖在 MainContent 之上并消费全部点击（「只看不摸」）；GuideScreen 的 Box 直铺
 * 路由内容、与 MainContent 同一 root，boundsInRoot 减去自身偏移即本层局部坐标。
 */
@Composable
fun GuideOverlay(
    holder: GuideStateHolder,
    stepIndex: Int,
    showPermissionNote: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = GuideSteps[stepIndex]
    val isLast = stepIndex == GuideSteps.lastIndex
    // pointerInput(Unit) 只捕获首个 lambda 闭包：经 rememberUpdatedState 取最新回调（滑动同款坑）
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnSkip by rememberUpdatedState(onSkip)
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val holePaddingPx = with(LocalDensity.current) { 8.dp.toPx() }
    val nextLabel = stringResource(if (isLast) R.string.guide_done else R.string.guide_next)
    val skipLabel = stringResource(R.string.guide_skip)

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayOffset = it.boundsInRoot().topLeft
                overlaySize = it.size
            }
            // 全屏拦截触摸（「只看不摸」，滚动不会落入下层）；点遮罩/说明卡空白 = 下一步/结束
            .pointerInput(Unit) { detectTapGestures { currentOnNext() } }
            // TalkBack：遮罩点按推进对手势用户不可达，提供等价自定义动作
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { currentOnNext(); true },
                    CustomAccessibilityAction(skipLabel) { currentOnSkip(); true },
                )
            },
    ) {
        val targetHole = step.targetKey
            ?.let { holder.targets[it] }
            ?.translate(-overlayOffset)
            ?.inflate(holePaddingPx)

        // 镂空平滑滑向新目标；首个聚光步（null→rect）与布局首帧（尺寸未测得）直接落位不动画
        var animatedHole by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(targetHole, overlaySize) {
            if (targetHole == null || overlaySize == IntSize.Zero) return@LaunchedEffect
            val from = animatedHole
            if (from == null || from == targetHole) {
                animatedHole = targetHole
            } else {
                val anim = Animatable(from, Rect.VectorConverter)
                anim.animateTo(targetHole, tween(280)) { animatedHole = value }
            }
        }
        val hole = animatedHole ?: targetHole

        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(14.dp.toPx())
            // EvenOdd 填充：整屏矩形 XOR 镂空圆角矩形 = 带洞遮罩
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                hole?.let { addRoundRect(RoundRect(rect = it, cornerRadius = corner)) }
            }
            drawPath(path, ScrimColor)
            hole?.let {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = corner,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // 说明卡纵向定位。首帧卡高未测得按 0 估，onSizeChanged 回填后下一帧自然校正
        var cardHeightPx by remember { mutableStateOf(0) }
        if (hole == null) {
            // 无聚光（欢迎步）：卡片居中
            GuideCard(
                step = step,
                stepIndex = stepIndex,
                showPermissionNote = showPermissionNote,
                onSkip = onSkip,
                onNext = onNext,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            )
        } else {
            val gapPx = with(LocalDensity.current) { 24.dp.toPx() }
            val cardY = cardOffsetY(hole, overlaySize.height, cardHeightPx, gapPx)
            GuideCard(
                step = step,
                stepIndex = stepIndex,
                showPermissionNote = showPermissionNote,
                onSkip = onSkip,
                onNext = onNext,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, end = 24.dp)
                    .absoluteOffset { IntOffset(0, cardY) }
                    .onSizeChanged { cardHeightPx = it.height },
            )
        }
    }
}

/** 说明卡：步数指示 + 标题 + 正文（按步骤声明追加权限提示）+ 跳过 + 下一步/完成。 */
@Composable
private fun GuideCard(
    step: GuideStep,
    stepIndex: Int,
    showPermissionNote: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.guide_step_indicator, stepIndex + 1, GuideSteps.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(step.titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                buildString {
                    append(stringResource(step.bodyRes))
                    if (showPermissionNote && step.appendPermissionNote) {
                        append("\n\n")
                        append(stringResource(R.string.guide_permission_note))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            // skip 紧贴主操作左侧（成组右对齐，走查反馈：不要拆到两端）
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.guide_skip))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onNext) {
                    Text(
                        stringResource(
                            if (stepIndex == GuideSteps.lastIndex) R.string.guide_done else R.string.guide_next,
                        ),
                    )
                }
            }
        }
    }
}
