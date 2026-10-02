package com.aurora.browser.downloads

import android.app.DownloadManager
import android.content.Context
import com.aurora.browser.data.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls the system DownloadManager about once per second and mirrors progress
 * into Room so the Downloads screen stays live.
 *
 * Polling only runs while [start] has been called and [stop] hasn't - the
 * Downloads screen starts it in a DisposableEffect, so no background work
 * happens when the screen isn't visible (spec B-18).
 *
 * Cross-worker assumptions (Worker B owns the repository):
 * - `suspend fun getActiveDownloads(): List<DownloadRecord>` - rows whose
 *   status is RUNNING or QUEUED.
 * - `suspend fun updateProgress(systemDownloadId: Long, downloadedBytes: Long, totalBytes: Long)`
 * - `suspend fun markComplete(systemDownloadId: Long)` - sets COMPLETE + completedAt.
 * - `suspend fun markFailed(systemDownloadId: Long)` - sets FAILED + completedAt.
 *   The DownloadManager COLUMN_REASON is intentionally stored nowhere; the
 *   Downloads screen maps the live reason code to text when rendering a
 *   failed row.
 */
class DownloadTracker(
    context: Context,
    private val downloadRepository: DownloadRepository
) {
    // Never hold an Activity here: the tracker can outlive the screen.
    private val appContext = context.applicationContext
    private var job: Job? = null

    /**
     * Starts polling on the CALLER-provided [scope] (DownloadsViewModel passes
     * viewModelScope). Idempotent - calling it twice doesn't start two loops.
     */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pollOnce()
                } catch (e: CancellationException) {
                    throw e // never swallow cancellation
                } catch (e: Exception) {
                    // A transient failure must not kill the loop; the next tick retries.
                }
                delay(1000)
            }
        }
    }

    /** Stops polling. Safe to call when not started. */
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun pollOnce() {
        val dm = appContext.getSystemService(DownloadManager::class.java) ?: return
        val active = downloadRepository.getActiveDownloads()
        if (active.isEmpty()) return

        val ids = active.map { it.systemDownloadId }.toLongArray()
        val seen = HashSet<Long>(ids.size)
        dm.query(DownloadManager.Query().setFilterById(*ids)).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val downloadedCol =
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (cursor.moveToNext()) {
                val systemId = cursor.getLong(idCol)
                seen.add(systemId)
                when (cursor.getInt(statusCol)) {
                    DownloadManager.STATUS_SUCCESSFUL -> downloadRepository.markComplete(systemId)
                    DownloadManager.STATUS_FAILED -> downloadRepository.markFailed(systemId)
                    // STATUS_PENDING / STATUS_RUNNING / STATUS_PAUSED: keep the row
                    // alive with fresh byte counts.
                    else -> downloadRepository.updateProgress(
                        systemDownloadId = systemId,
                        downloadedBytes = cursor.getLong(downloadedCol),
                        totalBytes = cursor.getLong(totalCol)
                    )
                }
            }
        }
        // A system id with no DownloadManager row anymore (e.g. the user cleared it
        // from the system UI) would otherwise poll forever - mark it failed so the
        // list stays honest instead of spinning.
        for (record in active) {
            if (record.systemDownloadId !in seen) {
                downloadRepository.markFailed(record.systemDownloadId)
            }
        }
    }
}
