package com.aurora.browser.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.viewmodel.SettingsViewModel
import com.aurora.browser.viewmodel.SitePermissionUi

/**
 * Per-origin site permissions (camera / microphone / location).
 *
 * Shows the union of the Room permission table and the DataStore mirror, so
 * choices remembered by the browser's permission prompts appear here too.
 * Toggling writes to both stores; deleting an origin forgets all its choices.
 */
@Composable
fun SitePermissionsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val permissions by viewModel.sitePermissions.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }

    // Group rows by site for a compact per-origin card layout.
    val byOrigin = remember(permissions) { permissions.groupBy { it.origin } }

    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_site_permissions_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = permissions.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.content_site_permissions_clear_all)
                        )
                    }
                }
            )
        }
    ) { padding ->
        // The scaffold padding wraps both the list and the empty state.
        Column(modifier = Modifier.padding(padding)) {
            if (permissions.isEmpty()) {
                EmptyState(text = stringResource(R.string.content_site_permissions_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    byOrigin.forEach { (origin, rows) ->
                        item(key = origin) {
                            OriginPermissionCard(
                                origin = origin,
                                rows = rows,
                                onToggle = { row, allowed ->
                                    viewModel.setSitePermission(origin, row.permission, allowed)
                                },
                                onDeleteOrigin = { viewModel.deleteSitePermissionsForOrigin(origin) },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.content_site_permissions_clear_all)) },
            text = { Text(stringResource(R.string.content_site_permissions_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearAllSitePermissions()
                }) {
                    Text(stringResource(R.string.content_common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.content_common_cancel))
                }
            }
        )
    }
}

@Composable
private fun OriginPermissionCard(
    origin: String,
    rows: List<SitePermissionUi>,
    onToggle: (SitePermissionUi, Boolean) -> Unit,
    onDeleteOrigin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = origin,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDeleteOrigin) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.content_site_permissions_delete_origin)
                    )
                }
            }
            rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(permissionLabelRes(row.permission)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(
                            if (row.allowed) R.string.content_site_permissions_allow
                            else R.string.content_site_permissions_block
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = row.allowed,
                        onCheckedChange = { onToggle(row, it) }
                    )
                }
            }
        }
    }
}

private fun permissionLabelRes(permission: String): Int = when (permission) {
    "CAMERA" -> R.string.content_site_permissions_camera
    "MIC" -> R.string.content_site_permissions_microphone
    "LOCATION" -> R.string.content_site_permissions_location
    else -> R.string.content_site_permissions_unknown
}
