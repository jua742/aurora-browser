package com.aurora.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Privacy: one-tap "Clear browsing data" (checklist dialog), clear-on-exit,
 * web-content toggles, and the honest incognito explainer.
 *
 * The actual wipe is performed by the browser layer: confirming the dialog
 * calls SettingsViewModel.requestClearData(), which emits the chosen category
 * ids on SettingsViewModel.clearDataEvents for the BrowserViewModel to collect.
 */
@Composable
fun PrivacySettingsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val clearOnExit by viewModel.clearOnExit.collectAsState()
    val clearOnExitCategories by viewModel.clearOnExitCategories.collectAsState()
    val javaScriptEnabled by viewModel.javaScriptEnabled.collectAsState()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsState()
    val desktopModeDefault by viewModel.desktopModeDefault.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearingMessage = stringResource(R.string.content_privacy_clearing)
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_privacy_title),
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.content_privacy_clear_data))
                }
            }

            item { SectionHeader(R.string.content_privacy_clear_on_exit) }
            item {
                SwitchRow(
                    title = stringResource(R.string.content_privacy_clear_on_exit),
                    supporting = stringResource(R.string.content_privacy_clear_on_exit_note),
                    checked = clearOnExit,
                    onCheckedChange = viewModel::setClearOnExit
                )
            }
            if (clearOnExit) {
                item {
                    ClearCategoryCheckboxes(
                        checked = clearOnExitCategories,
                        onCheckedChange = viewModel::setClearOnExitCategories
                    )
                }
            }

            item { SectionHeader(R.string.content_privacy_web_content) }
            item {
                SwitchRow(
                    title = stringResource(R.string.content_privacy_javascript),
                    checked = javaScriptEnabled,
                    onCheckedChange = viewModel::setJavaScriptEnabled
                )
            }
            if (!javaScriptEnabled) {
                item {
                    Text(
                        text = stringResource(R.string.content_privacy_javascript_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.content_privacy_block_third_party),
                    checked = blockThirdPartyCookies,
                    onCheckedChange = viewModel::setBlockThirdPartyCookies
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.content_privacy_desktop_mode),
                    checked = desktopModeDefault,
                    onCheckedChange = viewModel::setDesktopModeDefault
                )
            }

            item { SectionHeader(R.string.content_privacy_incognito_title) }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Honest wording required by the spec: incognito is local-only
                        // privacy, never anonymity.
                        Text(
                            text = stringResource(R.string.content_privacy_incognito_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.content_privacy_incognito_downloads),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ClearDataDialog(
            onDismiss = { showClearDialog = false },
            onConfirm = { categories ->
                showClearDialog = false
                viewModel.requestClearData(categories)
                scope.launch { snackbarHostState.showSnackbar(clearingMessage) }
            }
        )
    }
}

@Composable
private fun ClearCategoryCheckboxes(
    checked: Set<String>,
    onCheckedChange: (Set<String>) -> Unit
) {
    val categories = listOf(
        SettingsViewModel.CLEAR_HISTORY to R.string.content_privacy_clear_history,
        SettingsViewModel.CLEAR_COOKIES to R.string.content_privacy_clear_cookies,
        SettingsViewModel.CLEAR_CACHE to R.string.content_privacy_clear_cache,
        SettingsViewModel.CLEAR_DOWNLOADS to R.string.content_privacy_clear_downloads,
        SettingsViewModel.CLEAR_PERMISSIONS to R.string.content_privacy_clear_permissions
    )
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        categories.forEach { (id, labelRes) ->
            val isChecked = id in checked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        onCheckedChange(
                            if (isChecked) checked - id else checked + id
                        )
                    })
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { nowChecked ->
                        onCheckedChange(
                            if (nowChecked) checked + id else checked - id
                        )
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (id == SettingsViewModel.CLEAR_DOWNLOADS) {
                        Text(
                            text = stringResource(R.string.content_privacy_clear_downloads_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one-tap "Clear browsing data" checklist. Confirming hands the selected
 * category ids to the ViewModel; the browser layer performs the wipe.
 */
@Composable
private fun ClearDataDialog(onDismiss: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    // Pre-select every category, matching the spec's one-tap flow.
    val checked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            SettingsViewModel.ALL_CLEAR_CATEGORIES.forEach { put(it, true) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_privacy_clear_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val labels = mapOf(
                    SettingsViewModel.CLEAR_HISTORY to R.string.content_privacy_clear_history,
                    SettingsViewModel.CLEAR_COOKIES to R.string.content_privacy_clear_cookies,
                    SettingsViewModel.CLEAR_CACHE to R.string.content_privacy_clear_cache,
                    SettingsViewModel.CLEAR_DOWNLOADS to R.string.content_privacy_clear_downloads,
                    SettingsViewModel.CLEAR_PERMISSIONS to R.string.content_privacy_clear_permissions
                )
                labels.forEach { (id, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { checked[id] = !(checked[id] ?: false) })
                    ) {
                        Checkbox(
                            checked = checked[id] ?: false,
                            onCheckedChange = { checked[id] = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(checked.filterValues { it }.keys)
            }) {
                Text(stringResource(R.string.content_common_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.content_common_cancel))
            }
        }
    )
}
