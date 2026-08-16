package com.battor.freshmate.ui.navigation

import android.content.Context
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
import com.battor.freshmate.ui.logviewer.LogViewerScreen
import com.battor.freshmate.ui.main.MainScreen
import com.battor.freshmate.ui.main.MainViewModel
import com.battor.freshmate.ui.settings.AboutScreen
import com.battor.freshmate.ui.settings.SettingsScreen
import java.io.File

/** 路由常量：拼错导航名只在编译期发现，不在运行期静默失败。 */
private object Routes {
    const val MAIN = "main"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val LOG_VIEWER = "logviewer"
    const val ABOUT = "about"
}

@Composable
fun FreshMateNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            val context = LocalContext.current.applicationContext
            val viewModel: MainViewModel = viewModel(factory = mainFactory(context))
            MainScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.HISTORY) {
            val context = LocalContext.current.applicationContext
            val viewModel: HistoryViewModel = viewModel(factory = historyFactory(context))
            HistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate(Routes.LOG_VIEWER) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.LOG_VIEWER) {
            val context = LocalContext.current.applicationContext
            LogViewerScreen(
                logsDir = File(context.filesDir, "logs"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}

/** 各路由自建 ViewModel（仓库/调度器构造集中在此，避免三处复制）。 */
private fun mainFactory(context: Context) = viewModelFactory {
    initializer {
        MainViewModel(
            repository = FoodItemRepository(FoodItemDatabase.get(context).foodItemDao()),
            scheduler = ReminderScheduler(context),
        )
    }
}

private fun historyFactory(context: Context) = viewModelFactory {
    initializer {
        HistoryViewModel(
            repository = FoodItemRepository(FoodItemDatabase.get(context).foodItemDao()),
            scheduler = ReminderScheduler(context),
        )
    }
}
