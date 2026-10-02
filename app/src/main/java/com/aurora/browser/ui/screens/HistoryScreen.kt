package com.aurora.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.data.db.entities.HistoryEntry
import com.aurora.browser.viewmodel.HistoryGroup
import com.aurora.browser.viewmodel.HistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Browsing history: grouped by day, searchable, swipe-to-delete with undo,
 * and clear-all behind a confirm dialog. Tapping a row opens the URL.
 */
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val viewModel: HistoryViewModel = viewModel()
    val groups by viewModel.groupedHistory.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.content_common_undo)
    val deletedMessage = stringResource(R.string.content_history_deleted)

    var showClearConfirm by remember { mutableStateOf(false) }

    fun deleteWithUndo(entry: HistoryEntry) {
        viewModel.deleteEntry(entry)
        scope.launch {
            val autoDismiss = launch {
                delay(5000)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Indefinite
            )
            autoDismiss.cancel()
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreDeleted()
            }
        }
    }

    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_history_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = groups.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.content_history_clear_all)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setSearchQuery,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                placeholder = { Text(stringResource(R.string.content_history_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (groups.isEmpty()) {
                EmptyState(text = stringResource(R.string.content_history_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groups.forEach { group ->
                        item(key = "header:${group.title}") {
                            HistoryDayHeader(group)
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            SwipeableHistoryRow(
                                entry = entry,
                                onOpen = { onOpenUrl(entry.url) },
                                onDelete = { deleteWithUndo(entry) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.content_history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.content_history_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearAll()
                }) {
                    Text(stringResource(R.string.content_common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.content_common_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryDayHeader(group: HistoryGroup) {
    Text(
        text = group.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * One history row. Swiping it away (end-to-start) deletes the entry; the screen
 * then offers Undo for 5 seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val isDismissed = dismissState.currentValue != SwipeToDismissBoxValue.Settled
    LaunchedEffect(isDismissed) {
        if (isDismissed) onDelete()
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        HistoryRowContent(entry = entry, onOpen = onOpen)
    }
}

@Composable
private fun HistoryRowContent(entry: HistoryEntry, onOpen: () -> Unit) {
    val context = LocalContext.current
    // Device-locale time format (e.g. "14:32" or "2:32 PM").
    val timeText = remember(entry.lastVisited) {
        android.text.format.DateFormat.getTimeFormat(context).format(Date(entry.lastVisited))
    }
    ListItem(
        headlineContent = {
            Text(
                text = entry.title.ifBlank { entry.url },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = entry.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onOpen)
    )
}
