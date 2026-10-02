package com.aurora.browser.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata for one download. The actual file lives in the system's public
 * Downloads folder and is managed by Android's DownloadManager; this row only
 * tracks what the in-app Downloads list shows.
 *
 * `status` is one of the STATUS_* constants below.
 */
@Entity(
    tableName = "downloads",
    indices = [Index(value = ["started_at"])]
)
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** The id returned by DownloadManager.enqueue(); links this row to the system download. */
    @ColumnInfo(name = "system_download_id")
    val systemDownloadId: Long,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String?,

    /** Total size in bytes; -1 when the server did not report a size. */
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,

    /** Bytes downloaded so far. */
    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long,

    /** One of STATUS_QUEUED, STATUS_RUNNING, STATUS_COMPLETE, STATUS_FAILED. */
    @ColumnInfo(name = "status")
    val status: String,

    /** Epoch millis when the download was enqueued. */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /** Epoch millis when it finished or failed; null while in progress. */
    @ColumnInfo(name = "completed_at")
    val completedAt: Long?
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMPLETE = "COMPLETE"
        const val STATUS_FAILED = "FAILED"
    }
}
