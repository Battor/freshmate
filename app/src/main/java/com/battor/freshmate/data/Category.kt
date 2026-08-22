package com.battor.freshmate.data

import androidx.annotation.StringRes
import com.battor.freshmate.R

/** 商品分类（设计文档 §4；2026-08-15 用户反馈去掉“其他”，共 8 类）。 */
enum class Category(@StringRes val labelRes: Int) {
    FRUITS_VEG(R.string.category_fruits_veg),
    MEAT_EGG(R.string.category_meat_egg),
    DAIRY(R.string.category_dairy),
    DRINK(R.string.category_drink),
    SNACK(R.string.category_snack),
    STAPLE(R.string.category_staple),
    FROZEN(R.string.category_frozen),
    CONDIMENT(R.string.category_condiment),
}
