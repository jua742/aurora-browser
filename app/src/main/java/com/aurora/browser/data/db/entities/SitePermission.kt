package com.aurora.browser.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A remembered per-site permission choice, e.g. "allow location for https://example.com".
 *
 * - `origin` is the site's origin as reported by the WebView ("https://example.com").
 * - `permission` is one of PERMISSION_CAMERA, PERMISSION_MIC, PERMISSION_LOCATION.
 * - The (origin, permission) pair is UNIQUE: re-answering replaces the old choice.
 *
 * Note: the same choices are mirrored in DataStore (SettingsRepository) so the
 * browser's permission prompts can read them without a database query. The
 * SettingsViewModel keeps both stores in sync.
 */
@Entity(
    tableName = "site_permissions",
    indices = [Index(value = ["origin", "permission"], unique = true)]
)
data class SitePermission(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "origin")
    val origin: String,

    /** One of PERMISSION_CAMERA, PERMISSION_MIC, PERMISSION_LOCATION. */
    @ColumnInfo(name = "permission")
    val permission: String,

    @ColumnInfo(name = "allowed")
    val allowed: Boolean,

    /** Epoch millis when the choice was last changed. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val PERMISSION_CAMERA = "CAMERA"
        const val PERMISSION_MIC = "MIC"
        const val PERMISSION_LOCATION = "LOCATION"
    }
}
