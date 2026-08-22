package com.battor.freshmate.ui.logviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.battor.freshmate.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.battor.freshmate.logging.DailyFileWriter
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(logsDir: File, onBack: () -> Unit) {
    val writer = remember { DailyFileWriter(logsDir) }
    var dates by remember { mutableStateOf(writer.availableDates()) }
    var selected by remember { mutableStateOf(dates.firstOrNull()) }
    var lines by remember { mutableStateOf(selected?.let { writer.read(it) } ?: emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }

    fun select(date: LocalDate) {
        selected = date
        lines = writer.read(date)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title, selected ?: "")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        dates.forEach { date ->
                            DropdownMenuItem(
                                text = { Text(date.toString()) },
                                onClick = { menuOpen = false; select(date) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (lines.isEmpty()) {
            // 复用 Scaffold 的 padding：空态居中
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_logs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(lines) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}
