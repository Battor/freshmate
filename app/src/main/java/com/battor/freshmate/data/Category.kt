package com.battor.freshmate.data

/** 商品分类（设计文档 §4，共 9 类）。 */
enum class Category(val label: String) {
    FRUITS_VEG("果蔬"),
    MEAT_EGG("肉蛋"),
    DAIRY("乳品"),
    DRINK("饮料"),
    SNACK("零食"),
    STAPLE("主食"),
    FROZEN("冷冻"),
    CONDIMENT("调味"),
    OTHER("其他"),
}
