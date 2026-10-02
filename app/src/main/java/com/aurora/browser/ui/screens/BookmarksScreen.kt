package com.aurora.browser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aurora.browser.R
import com.aurora.browser.data.db.entities.BookmarkFolder
import com.aurora.browser.data.db.entities.DEFAULT_FOLDER_BOOKMARKS_BAR
import com.aurora.browser.data.db.entities.DEFAULT_FOLDER_OTHER
import com.aurora.browser.data.repository.BookmarkWithFolder
import com.aurora.browser.viewmodel.BookmarksViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The bookmarks list: search, folder chips, add/edit/delete/move, multi-select,
 * and folder management. Tapping a row opens the URL in the current tab.
 */
@Composable
fun BookmarksScreen(onNavigateBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val viewModel: BookmarksViewModel = viewModel()
    val bookmarks by viewModel.visibleBookmarks.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.content_common_undo)
    val singleDeletedMessage = stringResource(R.string.content_bookmarks_deleted, 1)
    val defaultFolderKeptMessage = stringResource(R.string.content_bookmarks_folder_is_default)
    val nonEmptyFolderKeptMessage = stringResource(R.string.content_bookmarks_folder_not_empty)

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BookmarkWithFolder?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveTargets by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var folderMenuTarget by remember { mutableStateOf<BookmarkFolder?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<BookmarkFolder?>(null) }

    // Deletes, then shows an Undo snackbar that auto-dismisses after 5 seconds.
    fun deleteWithUndo(items: List<BookmarkWithFolder>, message: String) {
        viewModel.deleteBookmarks(items)
        scope.launch {
            val autoDismiss = launch {
                delay(5000)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
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
            if (isSelectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onDelete = { showDeleteConfirm = true },
                    onMove = {
                        moveTargets = selectedIds
                        showMoveDialog = true
                    }
                )
            } else {
                ContentTopBar(
                    title = stringResource(R.string.content_bookmarks_title),
                    onNavigateBack = onNavigateBack
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.content_bookmarks_add))
                }
            }
        }
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
                placeholder = { Text(stringResource(R.string.content_bookmarks_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            FolderChipsRow(
                folders = folders,
                selectedFolderId = selectedFolderId,
                onSelect = viewModel::selectFolder,
                onAddFolder = { showFolderDialog = true },
                onFolderLongPress = { folderMenuTarget = it }
            )
            if (bookmarks.isEmpty()) {
                EmptyState(text = stringResource(R.string.content_bookmarks_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(bookmarks, key = { it.bookmark.id }) { item ->
                        val selected = item.bookmark.id in selectedIds
                        BookmarkRow(
                            item = item,
                            selected = selected,
                            selectionMode = isSelectionMode,
                            onOpen = { onOpenUrl(item.bookmark.url) },
                            onToggleSelect = { viewModel.toggleSelection(item.bookmark.id) },
                            onEdit = { editingItem = item },
                            onMove = {
                                moveTargets = setOf(item.bookmark.id)
                                showMoveDialog = true
                            },
                            onDelete = {
                                deleteWithUndo(listOf(item), singleDeletedMessage)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        BookmarkEditDialog(
            initial = null,
            folders = folders,
            onDismiss = { showAddDialog = false },
            onSave = { title, url, folderId -> viewModel.addBookmark(title, url, folderId) }
        )
    }
    editingItem?.let { item ->
        BookmarkEditDialog(
            initial = item,
            folders = folders,
            onDismiss = { editingItem = null },
            onSave = { title, url, folderId ->
                viewModel.updateBookmark(item.bookmark.id, title, url, folderId)
            }
        )
    }
    if (showMoveDialog) {
        MoveToFolderDialog(
            folders = folders,
            onDismiss = { showMoveDialog = false },
            onMove = { folderId ->
                viewModel.moveBookmarks(moveTargets, folderId)
                showMoveDialog = false
            }
        )
    }
    if (showDeleteConfirm) {
        val targets = viewModel.selectedItems()
        val message = stringResource(R.string.content_bookmarks_deleted, targets.size)
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.content_bookmarks_delete_selected_title, targets.size)) },
            text = { Text(stringResource(R.string.content_bookmarks_delete_selected_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    deleteWithUndo(targets, message)
                }) {
                    Text(stringResource(R.string.content_common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.content_common_cancel))
                }
            }
        )
    }
    folderMenuTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderMenuTarget = null },
            title = { Text(folder.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        renamingFolder = folder
                        folderMenuTarget = null
                    }) {
                        Text(stringResource(R.string.content_bookmarks_rename_folder))
                    }
                    TextButton(onClick = {
                        folderMenuTarget = null
                        // The repository keeps the folder when it is a default folder
                        // or still holds bookmarks — explain which one it was.
                        val keptMessage =
                            if (folder.name == DEFAULT_FOLDER_BOOKMARKS_BAR || folder.name == DEFAULT_FOLDER_OTHER) {
                                defaultFolderKeptMessage
                            } else {
                                nonEmptyFolderKeptMessage
                            }
                        viewModel.deleteFolder(folder.id) { deleted ->
                            if (!deleted) {
                                scope.launch { snackbarHostState.showSnackbar(keptMessage) }
                            }
                        }
                    }) {
                        Text(stringResource(R.string.content_common_delete))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { folderMenuTarget = null }) {
                    Text(stringResource(R.string.content_common_close))
                }
            }
        )
    }
    if (showFolderDialog || renamingFolder != null) {
        FolderEditDialog(
            initial = renamingFolder,
            onDismiss = {
                showFolderDialog = false
                renamingFolder = null
            },
            onSave = { name ->
                scope.launch {
                    val target = renamingFolder
                    if (target == null) viewModel.addFolder(name) else viewModel.renameFolder(target.id, name)
                    showFolderDialog = false
                    renamingFolder = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.content_bookmarks_selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_bookmarks_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onMove) {
                Icon(Icons.Filled.Folder, contentDescription = stringResource(R.string.content_bookmarks_move))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.content_common_delete))
            }
        }
    )
}

@Composable
private fun FolderChipsRow(
    folders: List<BookmarkFolder>,
    selectedFolderId: Long?,
    onSelect: (Long?) -> Unit,
    onAddFolder: () -> Unit,
    onFolderLongPress: (BookmarkFolder) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FolderChip(
                name = stringResource(R.string.content_bookmarks_all),
                selected = selectedFolderId == null,
                onClick = { onSelect(null) },
                onLongClick = {}
            )
        }
        items(folders, key = { it.id }) { folder ->
            FolderChip(
                name = folder.name,
                selected = selectedFolderId == folder.id,
                onClick = { onSelect(folder.id) },
                onLongClick = { onFolderLongPress(folder) }
            )
        }
        item {
            FolderChip(
                name = stringResource(R.string.content_bookmarks_new_folder),
                selected = false,
                leadingIcon = {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = onAddFolder,
                onLongClick = {}
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class) // combinedClickable is still experimental in foundation 1.7.x
private fun FolderChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            leadingIcon?.invoke()
            if (leadingIcon != null) Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class) // combinedClickable is still experimental in foundation 1.7.x
private fun BookmarkRow(
    item: BookmarkWithFolder,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(item.bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(item.bookmark.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            FaviconImage(favicon = item.bookmark.favicon, title = item.bookmark.title)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.content_bookmarks_more_options)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.content_bookmarks_menu_edit)) },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.content_bookmarks_menu_move)) },
                        onClick = { showMenu = false; onMove() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.content_bookmarks_menu_delete)) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        },
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.combinedClickable(
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            onLongClick = onToggleSelect
        )
    )
}

/**
 * The site's favicon from the database, or a letter tile when there is none.
 * (A tile avoids depending on drawable resources owned by another worker.)
 */
@Composable
private fun FaviconImage(favicon: ByteArray?, title: String) {
    if (favicon != null) {
        AsyncImage(
            model = favicon,
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun BookmarkEditDialog(
    initial: BookmarkWithFolder?,
    folders: List<BookmarkFolder>,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, folderId: Long?) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.bookmark?.title ?: "") }
    var url by remember(initial) { mutableStateOf(initial?.bookmark?.url ?: "") }
    var folderId by remember(initial) { mutableStateOf(initial?.bookmark?.folderId) }
    var urlError by remember { mutableStateOf(false) }
    var folderMenuOpen by remember { mutableStateOf(false) }
    val folderName = folders.firstOrNull { it.id == folderId }?.name
        ?: stringResource(R.string.content_bookmarks_no_folder)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.content_bookmarks_add
                    else R.string.content_bookmarks_edit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.content_bookmarks_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; urlError = false },
                    label = { Text(stringResource(R.string.content_bookmarks_url_hint)) },
                    singleLine = true,
                    isError = urlError,
                    supportingText = {
                        if (urlError) Text(stringResource(R.string.content_bookmarks_url_error))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.content_bookmarks_folder), style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { folderMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(folderName)
                    }
                    DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.content_bookmarks_no_folder)) },
                            onClick = { folderId = null; folderMenuOpen = false }
                        )
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.name) },
                                onClick = { folderId = folder.id; folderMenuOpen = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isBlank()) {
                    urlError = true
                } else {
                    onSave(title, url, folderId)
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.content_common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.content_common_cancel))
            }
        }
    )
}

@Composable
private fun MoveToFolderDialog(
    folders: List<BookmarkFolder>,
    onDismiss: () -> Unit,
    onMove: (folderId: Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_bookmarks_move_to)) },
        text = {
            LazyColumn {
                item {
                    TextButton(
                        onClick = { onMove(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.content_bookmarks_no_folder))
                    }
                }
                items(folders, key = { it.id }) { folder ->
                    TextButton(
                        onClick = { onMove(folder.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(folder.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.content_common_cancel))
            }
        }
    )
}

@Composable
private fun FolderEditDialog(
    initial: BookmarkFolder?,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.content_bookmarks_new_folder
                    else R.string.content_bookmarks_rename_folder
                )
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text(stringResource(R.string.content_bookmarks_folder_name_hint)) },
                singleLine = true,
                isError = nameError,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) nameError = true
                else onSave(name)
            }) {
                Text(stringResource(R.string.content_common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.content_common_cancel))
            }
        }
    )
}
