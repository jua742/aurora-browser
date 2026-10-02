package com.aurora.browser.data.repository

import com.aurora.browser.data.db.daos.BookmarkDao
import com.aurora.browser.data.db.entities.Bookmark
import com.aurora.browser.data.db.entities.BookmarkFolder
import com.aurora.browser.data.db.entities.DEFAULT_FOLDER_BOOKMARKS_BAR
import com.aurora.browser.data.db.entities.DEFAULT_FOLDER_OTHER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A bookmark together with the display name of its folder (null when it has none). */
data class BookmarkWithFolder(val bookmark: Bookmark, val folderName: String?)

/**
 * Escapes SQL LIKE wildcards so user-typed search text is matched literally.
 * Shared with [HistoryRepository] (same module, internal visibility).
 */
internal fun String.escapeLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * Bookmark business logic: duplicate-URL guard, field-length caps, folder rules.
 * All functions are safe to call from any thread; ViewModels dispatch them to IO.
 */
class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    /** All bookmarks, newest first, each paired with its folder's display name. */
    fun observeBookmarks(): Flow<List<BookmarkWithFolder>> =
        bookmarkDao.observeAll().combine(bookmarkDao.observeFolders()) { bookmarks, folders ->
            val namesById = folders.associate { it.id to it.name }
            bookmarks.map { bookmark ->
                BookmarkWithFolder(bookmark, bookmark.folderId?.let(namesById::get))
            }
        }

    fun observeFolders(): Flow<List<BookmarkFolder>> = bookmarkDao.observeFolders()

    fun searchBookmarks(query: String): Flow<List<BookmarkWithFolder>> =
        bookmarkDao.search(query.escapeLike()).combine(bookmarkDao.observeFolders()) { bookmarks, folders ->
            val namesById = folders.associate { it.id to it.name }
            bookmarks.map { bookmark ->
                BookmarkWithFolder(bookmark, bookmark.folderId?.let(namesById::get))
            }
        }

    suspend fun isBookmarked(url: String): Boolean = bookmarkDao.getByUrl(url) != null

    /**
     * Row id of the bookmark with this URL, or null. Used by the browser
     * toolbar star so it can un-bookmark rows created anywhere (browser,
     * bookmarks screen, ...), not just rows it created itself.
     */
    suspend fun getBookmarkIdByUrl(url: String): Long? = bookmarkDao.getByUrl(url)?.id

    /**
     * Adds a bookmark, or — when the URL already exists (duplicate-URL guard) —
     * updates the existing row's title/folder and returns its id.
     */
    suspend fun addOrUpdateBookmark(title: String, url: String, folderId: Long?): Long {
        val cleanTitle = title.trim().take(MAX_TITLE_LENGTH)
        val cleanUrl = url.trim().take(MAX_URL_LENGTH)
        require(cleanUrl.isNotBlank()) { "Bookmark URL must not be blank" }
        val now = System.currentTimeMillis()
        val existing = bookmarkDao.getByUrl(cleanUrl)
        return if (existing == null) {
            bookmarkDao.insert(
                Bookmark(
                    title = cleanTitle.ifBlank { cleanUrl },
                    url = cleanUrl,
                    folderId = folderId,
                    favicon = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            bookmarkDao.update(
                existing.copy(
                    title = cleanTitle.ifBlank { existing.title },
                    folderId = folderId,
                    updatedAt = now
                )
            )
            existing.id
        }
    }

    suspend fun updateBookmark(id: Long, title: String, url: String, folderId: Long?) {
        val cleanTitle = title.trim().take(MAX_TITLE_LENGTH)
        val cleanUrl = url.trim().take(MAX_URL_LENGTH)
        require(cleanUrl.isNotBlank()) { "Bookmark URL must not be blank" }
        val existing = bookmarkDao.getById(id) ?: return
        bookmarkDao.update(
            existing.copy(
                title = cleanTitle.ifBlank { existing.title },
                url = cleanUrl,
                folderId = folderId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)

    /**
     * Moves a bookmark to another folder (null = no folder) without touching
     * its title or URL.
     * Extra beyond the phase contract — used by the multi-select move action.
     */
    suspend fun moveToFolder(id: Long, folderId: Long?) {
        val existing = bookmarkDao.getById(id) ?: return
        bookmarkDao.update(existing.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Re-inserts rows removed by delete (the Undo path). Ids are preserved so a
     * restored bookmark lands exactly where it was.
     * Extra beyond the phase contract — needed by the UI's undo feature.
     */
    suspend fun restoreBookmarks(bookmarks: List<Bookmark>) {
        bookmarks.forEach { bookmarkDao.insertReplace(it) }
    }

    /**
     * Stores a favicon for the bookmark with this URL. Images over 32 KB are
     * discarded (the spec cap); truncating a PNG would corrupt it, so oversized
     * icons become "no icon" instead of a broken one.
     * Extra beyond the phase contract — called by the browser layer when a page
     * favicon finishes loading.
     */
    suspend fun updateFavicon(url: String, favicon: ByteArray?) {
        val capped = if (favicon != null && favicon.size > MAX_FAVICON_BYTES) null else favicon
        bookmarkDao.updateFavicon(url, capped)
    }

    suspend fun addFolder(name: String): Long {
        val clean = name.trim().take(MAX_FOLDER_NAME_LENGTH)
        require(clean.isNotEmpty()) { "Folder name must not be blank" }
        return bookmarkDao.insertFolder(
            BookmarkFolder(name = clean, parentId = null, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun renameFolder(id: Long, name: String) {
        val clean = name.trim().take(MAX_FOLDER_NAME_LENGTH)
        require(clean.isNotEmpty()) { "Folder name must not be blank" }
        val folder = bookmarkDao.getFolderById(id) ?: return
        bookmarkDao.updateFolder(folder.copy(name = clean))
    }

    /**
     * Deletes a folder only when it holds no bookmarks and is not one of the two
     * seeded default folders. Returns false when the folder was kept, so the UI
     * can explain why.
     */
    suspend fun deleteFolderIfEmpty(id: Long): Boolean {
        val folder = bookmarkDao.getFolderById(id) ?: return false
        if (folder.name == DEFAULT_FOLDER_BOOKMARKS_BAR || folder.name == DEFAULT_FOLDER_OTHER) {
            return false
        }
        if (bookmarkDao.countInFolder(id) > 0) {
            return false
        }
        bookmarkDao.deleteFolder(folder)
        return true
    }

    companion object {
        /** Spec B-13: bookmark/history writes cap the title at 500 chars. */
        const val MAX_TITLE_LENGTH = 500

        /** Spec B-13: URLs are capped at 2048 chars. */
        const val MAX_URL_LENGTH = 2048

        const val MAX_FOLDER_NAME_LENGTH = 100

        /** Spec B-11: favicon BLOBs are capped at 32 KB each. */
        const val MAX_FAVICON_BYTES = 32 * 1024
    }
}
