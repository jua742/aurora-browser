package com.aurora.browser.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aurora.browser.data.db.entities.TabRecord

/**
 * Database access for the restorable tab list.
 *
 * Tabs are saved with replace-all semantics: the table always mirrors exactly
 * the set of open non-incognito tabs, so a stale entry can never resurrect a
 * tab the user already closed.
 */
@Dao
interface TabDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<TabRecord>)

    @Query("SELECT * FROM tabs ORDER BY position ASC")
    suspend fun getAllOrdered(): List<TabRecord>

    @Query("DELETE FROM tabs")
    suspend fun clearAll()

    /** Writes the whole session in one transaction. */
    @Transaction
    suspend fun replaceAll(tabs: List<TabRecord>) {
        clearAll()
        if (tabs.isNotEmpty()) {
            insertAll(tabs)
        }
    }
}
