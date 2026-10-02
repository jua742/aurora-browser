package com.aurora.browser.navigation

/**
 * All Navigation Compose routes (spec B-1). The browser screen is the hub and
 * takes no arguments — it reads the active tab from BrowserViewModel.
 */
object Routes {
    const val SPLASH = "splash"
    const val BROWSER = "browser"
    const val TABS = "tabs"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val SETTINGS_SEARCH = "settings/search"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_PERMISSIONS = "settings/permissions"
    const val SETTINGS_DOWNLOADS = "settings/downloads"
    const val ABOUT = "about"
}
