package com.battor.freshmate.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.main.MainViewModel.EditingState
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/**
 * 编辑态三段式（需求-7）：VerticalPager 每段一页——本次操作 / 编辑区（默认）/ 已添加。
 * 段内各自 verticalScroll，滚到边缘继续拖由 pager 接管，松手按官方 snap 吸附到相邻段。
 * 上下屏幕边缘的梯形色块提示相邻段，点击等效滑动手势。
 */
@Composable
fun EditingPager(
    pinned: List<FoodItem>,
    buckets: List<ExpiryBucket>,
    editing: EditingState,
    editingTarget: FoodItem?,
    now: LocalDateTime,
    onStateChange: (EditingState) -> Unit,
    onPlaceholderHint: (String) -> Unit,
    onStartEdit: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
) {
    val hasExisting = buckets.isNotEmpty()
    val structure = remember(pinned.isNotEmpty(), hasExisting) {
        editingPages(hasSession = pinned.isNotEmpty(), hasExisting = hasExisting)
    }
    // key(structure)：段数变化（首条会话新增落库）时页索引整体平移，重建 pager 并按
    // defaultIndex 回到编辑区——与「保存后停在编辑区」一致。平时击键/保存不触发重建。
    key(structure) {
        val pagerState = rememberPagerState(initialPage = structure.defaultIndex) { structure.pages.size }
        val scope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                EditScrollColumn {
                    when (structure.pages[page]) {
                        EditPageKind.SESSION -> BucketBox(
                            header = stringResource(R.string.pinned_header),
                            status = null,
                            items = pinned,
                            now = now,
                            cardsEnabled = false,
                            onHeaderAction = null,
                            onStartEdit = onStartEdit,
                            onDeleteItem = onDeleteItem,
                        )

                        EditPageKind.FORM -> ItemForm(
                            state = editing,
                            onStateChange = onStateChange,
                            onPlaceholderHint = onPlaceholderHint,
                            editingTarget = editingTarget,
                            now = now,
                        )

                        EditPageKind.EXISTING -> buckets.forEach { bucket ->
                            BucketBox(
                                header = stringResource(bucket.status.labelRes),
                                status = bucket.status,
                                items = bucket.items,
                                now = now,
                                cardsEnabled = false,
                                onHeaderAction = null,
                                onStartEdit = onStartEdit,
                                onDeleteItem = onDeleteItem,
                            )
                        }
                    }
                }
            }

            // 退场中标签已变 null，AnimatedVisibility 的内容仍需可组合：记住最后一个非空标签
            val current = structure.pages.getOrNull(pagerState.currentPage) ?: EditPageKind.FORM
            val (top, bottom) = trapLabels(current, sessionCount = pinned.size, hasExisting = hasExisting)
            var lastTop by remember { mutableStateOf<TrapLabel?>(null) }
            var lastBottom by remember { mutableStateOf<TrapLabel?>(null) }
            if (top != null) lastTop = top
            if (bottom != null) lastBottom = bottom

            AnimatedVisibility(
                visible = top != null,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(animationSpec = spring(dampingRatio = 0.6f)) { -it },
                exit = slideOutVertically { -it },
            ) {
                lastTop?.let { label ->
                    TrapIndicator(
                        label = label,
                        orientation = TrapezoidShape.Orientation.TOP,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = bottom != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(animationSpec = spring(dampingRatio = 0.6f)) { it },
                exit = slideOutVertically { it },
            ) {
                lastBottom?.let { label ->
                    TrapIndicator(
                        label = label,
                        orientation = TrapezoidShape.Orientation.BOTTOM,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                    )
                }
            }
        }
    }
}

/** 三段式每页的公共容器：页内自由滚动 + 统一间距/边距。 */
@Composable
private fun EditScrollColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

/** 梯形色块：≈22dp 扁、宽度随文案自适应、点击跳相邻段。 */
@Composable
private fun TrapIndicator(
    label: TrapLabel,
    orientation: TrapezoidShape.Orientation,
    onClick: () -> Unit,
) {
    Text(
        text = when (label) {
            TrapLabel.ContinueEditDown -> stringResource(R.string.trap_session_to_form)
            TrapLabel.ContinueEditUp -> stringResource(R.string.trap_existing_to_form)
            is TrapLabel.SessionCount -> stringResource(R.string.trap_form_count, label.count)
            TrapLabel.ViewExisting -> stringResource(R.string.trap_form_to_existing)
        },
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .clip(TrapezoidShape(orientation))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * 梯形（需求-7 走查确认）：TOP = 上边全宽、下边两侧各收 [slope]（贴上边缘的倒梯形）；
 * BOTTOM 镜像贴下边缘。宽高由内容（Text）撑起，形状只负责斜边。
 */
class TrapezoidShape(private val orientation: Orientation) : Shape {

    enum class Orientation { TOP, BOTTOM }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val slope = with(density) { 10.dp.toPx() }.coerceAtMost(size.width / 2f)
        val path = Path().apply {
            if (orientation == Orientation.TOP) {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - slope, size.height)
                lineTo(slope, size.height)
            } else {
                moveTo(slope, 0f)
                lineTo(size.width - slope, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
