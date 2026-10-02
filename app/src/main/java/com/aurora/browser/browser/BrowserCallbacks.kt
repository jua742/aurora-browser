package com.aurora.browser.browser

import android.webkit.GeolocationPermissions
import android.webkit.WebView

/**
 * Events flowing from a tab's WebView clients up to the browser layer.
 *
 * TabManager wraps this per tab: UI-state events (progress, title, ...) only
 * reach BrowserViewModel when they come from the ACTIVE tab, while page visits,
 * downloads and popups always go through.
 */
interface BrowserCallbacks {
    /** Page load progress 0..100 (from onProgressChanged). */
    fun onProgressChanged(progress: Int)

    /** A real page started loading (never our browser-error:// pages). */
    fun onPageStarted(url: String?)

    /** History hook: a real http(s) page was visited (never incognito/error pages). */
    fun onPageVisit(url: String, title: String)

    /** A real page finished loading. */
    fun onPageFinished(url: String?, title: String?)

    /** The page asked to download a file (from WebView.setDownloadListener). */
    fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    )

    /** A popup window was opened as a new tab (from onCreateWindow). */
    fun onPopupWebViewCreated(newWebView: WebView)

    /** Page title arrived (from onReceivedTitle). */
    fun onTitleReceived(title: String?)

    /** The error page's [Go home] button was tapped (browser-error://home). */
    fun onHomeRequested()

    /** The renderer process crashed and the tab's WebView was recreated. */
    fun onRenderProcessCrashed()

    /** Find-in-page match update (from WebView.setFindListener). */
    fun onFindResult(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean)

    /** A site asked for geolocation and needs the Allow-once/Always/Block dialog. */
    fun onGeolocationPrompt(origin: String, callback: GeolocationPermissions.Callback)
}
