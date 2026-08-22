package com.battor.freshmate.ui.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.battor.freshmate.data.DataStoreSettingsRepository
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
import com.battor.freshmate.ui.settings.SettingsViewModel
import com.battor.freshmate.update.UpdateViewModel
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
            // Activity 级共享：main 的静默检查与 settings 的对话框用同一个 UpdateViewModel
            val updateViewModel = sharedUpdateViewModel()
            LaunchedEffect(Unit) { updateViewModel.check(manual = false) }
            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            MainScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                updateHint = updateState.silentFound?.let { "发现新版本 ${it.versionName}" },
                onUpdateHintShown = updateViewModel::clearSilentFound,
                onOpenUpdate = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.HISTORY) {
            val context = LocalContext.current.applicationContext
            val viewModel: HistoryViewModel = viewModel(factory = historyFactory(context))
            HistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current.applicationContext
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(DataStoreSettingsRepository(context)) }
                },
            )
            val updateViewModel = sharedUpdateViewModel()
            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate(Routes.LOG_VIEWER) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                updateState = updateState,
                onCheckUpdate = { updateViewModel.check(manual = true) },
                onDownload = updateViewModel::download,
                onInstall = updateViewModel::install,
                onDismissNotice = updateViewModel::clearNotice,
                onDismissUpdate = updateViewModel::dismissManifest,
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

/**
 * Activity 作用域的 UpdateViewModel：main（启动静默检查）与 settings（对话框/下载/安装）
 * 必须共享同一实例。viewModel() 默认按 NavBackStackEntry 作用域，这里显式挂到 Activity。
 */
@Composable
private fun sharedUpdateViewModel(): UpdateViewModel {
    val context = LocalContext.current.applicationContext
    val activity = LocalContext.current as ComponentActivity
    return viewModel(
        viewModelStoreOwner = activity,
        factory = viewModelFactory { initializer { UpdateViewModel(context) } },
    )
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
