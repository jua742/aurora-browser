package com.aurora.browser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurora.browser.data.db.daos.BookmarkDao
import com.aurora.browser.data.db.daos.DownloadDao
import com.aurora.browser.data.db.daos.HistoryDao
import com.aurora.browser.data.db.daos.SitePermissionDao
import com.aurora.browser.data.db.daos.TabDao
import com.aurora.browser.data.db.entities.Bookmark
import com.aurora.browser.data.db.entities.BookmarkFolder
import com.aurora.browser.data.db.entities.DEFAULT_FOLDER_BOOKMARKS_BAR
import com.aurora.browser.data.db.entities.DEFAULT_FOLDER_OTHER
import com.aurora.browser.data.db.entities.DownloadRecord
import com.aurora.browser.data.db.entities.HistoryEntry
import com.aurora.browser.data.db.entities.SitePermission
import com.aurora.browser.data.db.entities.TabRecord

/**
 * The app's Room database: bookmarks, folders, history, downloads metadata,
 * restorable tabs, and per-site permission choices.
 *
 * Versioning rules (spec: destructive migration is FORBIDDEN — it would wipe
 * user data):
 * - v1 ships at version = 1.
 * - For v2: bump `version`, write a real `Migration(1, 2)` object, and pass it
 *   to the builder below via `.addMigrations(...)`. Never add
 *   `fallbackToDestructiveMigration()`.
 */
@Database(
    entities = [
        Bookmark::class,
        BookmarkFolder::class,
        HistoryEntry::class,
        DownloadRecord::class,
        TabRecord::class,
        SitePermission::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuroraDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun tabDao(): TabDao
    abstract fun sitePermissionDao(): SitePermissionDao

    companion object {

        /**
         * Seeds the two default bookmark folders ("Bookmarks bar", "Other") on a
         * fresh install. Uses raw SQL because the database instance (and its DAOs)
         * does not exist yet inside onCreate.
         */
        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO bookmark_folders (name, parent_id, created_at) VALUES (?, NULL, ?)",
                    arrayOf<Any?>(DEFAULT_FOLDER_BOOKMARKS_BAR, now)
                )
                db.execSQL(
                    "INSERT INTO bookmark_folders (name, parent_id, created_at) VALUES (?, NULL, ?)",
                    arrayOf<Any?>(DEFAULT_FOLDER_OTHER, now)
                )
            }
        }

        /**
         * The single supported way to build the database. Always goes through
         * here so the seed callback (and any future migrations) cannot be
         * forgotten by a call site.
         */
        fun build(context: Context): AuroraDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AuroraDatabase::class.java,
                "aurora_browser.db"
            )
                .addCallback(seedCallback)
                // NOTE: never add fallbackToDestructiveMigration() here.
                .build()
    }
}
