package com.battor.freshmate.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.battor.freshmate.data.Category

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.FRUITS_VEG -> Icons.Filled.Eco
    Category.MEAT_EGG -> Icons.Filled.SetMeal
    Category.DAIRY -> Icons.Filled.WaterDrop
    Category.DRINK -> Icons.Filled.LocalDrink
    Category.SNACK -> Icons.Filled.Cookie
    Category.STAPLE -> Icons.Filled.BakeryDining
    Category.FROZEN -> Icons.Filled.AcUnit
    Category.CONDIMENT -> Icons.Filled.SoupKitchen
}
