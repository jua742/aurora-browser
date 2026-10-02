package com.aurora.browser.data.repository

import com.aurora.browser.data.db.daos.DownloadDao
import com.aurora.browser.data.db.entities.DownloadRecord
import kotlinx.coroutines.flow.Flow

/**
 * Download metadata business logic. The files themselves are owned by Android's
 * DownloadManager; this repository only tracks the rows shown in the Downloads
 * list. Clearing rows here never deletes the user's files.
 */
class DownloadRepository(private val downloadDao: DownloadDao) {

    fun observeDownloads(): Flow<List<DownloadRecord>> = downloadDao.observeAll()

    suspend fun insert(record: DownloadRecord): Long = downloadDao.insert(record)

    suspend fun updateProgress(id: Long, downloadedBytes: Long, totalBytes: Long, status: String) {
        val current = downloadDao.getById(id) ?: return
        downloadDao.update(
            current.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                status = status
            )
        )
    }

    /**
     * Progress update keyed by the DownloadManager system id (no status change).
     * Used by the download progress poller, which only knows system ids.
     * Extra beyond the phase contract — overload, so the contract signature is untouched.
     */
    suspend fun updateProgress(systemDownloadId: Long, downloadedBytes: Long, totalBytes: Long) {
        val current = downloadDao.getBySystemDownloadId(systemDownloadId) ?: return
        downloadDao.update(
            current.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
        )
    }

    /**
     * Rows still in flight (QUEUED or RUNNING), newest first.
     * Extra beyond the phase contract — used by the download progress poller.
     */
    suspend fun getActiveDownloads(): List<DownloadRecord> =
        downloadDao.getByStatuses(
            listOf(DownloadRecord.STATUS_QUEUED, DownloadRecord.STATUS_RUNNING)
        )

    /**
     * Marks a download complete. Accepts either the Room row id or the
     * DownloadManager system id: the row-id lookup runs first, then the
     * system-id lookup. (The progress poller only knows system ids; UI code
     * uses row ids. Both must work through this one signature.)
     */
    suspend fun markComplete(id: Long) {
        val current = downloadDao.getById(id)
            ?: downloadDao.getBySystemDownloadId(id)
            ?: return
        downloadDao.update(
            current.copy(
                status = DownloadRecord.STATUS_COMPLETE,
                downloadedBytes = current.totalBytes,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    /** Same dual-id lookup as [markComplete]. */
    suspend fun markFailed(id: Long) {
        val current = downloadDao.getById(id)
            ?: downloadDao.getBySystemDownloadId(id)
            ?: return
        downloadDao.update(
            current.copy(
                status = DownloadRecord.STATUS_FAILED,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getById(id: Long): DownloadRecord? = downloadDao.getById(id)

    /** Removes the list row only. Removing the actual file is DownloadManager.remove(). */
    suspend fun deleteById(id: Long) = downloadDao.deleteById(id)

    /** Clears the whole list. The user's downloaded files are never touched. */
    suspend fun clearRecords() = downloadDao.clearAll()

    /**
     * Alias for [clearRecords] — the downloads feature (Worker C) uses this name.
     * Extra beyond the phase contract.
     */
    suspend fun deleteAll() = clearRecords()
}
