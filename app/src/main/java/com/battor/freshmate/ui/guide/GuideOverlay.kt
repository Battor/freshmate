package com.battor.freshmate.ui.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.battor.freshmate.R
import kotlin.math.roundToInt

/**
 * 聚光目标注册：目标控件挂 [guideTarget]，把自身 bounds（root 坐标）报进来。
 * 挂在 CompositionLocal 上——主流程里 LocalGuideState 为 null，guideTarget 原样返回，零开销。
 */
class GuideStateHolder {
    val targets = mutableStateMapOf<String, Rect>()
}

val LocalGuideState = compositionLocalOf<GuideStateHolder?> { null }

/** 引导步骤定义：targetKey = null 表示无聚光（居中欢迎卡）。 */
data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val targetKey: String?,
)

internal val GuideSteps = listOf(
    GuideStep(R.string.guide_welcome_title, R.string.guide_welcome_body, null),
    GuideStep(R.string.guide_fab_title, R.string.guide_fab_body, "fab"),
    GuideStep(R.string.guide_card_title, R.string.guide_card_body, "first_card"),
    GuideStep(R.string.guide_buckets_title, R.string.guide_buckets_body, "bucket_area"),
    GuideStep(R.string.guide_topbar_title, R.string.guide_topbar_body, "topbar"),
)

/**
 * @Composable 修饰符工厂：引导态把 bounds 报到 holder，主流程（holder 为 null）原样返回。
 * 必须是 @Composable 才能读 CompositionLocal，避免 composed{} 的性能与限制问题。
 */
@Composable
fun Modifier.guideTarget(key: String): Modifier {
    val holder = LocalGuideState.current ?: return this
    return onGloballyPositioned { holder.targets[key] = it.boundsInRoot() }
}

private val ScrimColor = Color.Black.copy(alpha = 0.55f)

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
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayOffset = it.boundsInRoot().topLeft
                overlaySize = it.size
            }
            // 消费一切触摸：down 在本层被拦截，滚动/滑动不会落入下层界面（「只看不摸」）
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val targetRect = step.targetKey?.let { holder.targets[it] }

        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(14.dp.toPx())
            val hole = targetRect?.translate(-overlayOffset)?.inflate(8.dp.toPx())
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

        // 说明卡纵向定位：目标在屏上半 → 卡贴镂空下方；否则贴上方。
        // 首帧卡高未测得按 0 估，onSizeChanged 回填后下一帧自然校正。
        var cardHeightPx by remember { mutableStateOf(0) }
        if (targetRect == null) {
            // 无聚光（欢迎步）：卡片居中
            GuideCard(
                step = step,
                stepIndex = stepIndex,
                showPermissionNote = showPermissionNote,
                onNext = onNext,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            )
        } else {
            val gapPx = with(LocalDensity.current) { 24.dp.toPx() }
            val local = targetRect.translate(-overlayOffset)
            val cardY = if (local.center.y < overlaySize.height / 2f) {
                (local.bottom + gapPx).roundToInt()
            } else {
                (local.top - gapPx - cardHeightPx).roundToInt()
            }
            GuideCard(
                step = step,
                stepIndex = stepIndex,
                showPermissionNote = showPermissionNote,
                onNext = onNext,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, end = 24.dp)
                    .absoluteOffset { IntOffset(0, cardY) }
                    .onSizeChanged { cardHeightPx = it.height },
            )
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp),
        ) {
            Text(stringResource(R.string.guide_skip), color = Color.White)
        }
    }
}

/** 说明卡：步数指示 + 标题 + 正文（第 4 步按条件追加权限提示）+ 下一步/完成。 */
@Composable
private fun GuideCard(
    step: GuideStep,
    stepIndex: Int,
    showPermissionNote: Boolean,
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
                    if (showPermissionNote && step.targetKey == "bucket_area") {
                        append("\n\n")
                        append(stringResource(R.string.guide_permission_note))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onNext,
                modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
            ) {
                Text(
                    stringResource(
                        if (stepIndex == GuideSteps.lastIndex) R.string.guide_done else R.string.guide_next,
                    ),
                )
            }
        }
    }
}
