package com.aurora.browser.viewmodel

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.browser.AuroraApp
import com.aurora.browser.R
import com.aurora.browser.browser.DownloadHandler
import com.aurora.browser.data.db.entities.DownloadRecord
import com.aurora.browser.data.repository.DownloadRepository
import com.aurora.browser.downloads.DownloadTracker
import com.aurora.browser.permissions.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot UI events from [DownloadsViewModel] (snackbars etc.). */
sealed interface DownloadUiEvent {
    data class ShowMessage(val message: String) : DownloadUiEvent
}

/**
 * Backs the Downloads screen. No Hilt: dependencies come from the hand-rolled
 * container exposed by [AuroraApp] (Worker A owns AuroraApp; it must expose
 * `val container` with `val downloadRepository: DownloadRepository`).
 *
 * Cross-worker assumptions (Worker B owns the repository):
 * - `fun observeDownloads(): Flow<List<DownloadRecord>>` (newest first)
 * - `suspend fun deleteById(id: Long)` - deletes the Room row by its primary key
 * - `suspend fun deleteAll()` - deletes all Room rows, never touches user files
 * - plus the update/mark methods documented in DownloadTracker.
 */
class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as AuroraApp).container
    private val downloadRepository: DownloadRepository = container.downloadRepository

    private val tracker = DownloadTracker(application.applicationContext, downloadRepository)

    // Own handler for retries: re-enqueues the original URL with the same
    // cookie/UA plumbing as first-time downloads.
    private val downloadHandler = DownloadHandler(
        context = application.applicationContext,
        downloadRepository = downloadRepository,
        cookieProvider = { url -> CookieManager.getInstance().getCookie(url) },
        userAgentProvider = { WebSettings.getDefaultUserAgent(application) },
        onUnsupported = { message ->
            viewModelScope.launch { _events.emit(DownloadUiEvent.ShowMessage(message)) }
        }
    )

    /** All download records, newest first, observed straight from Room. */
    val downloads: StateFlow<List<DownloadRecord>> =
        downloadRepository.observeDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<DownloadUiEvent>()
    val events: SharedFlow<DownloadUiEvent> = _events.asSharedFlow()

    /**
     * True while a POST_NOTIFICATIONS permission request is in flight (the UI can
     * show a "why we ask" note). Worker A's BrowserViewModel calls
     * [ensureDownloadNotifications] just before the first download.
     */
    private val _notificationRationale = MutableStateFlow(false)
    val notificationRationale: StateFlow<Boolean> = _notificationRationale.asStateFlow()

    // Remembers whether we already asked for POST_NOTIFICATIONS, so "Allow" after
    // a permanent denial ("don't ask again") deep-links to app settings instead of
    // firing a system prompt that can never appear (spec B-17: never loop).
    private var notificationAskedOnce = false

    /** Called from the screen's DisposableEffect - polling stops when it leaves. */
    fun startTracking() = tracker.start(viewModelScope)

    fun stopTracking() = tracker.stop()

    /** Opens a completed download with the system's viewer (ACTION_VIEW). */
    fun openDownload(context: Context, record: DownloadRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = try {
                context.getSystemService(DownloadManager::class.java)
                    .getUriForDownloadedFile(record.systemDownloadId)
            } catch (e: Exception) {
                null
            }
            if (uri == null) {
                _events.emit(
                    DownloadUiEvent.ShowMessage(context.getString(R.string.downloads_file_missing))
                )
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, record.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Non-activity callers need this flag; the screen passes the Activity.
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Spec B-13: resolve before every external intent.
            if (hasHandler(context, intent)) {
                context.startActivity(intent)
            } else {
                _events.emit(
                    DownloadUiEvent.ShowMessage(context.getString(R.string.downloads_no_app_for_type))
                )
            }
        }
    }

    /**
     * Deletes the download. The screen shows the confirm dialog first (file
     * deletion is final - no UNDO); this performs DownloadManager.remove(id)
     * (removes the system record AND the file) plus the Room row.
     */
    fun deleteDownload(record: DownloadRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                app.getSystemService(DownloadManager::class.java).remove(record.systemDownloadId)
            } catch (e: Exception) {
                // Best effort: the file may already be gone; the row must still go.
            }
            downloadRepository.deleteById(record.id)
            _events.emit(
                DownloadUiEvent.ShowMessage(app.getString(R.string.downloads_deleted, record.fileName))
            )
        }
    }

    /** Re-enqueues the original URL (spec B-17). The failed row is replaced by the fresh attempt. */
    fun retryDownload(record: DownloadRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = downloadHandler.enqueueDownload(
                url = record.url,
                contentDisposition = null,
                mimeType = record.mimeType,
                contentLength = -1L // unknown until the server answers
            )
            if (newId != -1L) {
                // The failed attempt is superseded: drop the old row so the list
                // doesn't show a stale "Failed" entry next to the retry.
                downloadRepository.deleteById(record.id)
            }
            // On failure the handler already surfaced an honest message.
        }
    }

    /**
     * Clears the download LIST only (spec B-9 "Clear browsing data"): Room rows
     * are deleted, the user's files in Downloads are never touched.
     */
    fun clearRecords() {
        viewModelScope.launch(Dispatchers.IO) { downloadRepository.deleteAll() }
    }

    /**
     * Requests POST_NOTIFICATIONS once, before the first download (spec B-8/B-14).
     * No-op below API 33 and when already granted. Fire-and-forget: the download
     * itself never waits on this - denial only affects notifications, progress
     * stays visible in the Downloads screen.
     */
    fun ensureDownloadNotifications(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationAskedOnce = true
        _notificationRationale.value = true
        viewModelScope.launch {
            try {
                // PermissionManager is safe to construct lazily (it registers via the
                // ActivityResultRegistry, not registerForActivityResult).
                PermissionManager(activity).requestPermissions(Manifest.permission.POST_NOTIFICATIONS)
            } finally {
                _notificationRationale.value = false
            }
        }
    }

    /**
     * "Allow" action for the notification rationale banner. Respects "don't ask
     * again": after a permanent denial it deep-links to app settings instead of
     * re-firing a prompt that can never appear.
     */
    fun onNotificationBannerAllow(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            notificationAskedOnce &&
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            PermissionManager(activity).openAppSettings()
            _notificationRationale.value = false
            return
        }
        ensureDownloadNotifications(activity)
    }

    fun dismissNotificationRationale() {
        _notificationRationale.value = false
    }

    private fun hasHandler(context: Context, intent: Intent): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                intent, PackageManager.ResolveInfoFlags.of(0)
            ) != null
        } else {
            @Suppress("DEPRECATION")
            intent.resolveActivity(context.packageManager) != null
        }
    }
}
