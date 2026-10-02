package com.aurora.browser.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One visited page.
 *
 * - `url` is UNIQUE: repeat visits update the existing row (visitCount + 1,
 *   lastVisited = now) instead of adding a new row. The DAO's upsertVisit does this.
 * - Only http/https pages are ever stored; the repository rejects other schemes,
 *   and the browser layer never records visits from incognito tabs.
 */
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["last_visited"])
    ]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String,

    /** How many times this URL has been visited. */
    @ColumnInfo(name = "visit_count")
    val visitCount: Int,

    /** Epoch millis of the first recorded visit. */
    @ColumnInfo(name = "first_visited")
    val firstVisited: Long,

    /** Epoch millis of the most recent visit. */
    @ColumnInfo(name = "last_visited")
    val lastVisited: Long
)
