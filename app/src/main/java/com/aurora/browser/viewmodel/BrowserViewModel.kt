package com.aurora.browser.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.browser.AuroraApp
import com.aurora.browser.R
import com.aurora.browser.browser.AuroraWebChromeClient
import com.aurora.browser.browser.BrowserCallbacks
import com.aurora.browser.browser.BrowserTab
import com.aurora.browser.browser.DownloadHandler
import com.aurora.browser.browser.ExternalLinkHandler
import com.aurora.browser.browser.FileChooserDelegate
import com.aurora.browser.browser.FullscreenHandler
import com.aurora.browser.browser.SearchEngines
import com.aurora.browser.browser.TabInfo
import com.aurora.browser.browser.TabManager
import com.aurora.browser.browser.UrlResolver
import com.aurora.browser.browser.WebViewSettings
import com.aurora.browser.data.db.entities.HistoryEntry
import com.aurora.browser.data.db.entities.TabRecord
import com.aurora.browser.data.repository.BookmarkWithFolder
import com.aurora.browser.permissions.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The browser's UI state + actions (spec B-15: ViewModel + StateFlow, no Hilt).
 *
 * Owns the [TabManager] (one WebView per tab) and implements [BrowserCallbacks].
 * All user settings come from DataStore Flows and apply live — there are no
 * "Save" buttons anywhere (spec B-10).
 */
class BrowserViewModel(
    application: Application,
    activity: ComponentActivity,
    container: com.aurora.browser.AppContainer,
    fileChooserDelegate: FileChooserDelegate,
    permissionManager: PermissionManager,
    private val fullscreenHandler: FullscreenHandler,
) : AndroidViewModel(application), BrowserCallbacks {

    private val appContext: Context = application.applicationContext
    private val settings = container.settingsRepository
    private val historyRepository = container.historyRepository
    private val bookmarkRepository = container.bookmarkRepository
    private val downloadRepository = container.downloadRepository
    private val tabRepository = container.tabRepository

    // -- One-shot UI events ------------------------------------------------------

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    // -- Tab + page state ----------------------------------------------------------

    val tabManager = TabManager(
        activity = activity,
        outerCallbacks = this,
        fileChooserDelegate = fileChooserDelegate,
        permissionManager = permissionManager,
        settingsRepository = settings,
        fullscreenHandler = fullscreenHandler,
        showToast = { msg -> _toastEvents.tryEmit(msg) },
        onTabsChanged = { refreshTabList() },
    )

    private val _tabs = MutableStateFlow<List<TabInfo>>(emptyList())
    val tabs: StateFlow<List<TabInfo>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _isIncognitoActive = MutableStateFlow(false)
    val isIncognitoActive: StateFlow<Boolean> = _isIncognitoActive.asStateFlow()

    private val _bookmarked = MutableStateFlow(false)
    val bookmarked: StateFlow<Boolean> = _bookmarked.asStateFlow()

    private val _desktopModeActive = MutableStateFlow(false)
    val desktopModeActive: StateFlow<Boolean> = _desktopModeActive.asStateFlow()

    private val _findState = MutableStateFlow<FindState?>(null)
    val findState: StateFlow<FindState?> = _findState.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _geolocationRequest = MutableStateFlow<GeoRequest?>(null)
    val geolocationRequest: StateFlow<GeoRequest?> = _geolocationRequest.asStateFlow()

    private val _tabsRestored = MutableStateFlow(false)
    val tabsRestored: StateFlow<Boolean> = _tabsRestored.asStateFlow()

    /** Fullscreen video state, mirrored from MainActivity (drives back handling). */
    val isFullscreen = MutableStateFlow(false)

    /** Find-in-page UI state. */
    data class FindState(val query: String, val activeMatch: Int, val totalMatches: Int)

    /** A pending site geolocation request waiting for the user's dialog answer. */
    data class GeoRequest(val origin: String, val callback: GeolocationPermissions.Callback)

    /** One address-bar suggestion row (local history or bookmark). */
    data class Suggestion(val title: String, val url: String, val isBookmark: Boolean)

    // -- Settings-derived state ------------------------------------------------------

    val themeMode: StateFlow<String> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    private val searchTemplate: StateFlow<String> =
        combine(settings.searchEngineId, settings.customSearchTemplate, SearchEngines::templateFor)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngines.GOOGLE.searchUrlTemplate)

    private val clearOnExit: StateFlow<Boolean> =
        settings.clearOnExit.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val clearOnExitCategories: StateFlow<Set<String>> =
        settings.clearOnExitCategories.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var lastWebViewSettings = WebViewSettings()

    // -- Suggestions: local history + bookmarks only (spec B-4) ------------------------

    private val suggestionQuery = MutableStateFlow("")

    val suggestions: StateFlow<List<Suggestion>> = suggestionQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(
                    historyRepository.observeSuggestions(q, 4),
                    bookmarkRepository.searchBookmarks(q),
                ) { history, bookmarks ->
                    val fromHistory = history.map { Suggestion(it.title, it.url, false) }
                    val fromBookmarks = bookmarks.take(2)
                        .map { Suggestion(it.bookmark.title, it.bookmark.url, true) }
                    (fromHistory + fromBookmarks).take(6)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSuggestionQuery(query: String) {
        suggestionQuery.value = query
    }

    /** Top-sites tiles for the home screen (max 8, most visited). */
    val topSites: StateFlow<List<HistoryEntry>> =
        historyRepository.observeTopSites(8)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Bookmark chips for the home screen. An empty query matches everything in
     * the DAO's LIKE search, so this yields the most recently updated bookmarks.
     */
    val bookmarkChips: StateFlow<List<BookmarkWithFolder>> =
        bookmarkRepository.searchBookmarks("")
            .map { it.take(12) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -- Init --------------------------------------------------------------------------

    init {
        // Every WebView setting applies live to all tabs (spec B-10).
        combine(
            settings.javaScriptEnabled,
            settings.blockThirdPartyCookies,
            settings.textZoomPercent,
            settings.webDarkening,
            settings.desktopModeDefault,
        ) { javaScript, blockThirdParty, textZoom, darkening, desktopDefault ->
            WebViewSettings(
                javaScriptEnabled = javaScript,
                blockThirdPartyCookies = blockThirdParty,
                textZoomPercent = textZoom,
                webDarkening = darkening,
                desktopModeDefault = desktopDefault,
            )
        }
            .onEach { snapshot ->
                lastWebViewSettings = snapshot
                tabManager.updateWebViewSettings(snapshot)
            }
            .launchIn(viewModelScope)

        startOfflineMonitoring()
        restoreTabs()
    }

    /**
     * Re-point the TabManager at a new Activity after rotation, with freshly
     * registered file-chooser launchers and a PermissionManager bound to the new
     * Activity (the old one would be destroyed and silently deny everything).
     */
    fun attachActivity(activity: ComponentActivity, fileChooserDelegate: FileChooserDelegate) {
        tabManager.activity = activity
        tabManager.fileChooserDelegate = fileChooserDelegate
        tabManager.permissionManager = PermissionManager(activity)
    }

    // -- BrowserCallbacks ---------------------------------------------------------------

    override fun onProgressChanged(progress: Int) {
        _progress.value = progress
        _isLoading.value = progress in 1..99
    }

    override fun onPageStarted(url: String?) {
        _isLoading.value = true
        _progress.value = 0
        _currentUrl.value = url?.takeIf { UrlResolver.isHttpUrl(it) }
    }

    override fun onPageVisit(url: String, title: String) {
        // Incognito/error filtering already happened in AuroraWebViewClient.
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.recordVisit(url, title)
        }
    }

    override fun onPageFinished(url: String?, title: String?) {
        _isLoading.value = false
        _progress.value = 100
        _currentUrl.value = url?.takeIf { UrlResolver.isHttpUrl(it) }
        _currentTitle.value = title
        val tab = tabManager.activeTab()
        _canGoBack.value = tab?.webView?.canGoBack() == true
        _canGoForward.value = tab?.webView?.canGoForward() == true
        updateBookmarked()
    }

    override fun onTitleReceived(title: String?) {
        _currentTitle.value = title
    }

    override fun onHomeRequested() {
        showHomeTab()
    }

    override fun onRenderProcessCrashed() {
        _toastEvents.tryEmit(appContext.getString(R.string.error_crash_title))
    }

    override fun onFindResult(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        _findState.value = _findState.value?.copy(
            activeMatch = activeMatchOrdinal,
            totalMatches = numberOfMatches,
        )
    }

    override fun onGeolocationPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        _geolocationRequest.value = GeoRequest(origin, callback)
    }

    override fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    ) {
        viewModelScope.launch {
            val handler = DownloadHandler(
                context = appContext,
                downloadRepository = downloadRepository,
                cookieProvider = { u -> CookieManager.getInstance().getCookie(u) },
                userAgentProvider = {
                    tabManager.activeTab()?.webView?.settings?.userAgentString.orEmpty()
                },
                onUnsupported = { msg -> _toastEvents.tryEmit(msg) },
            )
            // blob: and friends get the honest "not supported yet" message (spec A-5).
            if (!handler.isSupportedUrl(url)) {
                _toastEvents.emit(appContext.getString(R.string.toast_download_unsupported))
                return@launch
            }
            // enqueueDownload reports failures itself via onUnsupported.
            handler.enqueueDownload(url, contentDisposition, mimeType, contentLength)
        }
    }

    override fun onPopupWebViewCreated(newWebView: WebView) {
        // TabManager already created + activated the tab; nothing left to do.
    }

    // -- Navigation actions -----------------------------------------------------------------

    fun loadInput(input: String) {
        when (val r = UrlResolver.resolve(input)) {
            is UrlResolver.Resolution.Empty -> Unit
            is UrlResolver.Resolution.Invalid ->
                _toastEvents.tryEmit(appContext.getString(R.string.toast_invalid_url))
            is UrlResolver.Resolution.LoadUrl -> activeWebView()?.loadUrl(r.url)
            is UrlResolver.Resolution.Search ->
                activeWebView()?.loadUrl(UrlResolver.buildSearchUrl(searchTemplate.value, r.query))
            is UrlResolver.Resolution.External -> handleExternal(r.url)
        }
    }

    private fun handleExternal(url: String) {
        when (ExternalLinkHandler.handle(appContext, url)) {
            ExternalLinkHandler.Result.Opened -> Unit
            ExternalLinkHandler.Result.NoHandler ->
                _toastEvents.tryEmit(appContext.getString(R.string.toast_no_app_for_link))
            ExternalLinkHandler.Result.Blocked ->
                _toastEvents.tryEmit(appContext.getString(R.string.toast_link_blocked))
        }
    }

    private fun activeWebView(): WebView? = tabManager.activeTab()?.webView
    private fun activeTab(): BrowserTab? = tabManager.activeTab()

    fun goBack() {
        val wv = activeWebView() ?: return
        if (wv.canGoBack()) wv.goBack()
    }

    fun goForward() {
        val wv = activeWebView() ?: return
        if (wv.canGoForward()) wv.goForward()
    }

    fun reload() = activeWebView()?.reload()
    fun stop() = activeWebView()?.stopLoading()

    // -- Tabs ----------------------------------------------------------------------------------

    fun newTab(incognito: Boolean = false) {
        val tab = tabManager.createTab(incognito = incognito, activate = true)
        // New tabs open the homepage setting (or the native home screen).
        viewModelScope.launch { applyHomepage(tab) }
    }

    fun closeTab(id: String) = tabManager.closeTab(id)
    fun setActiveTab(id: String) = tabManager.setActiveTab(id)

    /** [incognito] null = close everything; true/false = close that segment only. */
    fun closeAllTabs(incognito: Boolean? = null) = tabManager.closeAllTabs(incognito)

    /** Attach the active tab's WebView to the Compose container (called from AndroidView). */
    fun attachActiveWebView(container: ViewGroup) = tabManager.bindActiveWebView(container)

    fun toggleDesktopMode() {
        val tab = activeTab() ?: return
        val newValue = !(tab.desktopMode ?: lastWebViewSettings.desktopModeDefault)
        tabManager.setDesktopMode(tab.id, newValue)
        _desktopModeActive.value = newValue
    }

    // -- Home -----------------------------------------------------------------------------------

    /** Show the home tab content (used by new tabs, menu Home, error-page Go home). */
    fun showHomeTab() {
        val tab = activeTab() ?: return
        viewModelScope.launch { applyHomepage(tab) }
    }

    private suspend fun applyHomepage(tab: BrowserTab) {
        when (settings.homepageMode.first()) {
            "blank" -> {
                tabManager.loadUrl(tab.id, "about:blank")
                tab.hasLoadedPage = true
                refreshTabList()
            }
            "home" -> {
                val custom = settings.homepageUrl.first()
                val resolved = UrlResolver.resolve(custom)
                if (resolved is UrlResolver.Resolution.LoadUrl) {
                    tabManager.loadUrl(tab.id, resolved.url)
                } else {
                    // Invalid custom URL: fall back to the native home screen.
                    tab.hasLoadedPage = false
                    refreshTabList()
                }
            }
            // "last_tabs" only affects startup restore; new tabs get the native home screen.
            else -> {
                tab.hasLoadedPage = false
                refreshTabList()
            }
        }
    }

    // -- Bookmarks ---------------------------------------------------------------------------------

    private fun updateBookmarked() {
        val url = _currentUrl.value ?: run {
            _bookmarked.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _bookmarked.value = bookmarkRepository.isBookmarked(url)
        }
    }

    fun toggleBookmark() {
        val url = currentUrl.value ?: return
        val title = currentTitle.value?.takeIf { it.isNotBlank() } ?: url
        viewModelScope.launch(Dispatchers.IO) {
            // Look the row up by URL every time: the bookmark may have been
            // created from the Bookmarks screen, not just from this toolbar.
            val existingId = bookmarkRepository.getBookmarkIdByUrl(url)
            if (existingId != null) {
                bookmarkRepository.deleteBookmark(existingId)
                _bookmarked.value = false
            } else {
                bookmarkRepository.addOrUpdateBookmark(title, url, null)
                _bookmarked.value = true
            }
        }
    }

    // -- Share ----------------------------------------------------------------------------------------

    fun sharePage() {
        val url = currentUrl.value ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(intent, appContext.getString(R.string.menu_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
    }

    // -- Find in page -----------------------------------------------------------------------------------

    fun showFindBar() {
        if (_findState.value == null) _findState.value = FindState("", 0, 0)
    }

    fun findInPage(query: String) {
        if (query.isBlank()) {
            activeWebView()?.clearMatches()
            _findState.value = FindState("", 0, 0)
            return
        }
        if (_findState.value?.query != query) _findState.value = FindState(query, 0, 0)
        activeWebView()?.findAllAsync(query)
    }

    fun findNext() {
        activeWebView()?.findNext(true)
    }

    fun findPrevious() {
        activeWebView()?.findNext(false)
    }

    fun clearFind() {
        activeWebView()?.clearMatches()
        _findState.value = null
    }

    // -- Geolocation dialog answers ------------------------------------------------------------------------

    fun respondToGeolocation(allow: Boolean, remember: Boolean) {
        val req = _geolocationRequest.value ?: return
        _geolocationRequest.value = null
        viewModelScope.launch {
            if (remember) {
                settings.setSitePermissionChoice(
                    req.origin,
                    AuroraWebChromeClient.SITE_PERMISSION_LOCATION,
                    allow,
                )
            }
            req.callback.invoke(req.origin, allow, remember)
        }
    }

    fun dismissGeolocation() {
        val req = _geolocationRequest.value ?: return
        _geolocationRequest.value = null
        req.callback.invoke(req.origin, false, false)
    }

    // -- Fullscreen ----------------------------------------------------------------------------------------------

    fun exitFullscreen() {
        fullscreenHandler.hideCustomView()
        isFullscreen.value = false
    }

    // -- Clear browsing data (also used by clear-on-exit) --------------------------------------------------------------

    fun clearBrowsingData(categories: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ClearDataCategories.HISTORY in categories) historyRepository.clearAll()
            if (ClearDataCategories.PERMISSIONS in categories) settings.clearSitePermissionChoices()
            // Downloads list rows only — DownloadManager never deletes user files here.
            if (ClearDataCategories.DOWNLOADS in categories) downloadRepository.clearRecords()
            withContext(Dispatchers.Main) {
                if (ClearDataCategories.COOKIES in categories) {
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                }
                if (ClearDataCategories.CACHE in categories) tabManager.clearAllCaches()
            }
        }
    }

    /**
     * Category keys for [clearBrowsingData]. The Privacy settings screen passes
     * these; defaults in DataStore ("history","cookies","cache") match.
     */
    object ClearDataCategories {
        const val HISTORY = "history"
        const val COOKIES = "cookies"
        const val CACHE = "cache"
        const val DOWNLOADS = "downloads"
        const val PERMISSIONS = "permissions"
    }

    // -- Session persistence ----------------------------------------------------------------------

    /** Save non-incognito tabs to Room (called on pause; spec B-5). */
    fun saveTabsToRepository() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val records = tabManager.allTabs
                .filter { !it.isIncognito }
                .mapIndexed { index, tab ->
                    TabRecord(
                        tabId = tab.id,
                        url = tab.webView.url ?: "",
                        title = tab.webView.title ?: "",
                        position = index,
                        lastActive = if (tab.id == tabManager.activeTabId) now else tab.createdAt,
                    )
                }
            tabRepository.saveTabs(records)
        }
    }

    /** Rebuild tabs after process death by re-loading each URL (spec B-5). */
    private fun restoreTabs() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = try {
                if (settings.restoreTabs.first()) tabRepository.loadTabs() else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (records.isEmpty()) {
                    val tab = tabManager.createTab(activate = true)
                    launch { applyHomepage(tab) }
                } else {
                    val created = records.sortedBy { it.position }.map { record ->
                        record to tabManager.createTab(
                            url = record.url.ifBlank { null },
                            activate = false,
                        )
                    }
                    val toActivate = created.maxByOrNull { it.first.lastActive }?.second
                    if (toActivate != null) tabManager.setActiveTab(toActivate.id)
                    else tabManager.createTab(activate = true)
                }
                _tabsRestored.value = true
                refreshTabList()
            }
        }
    }

    // -- Offline monitoring ----------------------------------------------------------------------------

    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOffline.value = false
        }

        override fun onLost(network: Network) {
            _isOffline.value = !hasNetwork()
        }
    }

    private fun hasNetwork(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startOfflineMonitoring() {
        val cm = connectivityManager ?: return
        _isOffline.value = !hasNetwork()
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
        } catch (_: Exception) {
            // Without ACCESS_NETWORK_STATE the banner just never shows; page
            // loads still surface the offline error page.
        }
    }

    // -- Activity lifecycle hooks (called from MainActivity) ---------------------------------------------------

    fun onActivityPaused() {
        tabManager.onAppPaused()
        saveTabsToRepository()
    }

    fun onActivityResumed() {
        tabManager.onAppResumed()
    }

    fun onActivityDestroyed() {
        if (clearOnExit.value) clearBrowsingData(clearOnExitCategories.value)
    }

    // -- Internal ----------------------------------------------------------------------------------------------

    private fun refreshTabList() {
        _tabs.value = tabManager.allTabs.map { tabManager.toInfo(it) }
        refreshActiveTabState()
    }

    private fun refreshActiveTabState() {
        val tab = tabManager.activeTab()
        _activeTabId.value = tab?.id
        val rawUrl = tab?.webView?.url
        _currentUrl.value = rawUrl?.takeIf { UrlResolver.isHttpUrl(it) }
        _currentTitle.value = tab?.webView?.title
        _canGoBack.value = tab?.webView?.canGoBack() == true
        _canGoForward.value = tab?.webView?.canGoForward() == true
        _progress.value = tab?.lastProgress ?: 0
        _isLoading.value = (tab?.lastProgress ?: 0) in 1..99
        _isIncognitoActive.value = tab?.isIncognito == true
        _desktopModeActive.value =
            tab?.let { it.desktopMode ?: lastWebViewSettings.desktopModeDefault } == true
        updateBookmarked()
    }

    override fun onCleared() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Best effort.
        }
        tabManager.destroy()
        super.onCleared()
    }
}

/** Factory: the ViewModel needs the Activity, the DI container and delegates. */
class BrowserViewModelFactory(
    private val activity: ComponentActivity,
    private val fileChooserDelegate: FileChooserDelegate,
    private val permissionManager: PermissionManager,
    private val fullscreenHandler: FullscreenHandler,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
            val app = activity.application
            val container = (app as AuroraApp).container
            return BrowserViewModel(
                application = app,
                activity = activity,
                container = container,
                fileChooserDelegate = fileChooserDelegate,
                permissionManager = permissionManager,
                fullscreenHandler = fullscreenHandler,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
