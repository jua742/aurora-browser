package com.aurora.browser.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.aurora.browser.R
import com.aurora.browser.data.db.entities.DownloadRecord
import com.aurora.browser.data.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Starts real file downloads through the system DownloadManager.
 *
 * Why DownloadManager (spec B-8 / B-14): it writes into the public Downloads
 * collection itself, so the app never needs READ/WRITE_EXTERNAL_STORAGE.
 * No backend, no custom networking - 100% on-device.
 *
 * Honest limitation (spec A, B-3): blob: URLs cannot be fetched by
 * DownloadManager. [isSupportedUrl] rejects them and [onUnsupported] tells the
 * user plainly ("This download type isn't supported yet") instead of faking a
 * success or failing silently.
 *
 * Wiring: BrowserViewModel (Worker A) calls [enqueueDownload] from
 * WebView.setDownloadListener { url, _, contentDisposition, mimeType, _ -> ... }.
 *
 * Cross-worker references (Worker B owns these, now confirmed on disk):
 * - com.aurora.browser.data.db.entities.DownloadRecord with fields
 *   (id, systemDownloadId, url, fileName, mimeType, totalBytes, downloadedBytes,
 *   status, startedAt, completedAt) per spec B-11.
 * - com.aurora.browser.data.repository.DownloadRepository with
 *   `suspend fun insert(record: DownloadRecord): Long`.
 */
class DownloadHandler(
    private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val cookieProvider: (String) -> String?,
    private val userAgentProvider: () -> String,
    private val onUnsupported: (String) -> Unit
) {
    companion object {
        /** Schemes the download listener can hand us but DownloadManager cannot fetch. */
        private val UNSUPPORTED_SCHEMES = setOf("blob", "data", "about", "javascript")
    }

    /** False for blob:, data:, about:, javascript: and anything that isn't http(s). */
    fun isSupportedUrl(url: String): Boolean {
        val scheme = url.substringBefore(":").lowercase()
        if (scheme in UNSUPPORTED_SCHEMES) return false
        return scheme == "http" || scheme == "https"
    }

    /**
     * Enqueues the download and records it in Room.
     * @return the system download id, or -1 when the URL isn't supported or the
     * enqueue failed (in both cases [onUnsupported] was already called with an
     * honest message for the user).
     */
    suspend fun enqueueDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ): Long {
        if (!isSupportedUrl(url)) {
            // Exact user-visible text required by the contract: honest, never silent.
            notifyUnsupported(context.getString(R.string.downloads_unsupported))
            return -1L
        }
        // CookieManager / WebSettings touch WebView internals, so the providers
        // are invoked on the main thread even when we were called from a worker.
        val cookie = withContext(Dispatchers.Main) { cookieProvider(url) }
        val userAgent = withContext(Dispatchers.Main) { userAgentProvider() }
        return withContext(Dispatchers.IO) {
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription(url)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    // Deprecated on API 29+ but still the functional way to target the
                    // public Downloads dir through DownloadManager (spec B-8).
                    @Suppress("DEPRECATION")
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    @Suppress("DEPRECATION")
                    setAllowedOverMetered(true)
                    if (mimeType != null) setMimeType(mimeType)
                    // Authenticated downloads need the site's cookies + UA, otherwise
                    // the server may answer with a login page instead of the file.
                    if (!cookie.isNullOrEmpty()) addRequestHeader("Cookie", cookie)
                    addRequestHeader("User-Agent", userAgent)
                }
                val dm = context.getSystemService(DownloadManager::class.java)
                val systemId = dm.enqueue(request)
                downloadRepository.insert(
                    DownloadRecord(
                        systemDownloadId = systemId,
                        url = url,
                        fileName = fileName,
                        mimeType = mimeType,
                        totalBytes = contentLength,
                        downloadedBytes = 0L,
                        status = DownloadRecord.STATUS_RUNNING,
                        startedAt = System.currentTimeMillis(),
                        completedAt = null
                    )
                )
                systemId
            } catch (e: Exception) {
                // Never fake success: surface the real reason and report -1.
                notifyUnsupported(e.message ?: context.getString(R.string.downloads_start_failed))
                -1L
            }
        }
    }

    /** [onUnsupported] shows UI, so it is always delivered on the main thread. */
    private suspend fun notifyUnsupported(message: String) {
        withContext(Dispatchers.Main) { onUnsupported(message) }
    }
}
