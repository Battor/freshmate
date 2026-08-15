package com.battor.freshmate

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.data.FoodItemRepository
import com.battor.freshmate.notification.ReminderScheduler
import com.battor.freshmate.ui.main.MainScreen
import com.battor.freshmate.ui.main.MainViewModel
import com.battor.freshmate.ui.theme.FreshMateTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { factory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreshMateTheme { MainScreen(viewModel) }
        }
    }

    companion object {
        fun factory(appContext: Context) = viewModelFactory {
            initializer {
                MainViewModel(
                    repository = FoodItemRepository(
                        FoodItemDatabase.get(appContext).foodItemDao(),
                    ),
                    scheduler = ReminderScheduler(appContext),
                )
            }
        }
    }
}
