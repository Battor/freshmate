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
 * id 取负数：与任何真实条目 id 空间隔离，排查日志一眼可辨。
 * 名称由调用方经 stringResource 按当前语言解析后传入（VM 层拿不到资源）。
 */
class GuideViewModel(
    milkName: String,
    yogurtName: String,
    vegetableName: String,
    now: LocalDateTime = LocalDateTime.now(),
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
}
