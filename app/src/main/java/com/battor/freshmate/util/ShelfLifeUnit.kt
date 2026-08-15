package com.battor.freshmate.util

/** 保质期单位。设计文档约定：1 个月 = 30 天，1 年 = 365 天。 */
enum class ShelfLifeUnit(val label: String, val days: Int) {
    DAY("天", 1),
    WEEK("周", 7),
    MONTH("月", 30),
    YEAR("年", 365),
}

fun shelfLifeToDays(value: Int, unit: ShelfLifeUnit): Int = value * unit.days
