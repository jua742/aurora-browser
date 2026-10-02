package com.aurora.browser.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved bookmark.
 *
 * - `url` is UNIQUE: adding the same URL twice updates the existing row instead
 *   of creating a duplicate (the repository enforces this).
 * - `folderId` points at [BookmarkFolder]. When a folder is deleted, its
 *   bookmarks survive with no folder (onDelete = SET_NULL).
 * - `favicon` holds the site's icon as raw image bytes, capped at 32 KB on write.
 */
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["folder_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookmarkFolder::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "url")
    val url: String,

    /** Null means "no folder". Never points at a deleted folder thanks to SET_NULL. */
    @ColumnInfo(name = "folder_id")
    val folderId: Long?,

    /** Raw PNG bytes, or null when unknown. The repository drops images over 32 KB. */
    @ColumnInfo(name = "favicon", typeAffinity = ColumnInfo.BLOB)
    val favicon: ByteArray?,

    /** Epoch millis when the bookmark was created. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Epoch millis of the last edit. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    // A data class compares ByteArray by reference; compare the icon bytes by content instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bookmark) return false
        return id == other.id &&
            title == other.title &&
            url == other.url &&
            folderId == other.folderId &&
            (favicon contentEquals other.favicon) &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + (favicon?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
