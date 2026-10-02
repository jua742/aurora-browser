package com.aurora.browser.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persistable snapshot of one open tab, used to restore the session after the
 * app process is killed.
 *
 * Only the URL is restored (the page is reloaded); WebView state such as scroll
 * position is intentionally not persisted. Incognito tabs are NEVER written here.
 */
@Entity(tableName = "tabs")
data class TabRecord(
    /** Matches the in-memory tab's id (a UUID string). */
    @PrimaryKey
    @ColumnInfo(name = "tab_id")
    val tabId: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String,

    /** Order in the tab switcher, 0-based. */
    @ColumnInfo(name = "position")
    val position: Int,

    /** Epoch millis when this tab was last the active one. */
    @ColumnInfo(name = "last_active")
    val lastActive: Long
)
