package com.aurora.browser.ui.screens

import android.app.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aurora.browser.R

/**
 * Download settings. There is deliberately little to configure: with the
 * system DownloadManager the location is fixed to the public Downloads folder
 * (no storage permission needed), and notification control lives in the
 * system settings — this screen just deep-links there.
 */
@Composable
fun DownloadSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_download_settings_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                // Informational row: the location is fixed by the system DownloadManager,
                // so this row is deliberately not clickable.
                SettingsRow(
                    title = stringResource(R.string.content_download_location),
                    supporting = stringResource(R.string.content_download_location_value)
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_download_notifications),
                    supporting = stringResource(R.string.content_download_notifications_note),
                    onClick = { openAppNotificationSettings(context) }
                )
            }
        }
    }
}

/**
 * Opens this app's page in the system notification settings. Falls back to the
 * app-details page on devices without the notification-settings screen.
 */
private fun openAppNotificationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        val fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        try {
            context.startActivity(fallback)
        } catch (ignored: ActivityNotFoundException) {
            // Last resort: stay where we are rather than crashing.
        }
    }
}
