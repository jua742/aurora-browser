package com.aurora.browser.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Name of the first folder seeded on a fresh install. */
const val DEFAULT_FOLDER_BOOKMARKS_BAR = "Bookmarks bar"

/** Name of the second folder seeded on a fresh install. */
const val DEFAULT_FOLDER_OTHER = "Other"

/**
 * A folder that groups bookmarks.
 *
 * - `parentId` is a self-reference so folders could nest one day; v1 only uses
 *   top-level folders (parentId is always null). Deleting a parent deletes its
 *   children (onDelete = CASCADE).
 * - The two default folders are seeded by [com.aurora.browser.data.db.AuroraDatabase]
 *   on first launch and cannot be deleted (only renamed).
 */
@Entity(
    tableName = "bookmark_folders",
    indices = [Index(value = ["parent_id"])],
    foreignKeys = [
        ForeignKey(
            entity = BookmarkFolder::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookmarkFolder(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    /** Null for top-level folders (all folders in v1). */
    @ColumnInfo(name = "parent_id")
    val parentId: Long?,

    /** Epoch millis when the folder was created. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
