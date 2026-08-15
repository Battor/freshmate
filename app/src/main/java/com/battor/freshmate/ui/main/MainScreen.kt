package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val GroupHeaderFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("食刻 FreshMate") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val editId = state.editing?.editingItemId
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.editing?.let { editing ->
                item(key = "editing_form") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupHeader(editing.createdAt)
                        ItemForm(
                            state = editing,
                            onStateChange = { newState -> viewModel.updateEditing { newState } },
                            onPlaceholderHint = { hint ->
                                scope.launch { snackbarHostState.showSnackbar(hint) }
                            },
                        )
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = "header_${group.createdAt}") {
                    GroupHeader(group.createdAt)
                }
                items(group.items.filter { it.id != editId }, key = { it.id }) { item ->
                    FoodItemCard(
                        item = item,
                        onClick = { viewModel.startEdit(item) },
                        onDelete = {
                            viewModel.delete(item)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    "已删除「${item.name}」",
                                    actionLabel = "撤销",
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete(item)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(createdAt: LocalDateTime) {
    Surface(color = Color(0xFF616161), shape = RoundedCornerShape(10.dp)) {
        Text(
            createdAt.format(GroupHeaderFormat),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
