package com.aurora.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.browser.AuroraApp
import com.aurora.browser.data.db.entities.Bookmark
import com.aurora.browser.data.repository.BookmarkRepository
import com.aurora.browser.data.repository.BookmarkWithFolder
import com.aurora.browser.data.db.entities.BookmarkFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the Bookmarks screen: searching, folder filtering, add/edit/delete/
 * move, folder management, and multi-select with undoable deletes.
 *
 * The container (repositories, database) is owned by Worker A's AuroraApp; this
 * ViewModel only pulls what it needs out of it.
 */
class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as AuroraApp).container
    private val repository: BookmarkRepository = container.bookmarkRepository

    /** Raw text in the search field; the list query below debounces it. */
    val searchQuery = MutableStateFlow("")

    /** Currently selected folder chip; null = "All". */
    val selectedFolderId = MutableStateFlow<Long?>(null)

    /** Ids selected via long-press, for bulk delete/move. */
    val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val isSelectionMode: StateFlow<Boolean> = selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val folders: StateFlow<List<BookmarkFolder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val baseBookmarks: StateFlow<List<BookmarkWithFolder>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeBookmarks()
            else repository.searchBookmarks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The list the UI actually renders: search results filtered by the folder chip. */
    val visibleBookmarks: StateFlow<List<BookmarkWithFolder>> =
        combine(baseBookmarks, selectedFolderId) { list, folderId ->
            if (folderId == null) list else list.filter { it.bookmark.folderId == folderId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Unfiltered bookmark list. Used to resolve the multi-selection to items
     * even when a search query or folder chip is hiding some of them.
     */
    val allBookmarks: StateFlow<List<BookmarkWithFolder>> = repository.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The currently selected bookmarks as full items (for delete/move). */
    fun selectedItems(): List<BookmarkWithFolder> =
        allBookmarks.value.filter { it.bookmark.id in selectedIds.value }

    /** Bookmarks removed by the last delete, kept for the Undo snackbar action. */
    private var lastDeleted: List<Bookmark> = emptyList()

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFolder(folderId: Long?) {
        selectedFolderId.value = folderId
    }

    fun toggleSelection(id: Long) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun addBookmark(title: String, url: String, folderId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addOrUpdateBookmark(title, url, folderId)
        }
    }

    fun updateBookmark(id: Long, title: String, url: String, folderId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBookmark(id, title, url, folderId)
        }
    }

    /**
     * Deletes bookmarks and remembers them so the UI can offer Undo.
     * Call [restoreDeleted] within a few seconds to bring them back.
     */
    fun deleteBookmarks(items: List<BookmarkWithFolder>) {
        if (items.isEmpty()) return
        lastDeleted = items.map { it.bookmark }
        clearSelection()
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { repository.deleteBookmark(it.bookmark.id) }
        }
    }

    fun restoreDeleted() {
        val toRestore = lastDeleted
        if (toRestore.isEmpty()) return
        lastDeleted = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreBookmarks(toRestore)
        }
    }

    fun moveBookmarks(ids: Set<Long>, folderId: Long?) {
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.moveToFolder(id, folderId) }
        }
    }

    suspend fun addFolder(name: String): Long = withContext(Dispatchers.IO) {
        repository.addFolder(name)
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameFolder(id, name)
        }
    }

    /** Calls [onResult] on the main thread with false when the folder was kept. */
    fun deleteFolder(id: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.deleteFolderIfEmpty(id) }
            if (deleted && selectedFolderId.value == id) {
                selectedFolderId.value = null
            }
            onResult(deleted)
        }
    }
}
