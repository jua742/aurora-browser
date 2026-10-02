package com.aurora.browser.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aurora.browser.data.db.entities.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Database access for browsing history.
 *
 * The single writer is [upsertVisit]: every recorded page view goes through it,
 * so the UNIQUE(url) constraint can never produce duplicate rows.
 */
@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: HistoryEntry): Long

    @Update
    suspend fun update(entry: HistoryEntry)

    /** Re-insert used by undo-delete so the restored row keeps its original id and counts. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(entry: HistoryEntry)

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): HistoryEntry?

    /**
     * Records one page visit: first visit inserts a row, repeat visits bump
     * visitCount and lastVisited. Runs in one transaction so the read and the
     * write cannot interleave with another visit to the same URL.
     */
    @Transaction
    suspend fun upsertVisit(url: String, title: String, now: Long) {
        val existing = getByUrl(url)
        if (existing == null) {
            insert(
                HistoryEntry(
                    url = url,
                    title = title,
                    visitCount = 1,
                    firstVisited = now,
                    lastVisited = now
                )
            )
        } else {
            update(
                existing.copy(
                    // Keep the old title when the page reported nothing useful.
                    title = title.ifBlank { existing.title },
                    visitCount = existing.visitCount + 1,
                    lastVisited = now
                )
            )
        }
    }

    /** Most recent first, capped — the main history list. */
    @Query("SELECT * FROM history ORDER BY last_visited DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntry>>

    /** Most visited first — feeds the home screen's "Top sites" tiles. */
    @Query("SELECT * FROM history ORDER BY visit_count DESC, last_visited DESC LIMIT :limit")
    fun observeTopSites(limit: Int): Flow<List<HistoryEntry>>

    /**
     * Address-bar suggestions: title or URL *starting with* the typed prefix,
     * most recent first. Local-only by design (no keystrokes leave the device).
     */
    @Query(
        "SELECT * FROM history " +
            "WHERE title LIKE :prefix || '%' ESCAPE '\\' " +
            "OR url LIKE :prefix || '%' ESCAPE '\\' " +
            "ORDER BY last_visited DESC LIMIT :limit"
    )
    fun observeSuggestions(prefix: String, limit: Int): Flow<List<HistoryEntry>>

    @Query(
        "SELECT * FROM history " +
            "WHERE title LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR url LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY last_visited DESC LIMIT 200"
    )
    fun search(query: String): Flow<List<HistoryEntry>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    /** Removes entries last visited before [cutoffMillis] (the 90-day auto-trim). */
    @Query("DELETE FROM history WHERE last_visited < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
