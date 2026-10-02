@file:OptIn(ExperimentalMaterial3Api::class)

package com.aurora.browser.ui.screens

import android.app.DownloadManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.data.db.entities.DownloadRecord
import com.aurora.browser.viewmodel.DownloadUiEvent
import com.aurora.browser.viewmodel.DownloadsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Downloads screen (spec B-2 item 7). Referenced by Worker A's NavGraph with the
 * exact signature `DownloadsScreen(onNavigateBack: () -> Unit)`.
 *
 * - Header note: "Saved to Downloads folder".
 * - Rows: MIME-based icon, file name, status line, overflow menu (Open/Retry/Delete/Details).
 * - Tapping a completed row opens it via the system viewer.
 * - Delete asks for confirmation (file deletion is final - no UNDO snackbar).
 * - Details dialog shows URL, MIME, sizes, dates.
 * - Progress polling runs only while this screen is visible (DisposableEffect).
 */
@Composable
fun DownloadsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val viewModel: DownloadsViewModel = viewModel()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val showNotifRationale by viewModel.notificationRationale.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    var detailsRecord by remember { mutableStateOf<DownloadRecord?>(null) }

    // Spec B-18: download polling only while the Downloads screen is visible.
    DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose { viewModel.stopTracking() }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.downloads_back_desc)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                text = stringResource(R.string.downloads_saved_to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (showNotifRationale) {
                NotificationRationaleBanner(
                    onAllow = { activity?.let { viewModel.onNotificationBannerAllow(it) } },
                    onDismiss = { viewModel.dismissNotificationRationale() }
                )
            }
            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.downloads_empty),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.downloads_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(downloads, key = { it.id }) { record ->
                        DownloadRow(
                            record = record,
                            onOpen = { viewModel.openDownload(context, record) },
                            onRetry = { viewModel.retryDownload(record) },
                            onDelete = { pendingDelete = record },
                            onDetails = { detailsRecord = record }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.downloads_delete_title)) },
            // Honest note: file deletion is final, so the confirm dialog (not an
            // UNDO snackbar) is the safeguard.
            text = { Text(stringResource(R.string.downloads_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteDownload(record)
                }) { Text(stringResource(R.string.downloads_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.downloads_cancel))
                }
            }
        )
    }

    detailsRecord?.let { record ->
        DownloadDetailsDialog(record = record, onDismiss = { detailsRecord = null })
    }
}

@Composable
private fun NotificationRationaleBanner(onAllow: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.downloads_notifications_banner),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.downloads_notifications_allow))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_notifications_dismiss))
            }
        }
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val isComplete = record.status == DownloadRecord.STATUS_COMPLETE
    val isRunning = record.status == DownloadRecord.STATUS_RUNNING ||
        record.status == DownloadRecord.STATUS_QUEUED

    // The failure reason is intentionally stored nowhere in Room; map the live
    // DownloadManager reason code to text here when rendering a failed row.
    val failureReason = if (record.status == DownloadRecord.STATUS_FAILED) {
        remember(record.systemDownloadId) { failureReasonText(context, record.systemDownloadId) }
    } else {
        null
    }

    ListItem(
        headlineContent = {
            Text(
                text = record.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = statusText(record, failureReason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isRunning) {
                    if (record.totalBytes > 0) {
                        LinearProgressIndicator(
                            // Lambda overload (material3 1.3.x): the Float overload is deprecated.
                            progress = { progressFraction(record) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Total size unknown yet: indeterminate bar.
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = mimeIcon(record.mimeType),
                contentDescription = stringResource(R.string.downloads_file_icon_desc),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(
                            R.string.downloads_overflow_desc, record.fileName
                        )
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (isComplete) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_open)) },
                            onClick = { showMenu = false; onOpen() }
                        )
                    }
                    if (record.status == DownloadRecord.STATUS_FAILED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_retry)) },
                            onClick = { showMenu = false; onRetry() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_details)) },
                        onClick = { showMenu = false; onDetails() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_delete)) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        },
        // Tapping a completed row opens it (spec B-2); other states use the menu.
        modifier = Modifier.clickable(enabled = isComplete, onClick = onOpen)
    )
}

@Composable
private fun DownloadDetailsDialog(record: DownloadRecord, onDismiss: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }
    val na = stringResource(R.string.downloads_not_available)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_details_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(
                    stringResource(R.string.downloads_details_file), record.fileName
                )
                DetailRow(
                    stringResource(R.string.downloads_details_url), record.url
                )
                DetailRow(
                    stringResource(R.string.downloads_details_mime), record.mimeType ?: na
                )
                DetailRow(
                    stringResource(R.string.downloads_details_status), record.status
                )
                DetailRow(
                    stringResource(R.string.downloads_details_downloaded),
                    stringResource(
                        R.string.downloads_details_bytes,
                        formatBytes(record.downloadedBytes),
                        if (record.totalBytes > 0) formatBytes(record.totalBytes) else na
                    )
                )
                DetailRow(
                    stringResource(R.string.downloads_details_started),
                    dateFormat.format(Date(record.startedAt))
                )
                DetailRow(
                    stringResource(R.string.downloads_details_completed),
                    record.completedAt?.let { dateFormat.format(Date(it)) } ?: na
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.downloads_close)) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Status line per spec B-2: "12.4 MB of 48 MB · 26%", "Complete · 48 MB", "Failed — <reason>". */
@Composable
private fun statusText(record: DownloadRecord, failureReason: String?): String {
    return when (record.status) {
        DownloadRecord.STATUS_RUNNING -> if (record.totalBytes > 0) {
            val pct = (record.downloadedBytes * 100 / record.totalBytes).toInt().coerceIn(0, 100)
            stringResource(
                R.string.downloads_status_running,
                formatBytes(record.downloadedBytes),
                formatBytes(record.totalBytes),
                pct
            )
        } else {
            stringResource(
                R.string.downloads_status_running_unknown_total,
                formatBytes(record.downloadedBytes)
            )
        }
        DownloadRecord.STATUS_QUEUED -> stringResource(R.string.downloads_status_queued)
        DownloadRecord.STATUS_COMPLETE -> stringResource(
            R.string.downloads_status_complete,
            formatBytes(if (record.totalBytes > 0) record.totalBytes else record.downloadedBytes)
        )
        else -> stringResource(
            R.string.downloads_status_failed,
            failureReason ?: stringResource(R.string.downloads_error_unknown)
        )
    }
}

private fun progressFraction(record: DownloadRecord): Float {
    if (record.totalBytes <= 0) return 0f
    return (record.downloadedBytes.toFloat() / record.totalBytes.toFloat()).coerceIn(0f, 1f)
}

/** Maps DownloadManager failure reason codes to plain user text (spec B-17). */
private fun failureReasonText(context: Context, systemDownloadId: Long): String {
    fun res(id: Int) = context.getString(id)
    return try {
        val dm = context.getSystemService(DownloadManager::class.java)
            ?: return res(R.string.downloads_error_unknown)
        dm.query(DownloadManager.Query().setFilterById(systemDownloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return res(R.string.downloads_error_unknown)
            val reason =
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            res(
                when (reason) {
                    DownloadManager.ERROR_INSUFFICIENT_SPACE -> R.string.downloads_error_no_space
                    DownloadManager.ERROR_CANNOT_RESUME,
                    DownloadManager.ERROR_HTTP_DATA_ERROR -> R.string.downloads_error_network
                    DownloadManager.ERROR_FILE_ERROR -> R.string.downloads_error_storage
                    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> R.string.downloads_error_server
                    DownloadManager.ERROR_BLOCKED -> R.string.downloads_error_blocked
                    else -> R.string.downloads_error_unknown
                }
            )
        }
    } catch (e: Exception) {
        res(R.string.downloads_error_unknown)
    }
}

/**
 * Picks a Material icon by MIME type. Only icons from material-icons-core are
 * used so this compiles without the extended icons artifact.
 */
private fun mimeIcon(mimeType: String?): ImageVector {
    if (mimeType == null) return Icons.Filled.InsertDriveFile
    return when {
        mimeType.startsWith("image/") -> Icons.Filled.Image
        mimeType.startsWith("video/") -> Icons.Filled.Movie
        mimeType.startsWith("audio/") -> Icons.Filled.Audiotrack
        mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf
        mimeType.startsWith("text/") -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }
}

/** Human-readable byte counts: 512 B, 12.4 MB, 1.2 GB. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}"
    else "%.1f %s".format(Locale.US, value, units[unit])
}
