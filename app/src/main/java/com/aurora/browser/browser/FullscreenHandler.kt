package com.aurora.browser.browser

import android.view.View
import android.webkit.WebChromeClient

/**
 * Fullscreen video handling (spec B-3): WebChromeClient.onShowCustomView /
 * onHideCustomView. Implemented by MainActivity, which owns the window.
 */
interface FullscreenHandler {
    /** Show a fullscreen custom view (e.g. a video) and hide the system bars. */
    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback)

    /** Remove the fullscreen view and restore the system bars. */
    fun hideCustomView()

    /** True while a fullscreen custom view is showing (for back-press handling). */
    val isFullscreen: Boolean
}
