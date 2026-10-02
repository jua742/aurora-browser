package com.aurora.browser.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * One remembered per-origin permission choice, e.g. example.com + LOCATION + allowed.
 * Mirrors the Room [com.aurora.browser.data.db.entities.SitePermission] table so the
 * browser's permission prompts can read choices without a database query.
 */
data class SitePermissionChoice(val origin: String, val permission: String, val allowed: Boolean)

/**
 * A custom search-engine URL template is usable only when it is an https URL
 * containing a %s placeholder for the encoded query. Used by the settings UI
 * (shows an error) and by the browser layer (falls back to the default engine).
 */
fun isValidSearchTemplate(template: String): Boolean {
    val clean = template.trim()
    return clean.startsWith("https://") && clean.contains("%s") && !clean.contains(' ')
}

/**
 * All user settings, persisted with DataStore Preferences.
 *
 * - Every setting is a Flow: the UI collects it and always shows the current value.
 * - Writes go through suspend setters; DataStore is safe to call from any thread.
 * - Key strings follow spec B-10 exactly. Never rename a key — that would silently
 *   reset the setting for existing users.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SEARCH_ENGINE_ID = stringPreferencesKey("search_engine_id")
        val CUSTOM_SEARCH_TEMPLATE = stringPreferencesKey("custom_search_template")
        val HOMEPAGE_MODE = stringPreferencesKey("homepage_mode")
        val HOMEPAGE_URL = stringPreferencesKey("homepage_url")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEXT_ZOOM_PERCENT = intPreferencesKey("text_zoom_percent")
        val WEB_DARKENING = booleanPreferencesKey("web_darkening")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")
        val DESKTOP_MODE_DEFAULT = booleanPreferencesKey("desktop_mode_default")
        val CLEAR_ON_EXIT = booleanPreferencesKey("clear_on_exit")
        val CLEAR_ON_EXIT_CATEGORIES = stringSetPreferencesKey("clear_on_exit_categories")
        val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val RESTORE_TABS = booleanPreferencesKey("restore_tabs")
        val SITE_PERMISSION_CHOICES = stringSetPreferencesKey("site_permission_choices")
    }

    // If the prefs file is ever unreadable, emit empty prefs (→ defaults) instead of crashing.
    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val searchEngineId: Flow<String> =
        prefs.map { it[Keys.SEARCH_ENGINE_ID] ?: DEFAULT_SEARCH_ENGINE_ID }

    val customSearchTemplate: Flow<String> =
        prefs.map { it[Keys.CUSTOM_SEARCH_TEMPLATE] ?: "" }

    val homepageMode: Flow<String> =
        prefs.map { it[Keys.HOMEPAGE_MODE] ?: DEFAULT_HOMEPAGE_MODE }

    val homepageUrl: Flow<String> =
        prefs.map { it[Keys.HOMEPAGE_URL] ?: DEFAULT_HOMEPAGE_URL }

    val themeMode: Flow<String> =
        prefs.map { it[Keys.THEME_MODE] ?: DEFAULT_THEME_MODE }

    val textZoomPercent: Flow<Int> =
        prefs.map { it[Keys.TEXT_ZOOM_PERCENT] ?: DEFAULT_TEXT_ZOOM_PERCENT }

    val webDarkening: Flow<Boolean> =
        prefs.map { it[Keys.WEB_DARKENING] ?: DEFAULT_WEB_DARKENING }

    val javaScriptEnabled: Flow<Boolean> =
        prefs.map { it[Keys.JAVASCRIPT_ENABLED] ?: DEFAULT_JAVASCRIPT_ENABLED }

    val blockThirdPartyCookies: Flow<Boolean> =
        prefs.map { it[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: DEFAULT_BLOCK_THIRD_PARTY_COOKIES }

    val desktopModeDefault: Flow<Boolean> =
        prefs.map { it[Keys.DESKTOP_MODE_DEFAULT] ?: DEFAULT_DESKTOP_MODE_DEFAULT }

    val clearOnExit: Flow<Boolean> =
        prefs.map { it[Keys.CLEAR_ON_EXIT] ?: DEFAULT_CLEAR_ON_EXIT }

    val clearOnExitCategories: Flow<Set<String>> =
        prefs.map { it[Keys.CLEAR_ON_EXIT_CATEGORIES] ?: DEFAULT_CLEAR_ON_EXIT_CATEGORIES }

    val historyRetentionDays: Flow<Int> =
        prefs.map { it[Keys.HISTORY_RETENTION_DAYS] ?: DEFAULT_HISTORY_RETENTION_DAYS }

    val restoreTabs: Flow<Boolean> =
        prefs.map { it[Keys.RESTORE_TABS] ?: DEFAULT_RESTORE_TABS }

    suspend fun setSearchEngineId(id: String) {
        val clean = id.trim().ifBlank { DEFAULT_SEARCH_ENGINE_ID }
        dataStore.edit { it[Keys.SEARCH_ENGINE_ID] = clean }
    }

    suspend fun setCustomSearchTemplate(template: String) {
        dataStore.edit { it[Keys.CUSTOM_SEARCH_TEMPLATE] = template.trim() }
    }

    suspend fun setHomepageMode(mode: String) {
        val clean = if (mode in HOMEPAGE_MODES) mode else DEFAULT_HOMEPAGE_MODE
        dataStore.edit { it[Keys.HOMEPAGE_MODE] = clean }
    }

    suspend fun setHomepageUrl(url: String) {
        val clean = url.trim()
        if (clean.isNotEmpty()) {
            dataStore.edit { it[Keys.HOMEPAGE_URL] = clean }
        }
    }

    suspend fun setThemeMode(mode: String) {
        val clean = if (mode in THEME_MODES) mode else DEFAULT_THEME_MODE
        dataStore.edit { it[Keys.THEME_MODE] = clean }
    }

    /** Coerced to 50..200 (the WebView textZoom range the UI slider offers). */
    suspend fun setTextZoomPercent(percent: Int) {
        dataStore.edit { it[Keys.TEXT_ZOOM_PERCENT] = percent.coerceIn(50, 200) }
    }

    suspend fun setWebDarkening(enabled: Boolean) {
        dataStore.edit { it[Keys.WEB_DARKENING] = enabled }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.JAVASCRIPT_ENABLED] = enabled }
    }

    suspend fun setBlockThirdPartyCookies(block: Boolean) {
        dataStore.edit { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = block }
    }

    suspend fun setDesktopModeDefault(enabled: Boolean) {
        dataStore.edit { it[Keys.DESKTOP_MODE_DEFAULT] = enabled }
    }

    suspend fun setClearOnExit(enabled: Boolean) {
        dataStore.edit { it[Keys.CLEAR_ON_EXIT] = enabled }
    }

    suspend fun setClearOnExitCategories(categories: Set<String>) {
        dataStore.edit { it[Keys.CLEAR_ON_EXIT_CATEGORIES] = categories.toSet() }
    }

    /** Coerced to 1..365 days; the app-start trim skips when this is 0 or less. */
    suspend fun setHistoryRetentionDays(days: Int) {
        dataStore.edit { it[Keys.HISTORY_RETENTION_DAYS] = days.coerceIn(1, 365) }
    }

    suspend fun setRestoreTabs(restore: Boolean) {
        dataStore.edit { it[Keys.RESTORE_TABS] = restore }
    }

    // ------------------------------------------------- per-origin permission choices

    /**
     * The remembered choice for one origin + permission, or null when the user
     * has never answered. Stored as a string set of "origin|permission|allowed"
     * entries so the whole map stays in one DataStore key.
     */
    fun sitePermissionChoice(origin: String, permission: String): Flow<Boolean?> =
        prefs.map { p ->
            (p[Keys.SITE_PERMISSION_CHOICES] ?: emptySet())
                .asSequence()
                .mapNotNull(::parseChoice)
                .firstOrNull { it.origin == origin && it.permission == permission }
                ?.allowed
        }

    suspend fun setSitePermissionChoice(origin: String, permission: String, allowed: Boolean) {
        dataStore.edit { p ->
            val kept = (p[Keys.SITE_PERMISSION_CHOICES] ?: emptySet())
                .filterNot { raw ->
                    parseChoice(raw)?.let { it.origin == origin && it.permission == permission } == true
                }
                .toMutableSet()
            kept.add(encodeChoice(origin, permission, allowed))
            p[Keys.SITE_PERMISSION_CHOICES] = kept
        }
    }

    suspend fun clearSitePermissionChoices() {
        dataStore.edit { p -> p.remove(Keys.SITE_PERMISSION_CHOICES) }
    }

    /**
     * Every saved choice — feeds the Site permissions settings screen.
     * Extra beyond the phase contract (the screen needs the whole list, not one key).
     */
    fun observeSitePermissionChoices(): Flow<List<SitePermissionChoice>> =
        prefs.map { p ->
            (p[Keys.SITE_PERMISSION_CHOICES] ?: emptySet()).mapNotNull(::parseChoice)
        }

    /**
     * Forgets every choice for one origin (used when the user deletes a site's
     * permissions in settings).
     * Extra beyond the phase contract — the per-origin delete has no single-key API.
     */
    suspend fun removeSitePermissionChoicesForOrigin(origin: String) {
        dataStore.edit { p ->
            p[Keys.SITE_PERMISSION_CHOICES] = (p[Keys.SITE_PERMISSION_CHOICES] ?: emptySet())
                .filterNot { parseChoice(it)?.origin == origin }
                .toSet()
        }
    }

    companion object {
        const val DEFAULT_SEARCH_ENGINE_ID = "google"
        const val DEFAULT_HOMEPAGE_MODE = "home"
        const val DEFAULT_HOMEPAGE_URL = "https://www.google.com"
        const val DEFAULT_THEME_MODE = "system"
        const val DEFAULT_TEXT_ZOOM_PERCENT = 100
        const val DEFAULT_WEB_DARKENING = true
        const val DEFAULT_JAVASCRIPT_ENABLED = true
        const val DEFAULT_BLOCK_THIRD_PARTY_COOKIES = false
        const val DEFAULT_DESKTOP_MODE_DEFAULT = false
        const val DEFAULT_CLEAR_ON_EXIT = false
        val DEFAULT_CLEAR_ON_EXIT_CATEGORIES: Set<String> = setOf("history", "cookies", "cache")
        const val DEFAULT_HISTORY_RETENTION_DAYS = 90
        const val DEFAULT_RESTORE_TABS = true

        val HOMEPAGE_MODES: Set<String> = setOf("home", "blank", "last_tabs")
        val THEME_MODES: Set<String> = setOf("system", "light", "dark")

        // Entries are "origin|permission|allowed"; parsing from the end keeps origins intact.
        private fun encodeChoice(origin: String, permission: String, allowed: Boolean): String =
            "$origin|$permission|$allowed"

        private fun parseChoice(raw: String): SitePermissionChoice? {
            val parts = raw.split("|")
            if (parts.size < 3) return null
            val allowed = parts.last().toBooleanStrictOrNull() ?: return null
            val permission = parts[parts.size - 2]
            val origin = parts.subList(0, parts.size - 2).joinToString("|")
            if (origin.isBlank() || permission.isBlank()) return null
            return SitePermissionChoice(origin, permission, allowed)
        }
    }
}
