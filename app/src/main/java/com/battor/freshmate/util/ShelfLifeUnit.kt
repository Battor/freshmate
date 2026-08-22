package com.battor.freshmate.util

import androidx.annotation.StringRes
import com.battor.freshmate.R

/** 保质期单位。设计文档约定：1 个月 = 30 天，1 年 = 365 天。 */
enum class ShelfLifeUnit(@StringRes val labelRes: Int, val days: Int) {
    DAY(R.string.unit_day, 1),
    WEEK(R.string.unit_week, 7),
    MONTH(R.string.unit_month, 30),
    YEAR(R.string.unit_year, 365),
}

fun shelfLifeToDays(value: Int, unit: ShelfLifeUnit): Int = value * unit.days
