package com.aurora.browser

import android.app.Application
import android.content.Context
import com.aurora.browser.data.datastore.SettingsRepository
import com.aurora.browser.data.datastore.dataStore
import com.aurora.browser.data.db.AuroraDatabase
import com.aurora.browser.data.repository.BookmarkRepository
import com.aurora.browser.data.repository.DownloadRepository
import com.aurora.browser.data.repository.HistoryRepository
import com.aurora.browser.data.repository.TabRepository

/**
 * Application subclass: owns the [AppContainer] (manual dependency injection —
 * no Hilt in v1, per the spec's AndroidViewModel pattern).
 */
class AuroraApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual DI container (spec B-15). Lives as long as the process; every
 * repository is a lazy singleton sharing one Room database and one DataStore.
 */
class AppContainer(appContext: Context) {

    private val appCtx: Context = appContext.applicationContext

    // Worker B's documented single entry point: attaches the seed callback
    // (default bookmark folders) and the migration chain. Do not replace with
    // a raw Room.databaseBuilder call — the seeds would be silently dropped.
    val database: AuroraDatabase by lazy { AuroraDatabase.build(appCtx) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appCtx.dataStore)
    }

    val bookmarkRepository: BookmarkRepository by lazy {
        BookmarkRepository(database.bookmarkDao())
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(database.downloadDao())
    }

    val tabRepository: TabRepository by lazy {
        TabRepository(database.tabDao())
    }
}
