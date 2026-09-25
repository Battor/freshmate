package com.battor.freshmate.util

import com.battor.freshmate.data.FoodItem
import java.time.LocalDateTime

/** 统一推送的两档（按紧急度），档内按到期时间升序（最紧急在前）。 */
data class DigestTiers(val dueWithin1d: List<FoodItem>, val dueWithin3d: List<FoodItem>)

/**
 * 统一推送分档：复用主列表 expiryStatus 阈值——DUE_1D 一档、DUE_3D 一档，
 * EXPIRED 与 7 天及以上不推（spec：已过期不推、较长时间过期不通知）。
 */
fun digestTiers(items: List<FoodItem>, now: LocalDateTime): DigestTiers {
    fun expiry(item: FoodItem) =
        expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    fun tier(status: ExpiryStatus) = items
        .filter { expiryStatus(expiry(it), now) == status }
        .sortedBy { expiry(it) }
    return DigestTiers(tier(ExpiryStatus.DUE_1D), tier(ExpiryStatus.DUE_3D))
}
