package com.aurora.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.browser.AuroraApp
import com.aurora.browser.data.datastore.SettingsRepository
import com.aurora.browser.data.db.entities.SitePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the Site permissions screen. */
data class SitePermissionUi(val origin: String, val permission: String, val allowed: Boolean)

/**
 * State for all settings screens. Every setting is exposed as a StateFlow fed
 * by DataStore and persists immediately when its setter is called — there are
 * no "Save" buttons anywhere in settings.
 *
 * Clearing browsing data is a two-step handoff: this ViewModel emits the chosen
 * categories on [clearDataEvents]; the browser layer (Worker A's BrowserViewModel,
 * which owns the WebViews and CookieManager) collects that flow and performs
 * the actual wipe. Category ids are the CLEAR_* constants below.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as AuroraApp).container
    private val settings: SettingsRepository = container.settingsRepository
    private val sitePermissionDao = container.database.sitePermissionDao()

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val searchEngineId: StateFlow<String> = settings.searchEngineId.asState("google")
    val customSearchTemplate: StateFlow<String> = settings.customSearchTemplate.asState("")
    val homepageMode: StateFlow<String> = settings.homepageMode.asState("home")
    val homepageUrl: StateFlow<String> = settings.homepageUrl.asState("https://www.google.com")
    val themeMode: StateFlow<String> = settings.themeMode.asState("system")
    val textZoomPercent: StateFlow<Int> = settings.textZoomPercent.asState(100)
    val webDarkening: StateFlow<Boolean> = settings.webDarkening.asState(true)
    val javaScriptEnabled: StateFlow<Boolean> = settings.javaScriptEnabled.asState(true)
    val blockThirdPartyCookies: StateFlow<Boolean> = settings.blockThirdPartyCookies.asState(false)
    val desktopModeDefault: StateFlow<Boolean> = settings.desktopModeDefault.asState(false)
    val clearOnExit: StateFlow<Boolean> = settings.clearOnExit.asState(false)
    val clearOnExitCategories: StateFlow<Set<String>> =
        settings.clearOnExitCategories.asState(setOf(CLEAR_HISTORY, CLEAR_COOKIES, CLEAR_CACHE))
    val historyRetentionDays: StateFlow<Int> = settings.historyRetentionDays.asState(90)
    val restoreTabs: StateFlow<Boolean> = settings.restoreTabs.asState(true)

    fun setSearchEngineId(id: String) {
        viewModelScope.launch(Dispatchers.IO) { settings.setSearchEngineId(id) }
    }

    fun setCustomSearchTemplate(template: String) {
        viewModelScope.launch(Dispatchers.IO) { settings.setCustomSearchTemplate(template) }
    }

    fun setHomepageMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) { settings.setHomepageMode(mode) }
    }

    fun setHomepageUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) { settings.setHomepageUrl(url) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) { settings.setThemeMode(mode) }
    }

    fun setTextZoomPercent(percent: Int) {
        viewModelScope.launch(Dispatchers.IO) { settings.setTextZoomPercent(percent) }
    }

    fun setWebDarkening(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setWebDarkening(enabled) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setJavaScriptEnabled(enabled) }
    }

    fun setBlockThirdPartyCookies(block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setBlockThirdPartyCookies(block) }
    }

    fun setDesktopModeDefault(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setDesktopModeDefault(enabled) }
    }

    fun setClearOnExit(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setClearOnExit(enabled) }
    }

    fun setClearOnExitCategories(categories: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) { settings.setClearOnExitCategories(categories) }
    }

    fun setHistoryRetentionDays(days: Int) {
        viewModelScope.launch(Dispatchers.IO) { settings.setHistoryRetentionDays(days) }
    }

    fun setRestoreTabs(restore: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setRestoreTabs(restore) }
    }

    // ------------------------------------------------------- clear-data handoff

    private val _clearDataEvents = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

    /**
     * Emitted when the user confirms "Clear browsing data". The browser layer
     * collects this and wipes the matching stores:
     * - "history"     → historyRepository.clearAll()
     * - "cookies"     → CookieManager.removeAllCookies() + WebStorage.deleteAllData()
     * - "cache"       → WebView.clearCache(true)
     * - "downloads"   → downloadRepository.clearRecords() (list rows only, never files)
     * - "permissions" → sitePermissionDao.clearAll() + settings.clearSitePermissionChoices()
     */
    val clearDataEvents: SharedFlow<Set<String>> = _clearDataEvents.asSharedFlow()

    fun requestClearData(categories: Set<String>) {
        _clearDataEvents.tryEmit(categories.toSet())
    }

    // ------------------------------------------------------- site permissions

    /**
     * Union of the Room permission table and the DataStore mirror (the browser's
     * permission prompts read DataStore; this screen manages Room). Both are
     * written on every change so the two stores never silently diverge.
     */
    val sitePermissions: StateFlow<List<SitePermissionUi>> =
        combine(
            sitePermissionDao.observeAll(),
            settings.observeSitePermissionChoices()
        ) { rows, choices ->
            val merged = linkedMapOf<Pair<String, String>, Boolean>()
            rows.forEach { merged[it.origin to it.permission] = it.allowed }
            // DataStore is what live permission prompts read, so it wins on conflict.
            choices.forEach { merged[it.origin to it.permission] = it.allowed }
            merged
                .map { (key, allowed) -> SitePermissionUi(key.first, key.second, allowed) }
                .sortedWith(compareBy({ it.origin }, { it.permission }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSitePermission(origin: String, permission: String, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            sitePermissionDao.upsert(
                SitePermission(
                    origin = origin,
                    permission = permission,
                    allowed = allowed,
                    updatedAt = System.currentTimeMillis()
                )
            )
            settings.setSitePermissionChoice(origin, permission, allowed)
        }
    }

    fun deleteSitePermissionsForOrigin(origin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sitePermissionDao.deleteByOrigin(origin)
            settings.removeSitePermissionChoicesForOrigin(origin)
        }
    }

    fun clearAllSitePermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            sitePermissionDao.clearAll()
            settings.clearSitePermissionChoices()
        }
    }

    companion object {
        /** Category ids for clear-data and clear-on-exit. Shared with the browser layer. */
        const val CLEAR_HISTORY = "history"
        const val CLEAR_COOKIES = "cookies"
        const val CLEAR_CACHE = "cache"
        const val CLEAR_DOWNLOADS = "downloads"
        const val CLEAR_PERMISSIONS = "permissions"

        val ALL_CLEAR_CATEGORIES: Set<String> =
            setOf(CLEAR_HISTORY, CLEAR_COOKIES, CLEAR_CACHE, CLEAR_DOWNLOADS, CLEAR_PERMISSIONS)
    }
}
