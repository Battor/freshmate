package com.battor.freshmate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.data.FoodItemRepository
import com.battor.freshmate.notification.ReminderScheduler
import com.battor.freshmate.ui.history.HistoryScreen
import com.battor.freshmate.ui.history.HistoryViewModel
import com.battor.freshmate.ui.main.MainScreen
import com.battor.freshmate.ui.main.MainViewModel

@Composable
fun FreshMateNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            val context = LocalContext.current.applicationContext
            val viewModel: MainViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    MainViewModel(
                        repository = FoodItemRepository(
                            FoodItemDatabase.get(context).foodItemDao(),
                        ),
                        scheduler = ReminderScheduler(context),
                    )
                }
            })
            MainScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate("history") },
                onOpenSettings = { }, // 设置路由 Task 11 接入
            )
        }
        composable("history") {
            val context = LocalContext.current.applicationContext
            val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    HistoryViewModel(
                        repository = FoodItemRepository(
                            FoodItemDatabase.get(context).foodItemDao(),
                        ),
                        scheduler = ReminderScheduler(context),
                    )
                }
            })
            HistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
