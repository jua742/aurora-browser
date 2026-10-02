package com.aurora.browser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurora.browser.ui.screens.AboutScreen
import com.aurora.browser.ui.screens.AppearanceSettingsScreen
import com.aurora.browser.ui.screens.BookmarksScreen
import com.aurora.browser.ui.screens.BrowserScreen
import com.aurora.browser.ui.screens.DownloadSettingsScreen
import com.aurora.browser.ui.screens.DownloadsScreen
import com.aurora.browser.ui.screens.HistoryScreen
import com.aurora.browser.ui.screens.PrivacySettingsScreen
import com.aurora.browser.ui.screens.SearchSettingsScreen
import com.aurora.browser.ui.screens.SettingsScreen
import com.aurora.browser.ui.screens.SitePermissionsScreen
import com.aurora.browser.ui.screens.SplashScreen
import com.aurora.browser.ui.screens.TabsScreen
import com.aurora.browser.viewmodel.BrowserViewModel
import com.aurora.browser.viewmodel.SettingsViewModel

/**
 * The app's navigation graph (spec B-1): splash -> browser (hub) -> everything else.
 *
 * The browser screen owns the WebView; bookmark/history rows open their URL in the
 * CURRENT tab and pop back to the browser route.
 */
@Composable
fun AuroraNavGraph(
    browserViewModel: BrowserViewModel,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                viewModel = browserViewModel,
                onDone = {
                    navController.navigate(Routes.BROWSER) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.BROWSER) {
            BrowserScreen(viewModel = browserViewModel, navController = navController)
        }
        composable(Routes.TABS) {
            TabsScreen(
                browserViewModel = browserViewModel,
                onBackToBrowser = { navController.popBackStack() },
            )
        }
        composable(Routes.BOOKMARKS) {
            BookmarksScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenUrl = { url -> openUrlInBrowser(navController, browserViewModel, url) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenUrl = { url -> openUrlInBrowser(navController, browserViewModel, url) },
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateTo = { route -> navController.navigate(route) },
            )
        }
        composable(Routes.SETTINGS_SEARCH) {
            SearchSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PRIVACY) {
            // Wire the privacy screen's clear-data events to the browser layer's
            // wipe. viewModel() here resolves to the SAME SettingsViewModel instance
            // the screen uses (both are scoped to this destination's back stack
            // entry), and the category ids match BrowserViewModel.ClearDataCategories.
            val settingsViewModel: SettingsViewModel = viewModel()
            LaunchedEffect(settingsViewModel) {
                settingsViewModel.clearDataEvents.collect { categories ->
                    browserViewModel.clearBrowsingData(categories)
                }
            }
            PrivacySettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PERMISSIONS) {
            SitePermissionsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_DOWNLOADS) {
            DownloadSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

/** Load a URL in the current tab and return to the browser screen. */
private fun openUrlInBrowser(
    navController: NavHostController,
    browserViewModel: BrowserViewModel,
    url: String,
) {
    browserViewModel.loadInput(url)
    navController.popBackStack(Routes.BROWSER, inclusive = false)
}
