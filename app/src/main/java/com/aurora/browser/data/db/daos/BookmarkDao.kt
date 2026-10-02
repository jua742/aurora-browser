package com.aurora.browser.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aurora.browser.data.db.entities.Bookmark
import com.aurora.browser.data.db.entities.BookmarkFolder
import kotlinx.coroutines.flow.Flow

/**
 * Database access for bookmarks and bookmark folders.
 *
 * Queries referencing user text use LIKE with an ESCAPE clause; callers must pass
 * the query through escapeLike() (see the repository) so %, _ and \ are matched
 * literally instead of acting as wildcards.
 */
@Dao
interface BookmarkDao {

    // ------------------------------------------------------------------ bookmarks

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bookmark: Bookmark): Long

    /** Re-insert used by undo-delete so the restored row keeps its original id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): Bookmark?

    @Query("SELECT * FROM bookmarks ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE folder_id = :folderId ORDER BY updated_at DESC")
    fun observeByFolder(folderId: Long): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE folder_id IS NULL ORDER BY updated_at DESC")
    fun observeWithoutFolder(): Flow<List<Bookmark>>

    @Query(
        "SELECT * FROM bookmarks " +
            "WHERE title LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR url LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY updated_at DESC"
    )
    fun search(query: String): Flow<List<Bookmark>>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE folder_id = :folderId")
    suspend fun countInFolder(folderId: Long): Int

    @Query("UPDATE bookmarks SET favicon = :favicon WHERE url = :url")
    suspend fun updateFavicon(url: String, favicon: ByteArray?)

    // ------------------------------------------------------------------ folders

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: BookmarkFolder): Long

    @Update
    suspend fun updateFolder(folder: BookmarkFolder)

    @Delete
    suspend fun deleteFolder(folder: BookmarkFolder)

    @Query("SELECT * FROM bookmark_folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: Long): BookmarkFolder?

    @Query("SELECT * FROM bookmark_folders ORDER BY name COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<BookmarkFolder>>

    @Query("SELECT * FROM bookmark_folders ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFolders(): List<BookmarkFolder>
}
