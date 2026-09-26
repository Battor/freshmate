package com.battor.freshmate.ui.guide

import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.main.MainViewModel
import com.battor.freshmate.ui.main.bucketItems
import java.time.LocalDateTime

/**
 * 引导模式数据源：纯内存 mock 三件条目（覆盖已过期/1 天内/7 天内三个色档），
 * 不持有 Repository、不写 Room、不碰提醒调度——用户真实数据全程不被触及，
 * 「引导结束还原原数据」因此自动成立。
 * 非 ViewModel（无生命周期、无状态流），故不叫 ViewModel：一次构建、uiState 只读。
 * id 取负数：与任何真实条目 id 空间隔离，排查日志一眼可辨。
 * 名称由调用方经 stringResource 按当前语言解析后传入（本层拿不到资源）。
 */
class GuideMockContent(
    milkName: String,
    yogurtName: String,
    vegetableName: String,
    private val now: LocalDateTime = LocalDateTime.now(),
    private val formName: String = "",
    private val breadName: String = "",
    private val eggName: String = "",
) {

    val uiState: MainViewModel.UiState = run {
        // expiry = createdAt + shelfLifeDays（productionDate = null 时），倒推 createdAt 落桶
        fun mock(id: Long, name: String, category: Category, shelfLifeDays: Int, createdAt: LocalDateTime) =
            FoodItem(
                id = -id,
                name = name,
                category = category,
                productionDate = null,
                shelfLifeDays = shelfLifeDays,
                quantity = null,
                createdAt = createdAt,
            )

        val items = listOf(
            mock(1, milkName, Category.DAIRY, shelfLifeDays = 10, createdAt = now.minusDays(13)), // 到期 now-3d
            mock(2, yogurtName, Category.DAIRY, shelfLifeDays = 7, createdAt = now.plusHours(11).minusDays(7)), // 到期 now+11h
            mock(3, vegetableName, Category.FRUITS_VEG, shelfLifeDays = 7, createdAt = now.minusDays(2)), // 到期 now+5d
        )
        MainViewModel.UiState(items = items, now = now, buckets = bucketItems(items, now))
    }

    /**
     * 按引导步骤派生界面（specs/2026-09-26-fab-input-and-guide）：isEditingStep = true
     * （对应 GuideSteps 中 showEditingMock = true 的两步，由调用方传入——本层不依赖
     * GuideOverlay/GuideSteps，保持纯数据无 UI 依赖）返回预填表单 + 会话条目的三段式，
     * 否则返回基础主页态。纯函数，不修改基础 uiState。
     * 叙事：刚录完面包和鸡蛋（本次添加），正在录草莓（表单预填）→ 预览卡有内容、
     * 顶栏 ✓ 出现、FORM 页恒有上下两个梯形。
     */
    fun uiStateForStep(isEditingStep: Boolean): MainViewModel.UiState {
        if (!isEditingStep) return uiState
        val bread = FoodItem(
            id = -4L, name = breadName, category = Category.STAPLE,
            productionDate = null, shelfLifeDays = 5,
            quantity = null, createdAt = now.minusMinutes(8),
        )
        val egg = FoodItem(
            id = -5L, name = eggName, category = Category.MEAT_EGG,
            productionDate = null, shelfLifeDays = 15,
            quantity = null, createdAt = now.minusMinutes(3),
        )
        return uiState.copy(
            editing = MainViewModel.EditingState(
                name = formName,
                category = Category.FRUITS_VEG,
                shelfLifeValue = "3",
                createdAt = now,
            ),
            sessionItemIds = setOf(-4L, -5L),
            pinnedItems = listOf(egg, bread), // 新→旧（createdAt 倒序）
            buckets = uiState.buckets,        // 会话条目不重复入桶（mock 直接给派生值）
        )
    }
}
