package com.aurora.browser.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aurora.browser.R

/**
 * Handles navigation, errors, SSL, external schemes and history for one tab's
 * WebView (spec B-3, B-17).
 *
 * Honest v1 rules enforced here:
 * - NO addJavascriptInterface anywhere in the app. The error page's [Try again]
 *   button links to browser-error://retry, which is intercepted below.
 * - SSL errors: handler.cancel() with NO "proceed anyway" option (beginner-safe).
 * - Non-http(s) schemes: the tel:/mailto:/sms:/smsto: allowlist with a
 *   resolveActivity check; everything else (including intent:) is blocked.
 * - Error pages are local (assets/error.html) and never show raw exceptions.
 */
class AuroraWebViewClient(
    private val appContext: Context,
    private val callbacks: BrowserCallbacks,
    private val isIncognito: () -> Boolean,
    private val onMessage: (String) -> Unit,
) : WebViewClient() {

    /** The page that failed, so browser-error://retry reloads exactly that URL. */
    private var lastFailedUrl: String? = null

    // -- Navigation -----------------------------------------------------------

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Error-page actions (spec B-17): the buttons link to these pseudo-URLs.
        when {
            url.startsWith(ERROR_SCHEME_RETRY) -> {
                lastFailedUrl?.let { view.loadUrl(it) }
                return true
            }
            url.startsWith(ERROR_SCHEME_HOME) -> {
                callbacks.onHomeRequested()
                return true
            }
        }

        // http(s) — including redirects — is handled internally by the WebView.
        if (UrlResolver.isHttpUrl(url)) return false

        // Anything else goes through the external-scheme allowlist.
        when (ExternalLinkHandler.handle(appContext, url)) {
            ExternalLinkHandler.Result.Opened -> Unit
            ExternalLinkHandler.Result.NoHandler ->
                onMessage(appContext.getString(R.string.toast_no_app_for_link))
            ExternalLinkHandler.Result.Blocked ->
                onMessage(appContext.getString(R.string.toast_link_blocked))
        }
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // Our own error pages must not touch UI state (address bar, progress).
        if (url.startsWith(ERROR_SCHEME)) return
        callbacks.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        if (url.startsWith(ERROR_SCHEME)) return
        callbacks.onPageFinished(url, view.title)
    }

    /**
     * History hook (spec B-7): every real http(s) page visit, except incognito
     * tabs and our own error pages.
     */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (isIncognito()) return
        if (url.startsWith(ERROR_SCHEME)) return
        if (!UrlResolver.isHttpUrl(url)) return
        callbacks.onPageVisit(url, view.title ?: url)
    }

    // -- Errors -> local error pages (spec B-17) -------------------------------

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        // Main frame only: sub-resource failures (a broken image) must not
        // replace the whole page.
        if (!request.isForMainFrame) return
        val type = when (error.errorCode) {
            ERROR_HOST_LOOKUP -> ErrorType.DNS
            ERROR_CONNECT, ERROR_TIMEOUT, ERROR_UNKNOWN -> ErrorType.OFFLINE
            else -> ErrorType.LOAD
        }
        showErrorPage(view, type, request.url.toString(), httpCode = null)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (!request.isForMainFrame) return
        showErrorPage(view, ErrorType.HTTP, request.url.toString(), errorResponse.statusCode)
    }

    /**
     * SSL: cancel the load and show the SSL error page. There is deliberately
     * NO "proceed anyway" option in v1 (spec B-3/B-13).
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        showErrorPage(view, ErrorType.SSL, error.url ?: "", httpCode = null)
    }

    /**
     * The renderer crashed: show the crash page, then ask TabManager (via the
     * callback) to destroy and recreate this tab's WebView. Returning true
     * means "we handled it" — the default crash UI never appears.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (detail.didCrash()) {
            showErrorPage(view, ErrorType.CRASH, lastFailedUrl ?: "", httpCode = null)
            callbacks.onRenderProcessCrashed()
        }
        return true
    }

    // -- Local error pages ------------------------------------------------------

    private enum class ErrorType(val query: String) {
        OFFLINE("offline"),
        DNS("dns"),
        HTTP("http"),
        SSL("ssl"),
        CRASH("crash"),
        LOAD("load"),
    }

    private fun showErrorPage(
        view: WebView,
        type: ErrorType,
        failingUrl: String,
        httpCode: Int?,
    ) {
        lastFailedUrl = failingUrl.ifBlank { null }
        val titleRes = when (type) {
            ErrorType.OFFLINE -> R.string.error_offline_title
            ErrorType.DNS -> R.string.error_dns_title
            ErrorType.HTTP -> R.string.error_http_title
            ErrorType.SSL -> R.string.error_ssl_title
            ErrorType.CRASH -> R.string.error_crash_title
            ErrorType.LOAD -> R.string.error_load_title
        }
        val messageRes = when (type) {
            ErrorType.OFFLINE -> R.string.error_offline_message
            ErrorType.DNS -> R.string.error_dns_message
            ErrorType.HTTP -> R.string.error_http_message
            ErrorType.SSL -> R.string.error_ssl_message
            ErrorType.CRASH -> R.string.error_crash_message
            ErrorType.LOAD -> R.string.error_load_message
        }
        // Never show raw exception text to the user (spec B-17): only our strings.
        val message = if (type == ErrorType.HTTP && httpCode != null) {
            appContext.getString(messageRes, httpCode)
        } else {
            appContext.getString(messageRes)
        }
        val html = loadErrorTemplate()
            .replace("{title}", escapeHtml(appContext.getString(titleRes)))
            .replace("{message}", escapeHtml(message))
            .replace("{url}", escapeHtml(failingUrl))
            // SSL pages hide [Try again] on purpose: retrying a blocked page is pointless.
            .replace("{showRetry}", if (type == ErrorType.SSL) "false" else "true")
        // The base URL carries the variant for the page's icon script. History sees
        // only browser-error://..., which doUpdateVisitedHistory skips, and a
        // reload() of this entry would just re-show the error page — that is why
        // [Try again] loads lastFailedUrl explicitly instead.
        view.loadDataWithBaseURL("$ERROR_SCHEME?type=${type.query}", html, "text/html", "utf-8", null)
    }

    private fun loadErrorTemplate(): String = try {
        appContext.assets.open("error.html").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        // assets/error.html must always exist, but never show a blank page.
        "<html><body><h1>{title}</h1><p>{message}</p></body></html>"
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        /** Pseudo-scheme for our local error pages; never leaves the app. */
        const val ERROR_SCHEME = "browser-error://"
        private const val ERROR_SCHEME_RETRY = "browser-error://retry"
        private const val ERROR_SCHEME_HOME = "browser-error://home"
    }
}
