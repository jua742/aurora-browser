package com.aurora.browser.ui.screens

import android.app.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RowScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.viewmodel.SettingsViewModel

// ---------------------------------------------------------------------------
// Settings sub-screen routes.
//
// These exact strings come from spec B-1. Worker A's NavGraph must register a
// destination for each one and render the matching screen from this package.
// ---------------------------------------------------------------------------
const val ROUTE_SETTINGS_SEARCH = "settings/search"
const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"
const val ROUTE_SETTINGS_PRIVACY = "settings/privacy"
const val ROUTE_SETTINGS_PERMISSIONS = "settings/permissions"
const val ROUTE_SETTINGS_DOWNLOADS = "settings/downloads"
const val ROUTE_SETTINGS_ABOUT = "settings/about"

// ---------------------------------------------------------------------------
// Search-engine ids. Must match the ids used by the browser layer's
// SearchEngines model (Worker A): the default "google" is the DataStore default.
// ---------------------------------------------------------------------------
internal const val SEARCH_ENGINE_GOOGLE = "google"
internal const val SEARCH_ENGINE_BING = "bing"
internal const val SEARCH_ENGINE_DUCKDUCKGO = "duckduckgo"
internal const val SEARCH_ENGINE_YAHOO = "yahoo"
internal const val SEARCH_ENGINE_CUSTOM = "custom"

internal fun searchEngineNameRes(id: String): Int = when (id) {
    SEARCH_ENGINE_BING -> R.string.content_search_engine_bing
    SEARCH_ENGINE_DUCKDUCKGO -> R.string.content_search_engine_duckduckgo
    SEARCH_ENGINE_YAHOO -> R.string.content_search_engine_yahoo
    SEARCH_ENGINE_CUSTOM -> R.string.content_search_engine_custom
    else -> R.string.content_search_engine_google
}

internal fun themeModeNameRes(mode: String): Int = when (mode) {
    "light" -> R.string.content_appearance_theme_light
    "dark" -> R.string.content_appearance_theme_dark
    else -> R.string.content_appearance_theme_system
}

internal fun homepageModeNameRes(mode: String): Int = when (mode) {
    "blank" -> R.string.content_settings_homepage_mode_blank
    "last_tabs" -> R.string.content_settings_homepage_mode_last_tabs
    else -> R.string.content_settings_homepage_mode_home
}

// ---------------------------------------------------------------------------
// Shared settings UI pieces (internal: reused by the sub-screens in this package).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_navigate_back)
                )
            }
        },
        actions = actions
    )
}

@Composable
internal fun SectionHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** A settings row with an optional caption line underneath the title. Null onClick = informational, not clickable. */
@Composable
internal fun SettingsRow(title: String, supporting: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun RadioRow(
    title: String,
    supporting: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    supporting: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// The settings hub.
// ---------------------------------------------------------------------------

/**
 * Grouped settings list. Tapping a row navigates to a sub-screen via
 * [onNavigateTo] with one of the ROUTE_SETTINGS_* constants above.
 *
 * The homepage controls live directly in the General section (there is no
 * separate homepage screen in the spec's route list, but homepage_mode and
 * homepage_url are B-10 settings that must be editable).
 */
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit, onNavigateTo: (String) -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val searchEngineId by viewModel.searchEngineId.collectAsState()
    val homepageMode by viewModel.homepageMode.collectAsState()
    val homepageUrl by viewModel.homepageUrl.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val textZoomPercent by viewModel.textZoomPercent.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_settings_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item { SectionHeader(R.string.content_settings_general) }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_settings_search_engine),
                    supporting = stringResource(searchEngineNameRes(searchEngineId)),
                    onClick = { onNavigateTo(ROUTE_SETTINGS_SEARCH) }
                )
            }
            item {
                HomepageModeRow(
                    currentMode = homepageMode,
                    onModeSelected = viewModel::setHomepageMode
                )
            }
            if (homepageMode == "home") {
                item {
                    HomepageUrlField(
                        currentUrl = homepageUrl,
                        onUrlConfirmed = viewModel::setHomepageUrl
                    )
                }
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_settings_default_browser),
                    supporting = stringResource(R.string.content_settings_default_browser_note),
                    onClick = { openDefaultAppsSettings(context) }
                )
            }

            item { SectionHeader(R.string.content_settings_appearance) }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_settings_theme_display),
                    supporting = stringResource(
                        R.string.content_settings_theme_display_summary,
                        stringResource(themeModeNameRes(themeMode)),
                        textZoomPercent
                    ),
                    onClick = { onNavigateTo(ROUTE_SETTINGS_APPEARANCE) }
                )
            }

            item { SectionHeader(R.string.content_settings_privacy) }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_settings_privacy_clearing),
                    onClick = { onNavigateTo(ROUTE_SETTINGS_PRIVACY) }
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_settings_site_permissions),
                    onClick = { onNavigateTo(ROUTE_SETTINGS_PERMISSIONS) }
                )
            }

            item { SectionHeader(R.string.content_settings_downloads) }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_settings_downloads),
                    onClick = { onNavigateTo(ROUTE_SETTINGS_DOWNLOADS) }
                )
            }

            item { SectionHeader(R.string.content_settings_about) }
            item {
                SettingsRow(
                    title = stringResource(R.string.content_settings_about),
                    onClick = { onNavigateTo(ROUTE_SETTINGS_ABOUT) }
                )
            }
        }
    }
}

@Composable
private fun HomepageModeRow(currentMode: String, onModeSelected: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.content_settings_homepage),
            style = MaterialTheme.typography.bodyLarge
        )
        Row {
            listOf("home", "blank", "last_tabs").forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = { onModeSelected(mode) })
                        .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    RadioButton(selected = currentMode == mode, onClick = { onModeSelected(mode) })
                    Text(
                        text = stringResource(homepageModeNameRes(mode)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Homepage URL editor. The value is persisted when the user confirms (keyboard
 * Done); invalid input shows an error instead of being saved. The browser's
 * UrlResolver does the final validation before loading.
 */
@Composable
private fun HomepageUrlField(currentUrl: String, onUrlConfirmed: (String) -> Unit) {
    var draft by remember(currentUrl) { mutableStateOf(currentUrl) }
    var showError by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it; showError = false },
        label = { Text(stringResource(R.string.content_settings_homepage_url_hint)) },
        singleLine = true,
        isError = showError,
        supportingText = {
            if (showError) Text(stringResource(R.string.content_settings_homepage_url_error))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (isValidHomepageUrl(draft)) onUrlConfirmed(draft) else showError = true
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

private fun isValidHomepageUrl(url: String): Boolean {
    val clean = url.trim()
    return clean.isNotEmpty() && !clean.any { it.isWhitespace() }
}

/** Opens the system "default apps" screen; not all devices have it, so failures are ignored. */
private fun openDefaultAppsSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        // Nothing to do: the device simply has no default-apps screen.
    }
}
