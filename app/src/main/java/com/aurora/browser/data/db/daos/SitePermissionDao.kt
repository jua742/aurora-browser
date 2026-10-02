package com.aurora.browser.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.browser.data.db.entities.SitePermission
import kotlinx.coroutines.flow.Flow

/** Database access for remembered per-site permission choices. */
@Dao
interface SitePermissionDao {

    @Query("SELECT * FROM site_permissions WHERE origin = :origin AND permission = :permission LIMIT 1")
    suspend fun get(origin: String, permission: String): SitePermission?

    /**
     * Insert-or-replace: answering the same origin+permission twice updates the
     * stored choice instead of adding a second row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(permission: SitePermission)

    @Query("SELECT * FROM site_permissions ORDER BY origin ASC, permission ASC")
    fun observeAll(): Flow<List<SitePermission>>

    @Query("DELETE FROM site_permissions WHERE origin = :origin")
    suspend fun deleteByOrigin(origin: String)

    @Query("DELETE FROM site_permissions")
    suspend fun clearAll()
}
