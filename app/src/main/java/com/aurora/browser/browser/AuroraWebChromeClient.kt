package com.aurora.browser.browser

import android.Manifest
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.aurora.browser.data.datastore.SettingsRepository
import com.aurora.browser.permissions.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Chrome-side hooks for one tab's WebView (spec B-3 + Phase 9 engine parts):
 * progress, titles, file upload, camera/mic permission, geolocation,
 * fullscreen video and popup windows.
 *
 * Nothing here is ever auto-granted: camera/mic go through the Android runtime
 * permission first and only the user-approved resources are granted; location
 * additionally needs the per-origin dialog (Allow once / Always / Block).
 */
class AuroraWebChromeClient(
    private val callbacks: BrowserCallbacks,
    private val fileChooserDelegate: FileChooserDelegate,
    private val permissionManager: PermissionManager,
    private val settingsRepository: SettingsRepository,
    private val mainScope: CoroutineScope,
    private val fullscreenHandler: FullscreenHandler,
    private val onCreateWindow: (Message) -> Boolean,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        callbacks.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        callbacks.onTitleReceived(title)
    }

    // -- File upload (spec B-3) --------------------------------------------------

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        fileChooserDelegate.launch(fileChooserParams, filePathCallback)
        return true
    }

    // -- Camera / microphone (spec B-3, B-14) -------------------------------------

    override fun onPermissionRequest(request: PermissionRequest) {
        mainScope.launch {
            // Map WebKit resources onto Android runtime permissions.
            // Unknown future resources are simply not mapped: never auto-grant.
            val wanted = mutableMapOf<String, String>() // webkit resource -> android permission
            for (resource in request.resources) {
                when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                        wanted[resource] = Manifest.permission.CAMERA
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                        wanted[resource] = Manifest.permission.RECORD_AUDIO
                }
            }
            if (wanted.isEmpty()) {
                request.deny()
                return@launch
            }
            // Ask the user via the Android runtime permission FIRST (in-context,
            // with rationale — see PermissionManager).
            val results = permissionManager.requestPermissions(*wanted.values.toSet().toTypedArray())
            // Grant ONLY the resources the user approved.
            val granted = wanted.filter { results[it.value] == true }.keys.toTypedArray()
            if (granted.isEmpty()) request.deny() else request.grant(granted)
        }
    }

    // -- Geolocation (spec B-3, B-14) ----------------------------------------------

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        mainScope.launch {
            // Step 1: the Android runtime permission comes first.
            val results = permissionManager.requestPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (results.values.none { it }) {
                callback.invoke(origin, false, false)
                return@launch
            }
            // Step 2: a remembered per-origin choice wins without asking again.
            val remembered: Boolean? = try {
                settingsRepository.sitePermissionChoice(origin, SITE_PERMISSION_LOCATION).first()
            } catch (_: Exception) {
                null
            }
            if (remembered != null) {
                callback.invoke(origin, remembered, true)
                return@launch
            }
            // Step 3: ask the user — Allow once / Always allow / Block
            // (the dialog lives in BrowserScreen; the answer comes back through
            // BrowserViewModel.respondToGeolocation).
            callbacks.onGeolocationPrompt(origin, callback)
        }
    }

    // -- Fullscreen video (spec B-3) ------------------------------------------------

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        fullscreenHandler.showCustomView(view, callback)
    }

    override fun onHideCustomView() {
        fullscreenHandler.hideCustomView()
    }

    // -- Popups open as new tabs (spec B-3) ------------------------------------------

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean = onCreateWindow(resultMsg)

    companion object {
        /** Permission key used with SettingsRepository for geolocation choices. */
        const val SITE_PERMISSION_LOCATION = "LOCATION"
    }
}
