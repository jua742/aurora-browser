package com.aurora.browser.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import coil.compose.AsyncImage
import com.aurora.browser.R

/**
 * About: app icon and version (from the installed package), the System WebView
 * version (the actual rendering engine), open-source licenses, and the privacy note.
 */
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    // The real launcher icon, so this screen never depends on another worker's drawables.
    val appIcon = remember {
        try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (e: Exception) {
            null
        }
    }
    // App version, read from the installed package (no BuildConfig dependency).
    val (versionName, versionCode) = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            (info.versionName ?: "?") to code
        } catch (e: PackageManager.NameNotFoundException) {
            "?" to 0L
        }
    }
    // The Chromium version actually rendering pages on this device.
    val webViewVersion = remember {
        try {
            WebViewCompat.getCurrentWebViewPackage(context)?.versionName
        } catch (e: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_about_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            if (appIcon != null) {
                AsyncImage(
                    model = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.content_about_app_name),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.content_about_version,
                    versionName,
                    versionCode
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.content_about_webview),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (webViewVersion != null) {
                    stringResource(R.string.content_about_webview_version, webViewVersion)
                } else {
                    stringResource(R.string.content_about_webview_unknown)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            // Open-source notices (static v1 list — every dependency below is
            // Apache License 2.0; the WebView is a system component).
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.content_about_licenses),
                        style = MaterialTheme.typography.titleSmall
                    )
                    LicenseRow("Kotlin", "Apache License 2.0")
                    LicenseRow("Jetpack Compose (Material 3)", "Apache License 2.0")
                    LicenseRow("AndroidX Room", "Apache License 2.0")
                    LicenseRow("AndroidX DataStore", "Apache License 2.0")
                    LicenseRow("AndroidX WebKit", "Apache License 2.0")
                    LicenseRow("Coil", "Apache License 2.0")
                    LicenseRow(
                        "Android System WebView",
                        "System component (Chromium)"
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.content_about_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/** One row in the static open-source notices list. */
@Composable
private fun LicenseRow(name: String, license: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
