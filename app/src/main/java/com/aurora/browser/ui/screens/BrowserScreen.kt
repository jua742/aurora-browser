package com.aurora.browser.ui.screens

import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aurora.browser.R
import com.aurora.browser.navigation.Routes
import com.aurora.browser.ui.components.AddressBarEditor
import com.aurora.browser.ui.components.BrowserToolbar
import com.aurora.browser.ui.components.FindInPageBar
import com.aurora.browser.ui.components.OfflineBanner
import com.aurora.browser.viewmodel.BrowserViewModel

/**
 * The main browser screen (spec B-2): 56dp toolbar, 3dp progress bar, the
 * ACTIVE tab's WebView (via AndroidView), find-in-page overlay, offline banner,
 * overflow menu and the geolocation permission dialog.
 *
 * Only the active tab's WebView is ever attached here — background tabs stay
 * alive inside TabManager, detached. The AndroidView's update block re-binds
 * on every recomposition, so tab switches re-attach the right WebView.
 *
 * Back order (spec B-1): menu -> fullscreen -> find bar -> WebView history.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val currentTitle by viewModel.currentTitle.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    val desktopMode by viewModel.desktopModeActive.collectAsStateWithLifecycle()
    val findState by viewModel.findState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val geoRequest by viewModel.geolocationRequest.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    val activeTab = tabs.find { it.id == activeTabId }
    var menuExpanded by remember { mutableStateOf(false) }
    var addressEditing by remember { mutableStateOf(false) }

    // One-shot toasts from the ViewModel (blocked links, tab-cap notice, ...).
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Leaving the tab (or the screen) always exits edit mode.
    LaunchedEffect(activeTabId) {
        addressEditing = false
    }

    // Back order: the LAST composed enabled handler wins. The address editor
    // has its own BackHandler (composed later, inside the toolbar), so it
    // dismisses first; then menu -> fullscreen -> find -> WebView back.
    BackHandler(enabled = canGoBack && !addressEditing && !isFullscreen) {
        viewModel.goBack()
    }
    BackHandler(enabled = findState != null && !addressEditing && !isFullscreen) {
        viewModel.clearFind()
    }
    BackHandler(enabled = isFullscreen) {
        viewModel.exitFullscreen()
    }
    BackHandler(enabled = menuExpanded) {
        menuExpanded = false
    }

    val displayText = currentTitle?.takeIf { it.isNotBlank() }
        ?: activeTab?.host
        ?: stringResource(R.string.search_hint)
    val isSecure = (currentUrl ?: "").startsWith("https://")

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    if (addressEditing) {
                        AddressBarEditor(
                            initialText = currentUrl ?: "",
                            suggestions = suggestions,
                            onQueryChange = viewModel::setSuggestionQuery,
                            onSubmit = { text ->
                                viewModel.loadInput(text)
                                addressEditing = false
                                focusManager.clearFocus(force = true)
                            },
                            onDismiss = {
                                addressEditing = false
                                focusManager.clearFocus(force = true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    } else {
                        BrowserToolbar(
                            displayText = displayText,
                            isSecure = isSecure,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            isLoading = isLoading,
                            bookmarked = bookmarked,
                            tabCount = tabs.size,
                            onBack = viewModel::goBack,
                            onForward = viewModel::goForward,
                            onAddressClick = { addressEditing = true },
                            onReload = viewModel::reload,
                            onStop = viewModel::stop,
                            onToggleBookmark = viewModel::toggleBookmark,
                            onTabsClick = { navController.navigate(Routes.TABS) },
                            onMenuClick = { menuExpanded = true },
                        )
                    }
                    // 3dp progress bar, visible only mid-load (spec B-2).
                    if (progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                        )
                    }
                    if (isOffline) OfflineBanner()
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Fresh tabs (and about:blank) show the native home screen;
                // everything else shows the active tab's WebView.
                if (activeTab?.hasLoadedPage == true || currentUrl != null) {
                    AndroidView(
                        factory = { ctx -> FrameLayout(ctx) },
                        update = { container ->
                            // Re-bind ONLY when the hosted view isn't the active tab's
                            // WebView (e.g. after a tab switch). update runs on every
                            // recomposition — blindly re-attaching here would detach
                            // and re-attach the WebView constantly (flicker, lost
                            // scroll state, interrupted loads).
                            val active = viewModel.tabManager.activeTab()?.webView
                            if (container.childCount != 1 || container.getChildAt(0) !== active) {
                                container.removeAllViews()
                                viewModel.attachActiveWebView(container)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenUrl = { viewModel.loadInput(it) },
                    )
                }

                // Find-in-page bar docked above the keyboard.
                findState?.let { state ->
                    FindInPageBar(
                        state = state,
                        onQueryChange = viewModel::findInPage,
                        onNext = viewModel::findNext,
                        onPrevious = viewModel::findPrevious,
                        onClose = viewModel::clearFind,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .imePadding(),
                    )
                }
            }
        }

        // Overflow menu, anchored top-end under the toolbar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_new_tab)) },
                    onClick = { menuExpanded = false; viewModel.newTab(false) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_new_incognito_tab)) },
                    onClick = { menuExpanded = false; viewModel.newTab(true) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_home)) },
                    onClick = { menuExpanded = false; viewModel.showHomeTab() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_bookmark)) },
                    onClick = { menuExpanded = false; viewModel.toggleBookmark() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_share)) },
                    onClick = { menuExpanded = false; viewModel.sharePage() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_find_in_page)) },
                    onClick = { menuExpanded = false; viewModel.showFindBar() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_desktop_site)) },
                    trailingIcon = {
                        if (desktopMode) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = { menuExpanded = false; viewModel.toggleDesktopMode() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_bookmarks)) },
                    onClick = { menuExpanded = false; navController.navigate(Routes.BOOKMARKS) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_history)) },
                    onClick = { menuExpanded = false; navController.navigate(Routes.HISTORY) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_downloads)) },
                    onClick = { menuExpanded = false; navController.navigate(Routes.DOWNLOADS) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_settings)) },
                    onClick = { menuExpanded = false; navController.navigate(Routes.SETTINGS) },
                )
            }
        }

        // Per-origin geolocation dialog: Allow once / Always allow / Block (spec B-3).
        geoRequest?.let { req ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissGeolocation() },
                title = { Text(stringResource(R.string.geo_title, req.origin)) },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.respondToGeolocation(allow = true, remember = false) },
                    ) {
                        Text(stringResource(R.string.geo_allow_once))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                viewModel.respondToGeolocation(allow = false, remember = true)
                            },
                        ) {
                            Text(stringResource(R.string.geo_block))
                        }
                        TextButton(
                            onClick = {
                                viewModel.respondToGeolocation(allow = true, remember = true)
                            },
                        ) {
                            Text(stringResource(R.string.geo_allow_always))
                        }
                    }
                },
            )
        }
    }
}
