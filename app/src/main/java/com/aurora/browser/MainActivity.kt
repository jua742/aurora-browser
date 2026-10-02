package com.aurora.browser

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.browser.browser.FileChooserDelegate
import com.aurora.browser.browser.FullscreenHandler
import com.aurora.browser.navigation.AuroraNavGraph
import com.aurora.browser.permissions.PermissionManager
import com.aurora.browser.ui.theme.AuroraBrowserTheme
import com.aurora.browser.viewmodel.BrowserViewModel
import com.aurora.browser.viewmodel.BrowserViewModelFactory

/**
 * The single Activity (spec B-16: single-Activity architecture).
 *
 * Responsibilities:
 * - Hosts the Compose [AuroraNavGraph] inside [AuroraBrowserTheme].
 * - Registers the file-chooser ActivityResultLaunchers and hands them to the
 *   [FileChooserDelegate] used by AuroraWebChromeClient (must happen in onCreate,
 *   before the Activity is STARTED).
 * - Implements [FullscreenHandler] for WebChromeClient fullscreen video.
 * - Forwards onPause/onResume/onDestroy to [BrowserViewModel] lifecycle hooks
 *   (WebView timers, tab persistence, clear-on-exit wipe).
 *
 * Back-press order is handled in Compose (BrowserScreen): menu -> fullscreen ->
 * find bar -> WebView history -> nav pop (spec B-1).
 */
class MainActivity : ComponentActivity(), FullscreenHandler {

    private lateinit var fileChooserDelegate: FileChooserDelegate

    private val browserViewModel: BrowserViewModel by viewModels {
        // Evaluated lazily on first access below — AFTER fileChooserDelegate exists.
        BrowserViewModelFactory(
            activity = this,
            fileChooserDelegate = fileChooserDelegate,
            permissionManager = PermissionManager(this),
            fullscreenHandler = this,
        )
    }

    // -- Fullscreen video state (driven by WebChromeClient.onShowCustomView) --
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    override val isFullscreen: Boolean get() = customView != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launchers must be registered before onStart; the delegate is just a
        // thin holder the WebChromeClient calls into.
        val singlePicker =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (::fileChooserDelegate.isInitialized) fileChooserDelegate.onSingleResult(uri)
            }
        val multiPicker =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (::fileChooserDelegate.isInitialized) fileChooserDelegate.onMultipleResult(uris)
            }
        fileChooserDelegate = FileChooserDelegate(singlePicker, multiPicker)

        // Re-point the (possibly rotation-surviving) ViewModel at this Activity
        // and at the freshly registered launchers.
        browserViewModel.attachActivity(this, fileChooserDelegate)

        setContent {
            val themeMode by browserViewModel.themeMode.collectAsStateWithLifecycle()
            AuroraBrowserTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AuroraNavGraph(browserViewModel = browserViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::browserViewModel.isInitialized) browserViewModel.onActivityResumed()
    }

    override fun onPause() {
        if (::browserViewModel.isInitialized) browserViewModel.onActivityPaused()
        super.onPause()
    }

    override fun onDestroy() {
        // Never strand a pending file-chooser callback on a dead Activity.
        if (::fileChooserDelegate.isInitialized) fileChooserDelegate.clear()
        if (::browserViewModel.isInitialized) browserViewModel.onActivityDestroyed()
        super.onDestroy()
    }

    // -- FullscreenHandler -----------------------------------------------------

    override fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            // One fullscreen view at a time; refuse the second cleanly.
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        (window.decorView as ViewGroup).addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        // Immersive mode: hide system bars, reveal on swipe (spec B-3).
        WindowCompat.getInsetsController(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (::browserViewModel.isInitialized) browserViewModel.isFullscreen.value = true
    }

    override fun hideCustomView() {
        val view = customView ?: return
        (window.decorView as ViewGroup).removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        if (::browserViewModel.isInitialized) browserViewModel.isFullscreen.value = false
    }
}
