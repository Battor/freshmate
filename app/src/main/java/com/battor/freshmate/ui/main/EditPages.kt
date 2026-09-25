package com.battor.freshmate.ui.main

/**
 * 编辑态三段式（需求-7）的纯逻辑：页列表构建 + 梯形文案映射。
 * 无 Compose 依赖，可直测；UI 侧（EditingPager）只消费这两个函数的结果。
 */

/** 编辑态三段式的段类型（从上到下）：会话新增 → 表单 → 既有列表。 */
enum class EditPageKind { SESSION, FORM, EXISTING }

/** 页列表 + 默认停哪页（编辑区）。空段不生成页。 */
data class EditingPages(val pages: List<EditPageKind>, val defaultIndex: Int)

fun editingPages(hasSession: Boolean, hasExisting: Boolean): EditingPages {
    val pages = buildList {
        if (hasSession) add(EditPageKind.SESSION)
        add(EditPageKind.FORM)
        if (hasExisting) add(EditPageKind.EXISTING)
    }
    return EditingPages(pages, pages.indexOf(EditPageKind.FORM))
}

/** 梯形文案种类；null（由 trapLabels 返回）= 该方向无梯形。 */
sealed interface TrapLabel {
    /** 下滑继续编辑（本次操作段底边）。 */
    data object ContinueEditDown : TrapLabel

    /** 上滑继续编辑（已添加段顶边）。 */
    data object ContinueEditUp : TrapLabel

    /** 已添加 n 条（表单段顶边；n=0 时不生成）。 */
    data class SessionCount(val count: Int) : TrapLabel

    /** 下滑查看已有（表单段底边）。 */
    data object ViewExisting : TrapLabel
}

/** @return (上梯形文案?, 下梯形文案?)——按当前段与数据有无决定显示/隐藏。 */
fun trapLabels(
    current: EditPageKind,
    sessionCount: Int,
    hasExisting: Boolean,
): Pair<TrapLabel?, TrapLabel?> = when (current) {
    EditPageKind.SESSION -> null to TrapLabel.ContinueEditDown
    EditPageKind.FORM ->
        (sessionCount.takeIf { it > 0 }?.let { TrapLabel.SessionCount(it) }) to
            if (hasExisting) TrapLabel.ViewExisting else null
    EditPageKind.EXISTING -> TrapLabel.ContinueEditUp to null
}
