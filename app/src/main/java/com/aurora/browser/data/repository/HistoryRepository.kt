package com.aurora.browser.data.repository

import com.aurora.browser.data.db.daos.HistoryDao
import com.aurora.browser.data.db.entities.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * History business logic: visit recording with an http/https-only filter,
 * searching, suggestions, and retention trimming.
 */
class HistoryRepository(private val historyDao: HistoryDao) {

    fun observeHistory(limit: Int = 200): Flow<List<HistoryEntry>> =
        historyDao.observeRecent(limit)

    fun searchHistory(query: String): Flow<List<HistoryEntry>> =
        historyDao.search(query.escapeLike())

    fun observeTopSites(limit: Int = 8): Flow<List<HistoryEntry>> =
        historyDao.observeTopSites(limit)

    /**
     * Address-bar suggestions: title/URL starting with [prefix], most recent
     * first. Fed only by local history (plus bookmarks at the UI layer) — no
     * keystrokes are sent to any server.
     */
    fun observeSuggestions(prefix: String, limit: Int = 6): Flow<List<HistoryEntry>> =
        historyDao.observeSuggestions(prefix.escapeLike(), limit)

    /**
     * Records one page visit (upsert by URL: visitCount + 1, lastVisited = now).
     * Only http/https pages are stored — blank URLs and non-web schemes
     * (about:, browser-error:, data:, ...) are skipped. Incognito visits are
     * never recorded because the browser layer simply does not call this for
     * private tabs.
     */
    suspend fun recordVisit(url: String, title: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val scheme = cleanUrl.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") return
        historyDao.upsertVisit(cleanUrl, title.trim().take(MAX_TITLE_LENGTH), System.currentTimeMillis())
    }

    suspend fun deleteEntry(id: Long) = historyDao.deleteById(id)

    /**
     * Re-inserts a deleted row (the Undo path), preserving its original visit
     * counts and timestamps.
     * Extra beyond the phase contract — needed by the UI's undo feature.
     */
    suspend fun restoreEntry(entry: HistoryEntry) = historyDao.insertReplace(entry)

    suspend fun clearAll() = historyDao.clearAll()

    /**
     * Deletes entries last visited more than [days] days ago (the auto-trim that
     * runs on app start). Pass 0 or less to skip trimming.
     */
    suspend fun trimOlderThan(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
        historyDao.deleteOlderThan(cutoff)
    }

    companion object {
        /** Spec B-13: history titles are capped at 500 chars. */
        const val MAX_TITLE_LENGTH = 500
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
