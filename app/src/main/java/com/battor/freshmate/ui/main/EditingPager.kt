package com.battor.freshmate.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.common.guideTarget
import com.battor.freshmate.ui.main.MainViewModel.EditingState
import com.battor.freshmate.ui.theme.TrapFill
import java.time.LocalDateTime
import kotlin.math.min
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
    /** 引导模式聚光 key：表单区 / 上下梯形；主流程为 null 零开销（guideFirstCardKey 同款通道） */
    guideFormKey: String? = null,
    guideTrapTopKey: String? = null,
    guideTrapBottomKey: String? = null,
    /** 当前吸附段变化时上报——MainScreen 据此切换顶栏（已添加段显示默认样式） */
    onPageChanged: (EditPageKind) -> Unit = {},
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

        // 段切换（含 key(structure) 重建回编辑区）都把当前段同步出去；初始也发一次，
        // 让顶栏状态与 pager 实际页始终一致
        LaunchedEffect(structure, pagerState.currentPage) {
            structure.pages.getOrNull(pagerState.currentPage)?.let(onPageChanged)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 走查反馈：默认阈值 0.5（半页）要拖很远才吸附，降到 0.2
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = 0.2f,
                ),
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

                        EditPageKind.FORM -> {
                            // 预览+表单整体作为聚光目标（引导编辑态步骤）；补同款 spacedBy(12)
                            // 保持原间距不变观感，EditScrollColumn 的 spacedBy(12) 已覆盖与相邻段内容的间距。
                            // key 判空守卫同 MainScreen 的 guideBucketKey：guideTarget 只收非空
                            Column(
                                modifier = if (guideFormKey != null) Modifier.guideTarget(guideFormKey) else Modifier,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // 预览在编辑区上方（走查反馈确认）、拉开距离：列间距 12 + Spacer 12 + 列间距 12 = 36dp
                                FormPreviewCard(state = editing, saved = editingTarget, now = now)
                                Spacer(Modifier.height(12.dp))
                                ItemForm(
                                    state = editing,
                                    onStateChange = onStateChange,
                                    onPlaceholderHint = onPlaceholderHint,
                                    now = now,
                                )
                            }
                        }

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
                        // 引导第 4 步聚光上梯形（key 判空守卫同上）
                        modifier = if (guideTrapTopKey != null) Modifier.guideTarget(guideTrapTopKey) else Modifier,
                        onClick = {
                            // 走查反馈：默认 spring 太快，换 420ms tween 平滑减速
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage - 1,
                                    animationSpec = tween(durationMillis = 420),
                                )
                            }
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
                        // 引导第 4 步聚光下梯形
                        modifier = if (guideTrapBottomKey != null) Modifier.guideTarget(guideTrapBottomKey) else Modifier,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = tween(durationMillis = 420),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 三段式每页的公共容器：页内自由滚动 + 统一间距/边距；上下预留梯形高度不压内容。 */
@Composable
private fun EditScrollColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 键盘避让收在页内而不是 pager 外层：pager 视口尺寸恒定，IME 弹出不会
            // 触发 pager resize→重算吸附→bring-into-view 的抖动循环（走查 BUG）。
            // 键盘弹出期间贴底梯形被 IME 挡住，属预期（收起即恢复）
            .imePadding()
            .verticalScroll(rememberScrollState())
            // 上下 28dp：为屏幕边缘的梯形色块留位，静止时内容不与梯形重叠（走查反馈）
            .padding(top = 28.dp, bottom = 28.dp, start = 16.dp, end = 16.dp),
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
    modifier: Modifier = Modifier,
) {
    Text(
        text = when (label) {
            TrapLabel.ContinueEditDown -> stringResource(R.string.trap_session_to_form)
            TrapLabel.ContinueEditUp -> stringResource(R.string.trap_existing_to_form)
            is TrapLabel.SessionCount -> stringResource(R.string.trap_form_count, label.count)
            TrapLabel.ViewExisting -> stringResource(R.string.trap_form_to_existing)
        },
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .clip(TrapezoidShape(orientation))
            .background(TrapFill)
            .clickable(onClick = onClick)
            // 走查反馈：更宽更扁——水平 32dp、垂直 1.5dp（总高约 19dp）
            .padding(horizontal = 32.dp, vertical = 1.5.dp),
    )
}

/**
 * 梯形（需求-7 走查确认）：TOP = 上边全宽、下边两侧各收 [slope]（贴上边缘的倒梯形）；
 * BOTTOM 镜像贴下边缘。宽高由内容（Text）撑起，形状只负责斜边。
 * 走查反馈：四角圆角——每顶点向相邻两边各让一段 [cornerRadius]、二次贝塞尔过顶点。
 */
class TrapezoidShape(private val orientation: Orientation) : Shape {

    enum class Orientation { TOP, BOTTOM }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val slope = with(density) { 10.dp.toPx() }.coerceAtMost(size.width / 2f)
        val cornerRadius = with(density) { 6.dp.toPx() }
        val w = size.width
        val h = size.height
        val points = if (orientation == Orientation.TOP) {
            listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w - slope, h), Offset(slope, h))
        } else {
            listOf(Offset(slope, 0f), Offset(w - slope, 0f), Offset(w, h), Offset(0f, h))
        }
        // 每个顶点的圆角：沿两条邻边各退 trim（不超过邻边一半，防相邻圆角重叠），
        // 再以二次贝塞尔经过顶点
        val n = points.size
        val corners = points.mapIndexed { i, p ->
            val prev = points[(i + n - 1) % n]
            val next = points[(i + 1) % n]
            val dPrev = (p - prev).getDistance()
            val dNext = (next - p).getDistance()
            val trim = cornerRadius.coerceAtMost(min(dPrev, dNext) / 2f)
            Triple(
                p + (prev - p) / dPrev * trim, // 圆角起点（朝 prev 方向）
                p,                              // 顶点
                p + (next - p) / dNext * trim,  // 圆角终点（朝 next 方向）
            )
        }
        val path = Path()
        path.moveTo(corners[0].first.x, corners[0].first.y)
        for (i in 0 until n) {
            val (start, vertex, end) = corners[i]
            path.quadraticTo(vertex.x, vertex.y, end.x, end.y)
            val nextStart = corners[(i + 1) % n].first
            path.lineTo(nextStart.x, nextStart.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
