package com.aurora.browser.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.aurora.browser.R
import com.aurora.browser.data.datastore.SettingsRepository
import com.aurora.browser.permissions.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.UUID

/** UI-friendly snapshot of one tab, read by Compose. */
data class TabInfo(
    val id: String,
    val title: String?,
    val url: String?,
    val host: String?,
    val isIncognito: Boolean,
    val hasLoadedPage: Boolean,
    val thumbnail: Bitmap?,
)

/** One browser tab: its WebView plus per-tab state. */
class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val webView: WebView,
    val isIncognito: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** null = follow the app default; set by the per-tab "Desktop site" toggle. */
    var desktopMode: Boolean? = null

    /** Last URL loadUrl() was called with (for crash recovery + session restore). */
    var lastUrl: String? = null

    /** Last reported load progress 0..100. */
    var lastProgress: Int = 0

    /** True once a real page (not the home screen) has started loading. */
    var hasLoadedPage: Boolean = false
}

/** Snapshot of the DataStore settings that affect WebViews. */
data class WebViewSettings(
    val javaScriptEnabled: Boolean = true,
    val blockThirdPartyCookies: Boolean = false,
    val textZoomPercent: Int = 100,
    val webDarkening: Boolean = false,
    val desktopModeDefault: Boolean = false,
    val userAgentSuffix: String = "AuroraBrowser/1.0",
)

/**
 * Owns one WebView per tab (spec B-5). Only the ACTIVE tab's WebView is attached
 * to the view hierarchy; background tabs stay alive but detached, so their
 * scroll position, form and JS state survive switching.
 *
 * Threading: every method here must run on the UI thread (WebView requirement).
 *
 * Rotation note: WebViews are created with the Activity context (correct theming
 * and dialogs). [activity] is refreshed via BrowserViewModel.attachActivity after
 * rotation; WebViews created before the rotation keep the old Activity until
 * their tab closes — a bounded, documented v1 trade-off. WebViews are never
 * kept in a static field (spec B-18) and are all destroyed in [destroy].
 */
class TabManager(
    var activity: ComponentActivity,
    private val outerCallbacks: BrowserCallbacks,
    var fileChooserDelegate: FileChooserDelegate,
    var permissionManager: PermissionManager,
    private val settingsRepository: SettingsRepository,
    private val fullscreenHandler: FullscreenHandler,
    private val showToast: (String) -> Unit,
    /** Called after every tab-list mutation so the ViewModel can refresh UI state. */
    private val onTabsChanged: () -> Unit,
    private val maxNormalTabsProvider: () -> Int = { MAX_NORMAL_TABS },
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tabs = mutableListOf<BrowserTab>()
    val allTabs: List<BrowserTab> get() = tabs.toList()

    var activeTabId: String? = null
        private set

    private var currentSettings = WebViewSettings()

    /**
     * Best-effort thumbnails, in-memory LRU (max 8), never written to disk
     * (spec B-5). Missing/failed captures fall back to a placeholder in the UI.
     */
    private val thumbnails = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > MAX_THUMBNAILS
    }

    fun activeTab(): BrowserTab? = tabs.find { it.id == activeTabId }
    fun getTab(id: String): BrowserTab? = tabs.find { it.id == id }

    fun toInfo(tab: BrowserTab): TabInfo = TabInfo(
        id = tab.id,
        title = tab.webView.title,
        url = tab.webView.url?.takeIf { it.isNotBlank() },
        host = tab.webView.url?.let { UrlResolver.hostOf(it) },
        isIncognito = tab.isIncognito,
        hasLoadedPage = tab.hasLoadedPage,
        thumbnail = thumbnails[tab.id],
    )

    // -- Tab lifecycle ----------------------------------------------------------

    /**
     * Create a tab. [url] null = fresh home tab (shows the Compose home screen).
     * Enforces the 20 normal / 10 incognito cap with LRU discard + toast.
     */
    fun createTab(url: String? = null, incognito: Boolean = false, activate: Boolean = true): BrowserTab {
        enforceTabCap(incognito)
        val tab = BrowserTab(webView = WebView(activity), isIncognito = incognito)
        configureWebView(tab)
        tabs.add(tab)
        if (activate) setActiveTab(tab.id) else onTabsChanged()
        if (url != null) loadUrl(tab.id, url)
        return tab
    }

    fun setActiveTab(id: String) {
        val tab = getTab(id) ?: return
        if (id == activeTabId) {
            onTabsChanged()
            return
        }
        // Best-effort thumbnail of the tab we're leaving.
        activeTab()?.let { captureThumbnail(it) }
        activeTabId = id
        onTabsChanged()
    }

    fun loadUrl(tabId: String, url: String) {
        val tab = getTab(tabId) ?: return
        tab.lastUrl = url
        tab.webView.loadUrl(url)
    }

    fun closeTab(id: String) {
        val tab = getTab(id) ?: return
        val wasActive = id == activeTabId
        val wasIncognito = tab.isIncognito
        destroyTab(tab)
        tabs.remove(tab)
        thumbnails.remove(id)
        if (wasActive) {
            // Activate the nearest neighbor of the same kind; never show a
            // tab-less browser (spec B-5). Closing the last incognito tab lands
            // on a normal tab = exiting the incognito UI.
            val neighbor = tabs.lastOrNull { it.isIncognito == wasIncognito }
                ?: tabs.lastOrNull()
            if (neighbor != null) setActiveTab(neighbor.id)
            else createTab(incognito = false, activate = true)
        } else {
            onTabsChanged()
        }
        if (wasIncognito && tabs.none { it.isIncognito }) wipeIncognitoData()
    }

    fun closeAllTabs(incognito: Boolean? = null) {
        val doomed = if (incognito == null) tabs.toList() else tabs.filter { it.isIncognito == incognito }
        if (doomed.isEmpty()) return
        val activeClosed = doomed.any { it.id == activeTabId }
        val hadIncognito = doomed.any { it.isIncognito }
        doomed.forEach { destroyTab(it); thumbnails.remove(it.id) }
        tabs.removeAll(doomed.toSet())
        if (activeClosed) {
            val neighbor = tabs.lastOrNull()
            if (neighbor != null) setActiveTab(neighbor.id)
            else createTab(incognito = false, activate = true)
        } else {
            onTabsChanged()
        }
        if (hadIncognito && tabs.none { it.isIncognito }) wipeIncognitoData()
    }

    private fun destroyTab(tab: BrowserTab) {
        try {
            (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
            tab.webView.destroy()
        } catch (_: Exception) {
            // Already dead — nothing to do.
        }
    }

    /** Cap: 20 normal (+10 incognito). Oldest BACKGROUND tab is closed, with a toast. */
    private fun enforceTabCap(incognito: Boolean) {
        val max = if (incognito) MAX_INCOGNITO_TABS else maxNormalTabsProvider().coerceAtLeast(1)
        val sameType = tabs.filter { it.isIncognito == incognito }
        if (sameType.size < max) return
        val victim = sameType.filter { it.id != activeTabId }.minByOrNull { it.createdAt }
        if (victim != null) {
            destroyTab(victim)
            tabs.remove(victim)
            thumbnails.remove(victim.id)
            showToast(activity.getString(R.string.toast_oldest_tab_closed))
        }
    }

    // -- WebView attachment -------------------------------------------------------

    /** Attach the active tab's WebView to the Compose container (UI thread). */
    fun bindActiveWebView(container: ViewGroup) {
        val webView = activeTab()?.webView ?: return
        (webView.parent as? ViewGroup)?.removeView(webView)
        container.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    // -- Settings -------------------------------------------------------------------

    /** Re-apply settings to every tab (called when DataStore settings change). */
    fun updateWebViewSettings(s: WebViewSettings) {
        currentSettings = s
        for (tab in tabs) applySettings(tab.webView, s, tab.desktopMode)
    }

    /** Per-tab desktop toggle: swap the UA string and reload (spec B-3). */
    fun setDesktopMode(tabId: String, desktop: Boolean) {
        val tab = getTab(tabId) ?: return
        tab.desktopMode = desktop
        applySettings(tab.webView, currentSettings, desktop)
        tab.webView.reload()
    }

    /** Apply the required WebView configuration (spec B-3 hardening list). */
    private fun applySettings(webView: WebView, s: WebViewSettings, desktopMode: Boolean?) {
        val ws: WebSettings = webView.settings
        ws.javaScriptEnabled = s.javaScriptEnabled
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.mediaPlaybackRequiresUserGesture = true
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(ws, true)
        }
        // Hardening (spec B-13): no file access from pages; content access stays
        // on so the file chooser's content:// URIs keep working.
        ws.allowFileAccess = false
        ws.allowContentAccess = true
        ws.allowFileAccessFromFileURLs = false
        ws.allowUniversalAccessFromFileURLs = false
        // NO addJavascriptInterface in v1 — removes an entire attack class.
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.textZoom = s.textZoomPercent.coerceIn(50, 200)
        // Third-party cookies: CookieManager is process-global; this is per-WebView (API 21+).
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !s.blockThirdPartyCookies)
        // Algorithmic darkening follows the app theme; no-op on old WebViews.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(ws, s.webDarkening)
        }
        // User agent: default + honest app token; the desktop toggle swaps the string.
        val useDesktop = desktopMode ?: s.desktopModeDefault
        ws.userAgentString = if (useDesktop) {
            DESKTOP_UA
        } else {
            ws.userAgentString.substringBefore(" AuroraBrowser/") + " " + s.userAgentSuffix
        }
        webView.setSupportMultipleWindows(true)
    }

    // -- App lifecycle ---------------------------------------------------------------

    fun onAppPaused() {
        try {
            activeTab()?.webView?.onPause()
            activeTab()?.webView?.pauseTimers()
        } catch (_: Exception) {
            // Best effort.
        }
    }

    fun onAppResumed() {
        try {
            activeTab()?.webView?.onResume()
            activeTab()?.webView?.resumeTimers()
        } catch (_: Exception) {
            // Best effort.
        }
    }

    fun clearAllCaches() {
        for (tab in tabs) {
            try {
                tab.webView.clearCache(true)
            } catch (_: Exception) {
                // Best effort.
            }
        }
    }

    fun destroy() {
        try {
            mainScope.cancel()
        } catch (_: Exception) {
            // Best effort.
        }
        for (tab in tabs.toList()) destroyTab(tab)
        tabs.clear()
        activeTabId = null
    }

    // -- Incognito ---------------------------------------------------------------------

    /**
     * Wipe session data when the last incognito tab closes (spec B-9).
     *
     * HONEST LIMITATION (spec A-5): CookieManager is PROCESS-GLOBAL on Android,
     * so incognito tabs can never have truly isolated cookies while they are
     * open. Incognito here means: no history writes + everything wiped when the
     * private session ends. The UI must never claim anonymity.
     */
    private fun wipeIncognitoData() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        // Cache + form data live in the same app-global stores. clearCache /
        // clearFormData are instance methods, so a transient WebView does the job;
        // it is destroyed immediately.
        try {
            WebView(activity).apply {
                clearCache(true)
                clearFormData()
                destroy()
            }
        } catch (_: Exception) {
            // Best effort.
        }
    }

    // -- Popups & crash recovery ----------------------------------------------------------

    /** WebChromeClient.onCreateWindow -> the popup becomes a real new tab (spec B-3). */
    private fun openPopup(resultMsg: android.os.Message): Boolean = try {
        val fromIncognito = activeTab()?.isIncognito == true
        val newTab = createTab(incognito = fromIncognito, activate = true)
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = newTab.webView
        resultMsg.send()
        outerCallbacks.onPopupWebViewCreated(newTab.webView)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Render-process crash recovery (spec B-3): the old WebView is dead, so
     * build a fresh one for the same tab id and reload its last URL.
     */
    fun recreateWebView(tabId: String) {
        val tab = getTab(tabId) ?: return
        val index = tabs.indexOf(tab)
        val lastUrl = tab.lastUrl
        destroyTab(tab)
        val newTab = BrowserTab(
            id = tab.id,
            webView = WebView(activity),
            isIncognito = tab.isIncognito,
            createdAt = tab.createdAt,
        )
        newTab.desktopMode = tab.desktopMode
        newTab.lastUrl = lastUrl
        configureWebView(newTab)
        tabs[index] = newTab
        if (lastUrl != null) loadUrl(newTab.id, lastUrl)
        onTabsChanged()
    }

    // -- Internal wiring ---------------------------------------------------------------------

    private fun configureWebView(tab: BrowserTab) {
        val webView = tab.webView
        val tabCallbacks = TabCallbacks(tab)
        webView.webViewClient = AuroraWebViewClient(
            appContext = activity.applicationContext,
            callbacks = tabCallbacks,
            isIncognito = { tab.isIncognito },
            onMessage = showToast,
        )
        webView.webChromeClient = AuroraWebChromeClient(
            callbacks = tabCallbacks,
            fileChooserDelegate = fileChooserDelegate,
            permissionManager = permissionManager,
            settingsRepository = settingsRepository,
            mainScope = mainScope,
            fullscreenHandler = fullscreenHandler,
            onCreateWindow = ::openPopup,
        )
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            tabCallbacks.onDownloadRequested(url, userAgent, contentDisposition, mimeType, contentLength)
        }
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            tabCallbacks.onFindResult(activeMatchOrdinal, numberOfMatches, isDoneCounting)
        }
        applySettings(webView, currentSettings, tab.desktopMode)
    }

    private fun captureThumbnail(tab: BrowserTab) {
        try {
            val wv = tab.webView
            if (wv.width <= 0 || wv.height <= 0 || !tab.hasLoadedPage) return
            val targetW = 360
            val targetH = targetW * 10 / 16
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            canvas.scale(targetW / wv.width.toFloat(), targetH / wv.height.toFloat())
            wv.draw(canvas)
            thumbnails[tab.id] = bmp
        } catch (_: Exception) {
            // Best-effort only: the UI shows a placeholder instead.
        }
    }

    /**
     * Per-tab callback proxy: page visits, downloads, popups and geolocation
     * always reach the ViewModel; UI-state events (progress, title, find) only
     * when this is the ACTIVE tab — otherwise a background tab would hijack
     * the toolbar.
     */
    private inner class TabCallbacks(private val tab: BrowserTab) : BrowserCallbacks {
        private val isActive: Boolean get() = tab.id == activeTabId

        override fun onProgressChanged(progress: Int) {
            tab.lastProgress = progress
            if (isActive) outerCallbacks.onProgressChanged(progress)
        }

        override fun onPageStarted(url: String?) {
            if (url != null && UrlResolver.isHttpUrl(url)) {
                tab.hasLoadedPage = true
                tab.lastUrl = url
            }
            if (isActive) outerCallbacks.onPageStarted(url)
        }

        override fun onPageVisit(url: String, title: String) =
            outerCallbacks.onPageVisit(url, title)

        override fun onPageFinished(url: String?, title: String?) {
            if (isActive) outerCallbacks.onPageFinished(url, title)
        }

        override fun onDownloadRequested(
            url: String,
            userAgent: String?,
            contentDisposition: String?,
            mimeType: String?,
            contentLength: Long,
        ) = outerCallbacks.onDownloadRequested(url, userAgent, contentDisposition, mimeType, contentLength)

        override fun onPopupWebViewCreated(newWebView: WebView) =
            outerCallbacks.onPopupWebViewCreated(newWebView)

        override fun onTitleReceived(title: String?) {
            if (isActive) outerCallbacks.onTitleReceived(title)
        }

        override fun onHomeRequested() = outerCallbacks.onHomeRequested()

        override fun onRenderProcessCrashed() {
            recreateWebView(tab.id)
            if (isActive) outerCallbacks.onRenderProcessCrashed()
        }

        override fun onFindResult(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
            if (isActive) outerCallbacks.onFindResult(activeMatchOrdinal, numberOfMatches, isDoneCounting)
        }

        override fun onGeolocationPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) =
            outerCallbacks.onGeolocationPrompt(origin, callback)
    }

    companion object {
        const val MAX_NORMAL_TABS = 20
        const val MAX_INCOGNITO_TABS = 10
        private const val MAX_THUMBNAILS = 8

        /** Desktop Chrome UA + honest app token (spec B-3). */
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36 AuroraBrowser/1.0"
    }
}
