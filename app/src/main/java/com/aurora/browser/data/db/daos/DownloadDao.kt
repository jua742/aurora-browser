package com.aurora.browser.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aurora.browser.data.db.entities.DownloadRecord
import kotlinx.coroutines.flow.Flow

/** Database access for download metadata (the files themselves live in system storage). */
@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: DownloadRecord): Long

    @Update
    suspend fun update(record: DownloadRecord)

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadRecord?

    @Query("SELECT * FROM downloads WHERE system_download_id = :systemDownloadId LIMIT 1")
    suspend fun getBySystemDownloadId(systemDownloadId: Long): DownloadRecord?

    @Query("SELECT * FROM downloads ORDER BY started_at DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    /** Rows still in flight (QUEUED/RUNNING) — what the progress poller iterates. */
    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY started_at DESC")
    suspend fun getByStatuses(statuses: List<String>): List<DownloadRecord>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Deletes the list rows only — never touches the user's actual files. */
    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
