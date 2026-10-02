package com.aurora.browser.browser

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.ActivityResultLauncher

/**
 * Bridges WebChromeClient.onShowFileChooser to the ActivityResultLaunchers
 * registered in MainActivity (spec B-3).
 *
 * Honors the page's accept types (MIME types or file extensions like ".pdf")
 * and the multiple-selection flag. A cancelled picker reports null back to the
 * page, and any stranded callback is cancelled before a new one starts — a
 * leaked ValueCallback would hang the page's file input forever.
 */
class FileChooserDelegate(
    private val singlePicker: ActivityResultLauncher<String>,
    private val multiPicker: ActivityResultLauncher<Array<String>>,
) {

    private var pendingCallback: ValueCallback<Array<Uri>>? = null

    /** Called from AuroraWebChromeClient.onShowFileChooser (always the UI thread). */
    fun launch(params: WebChromeClient.FileChooserParams, callback: ValueCallback<Array<Uri>>) {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = callback

        val mimeTypes = params.acceptTypes
            .filter { it.isNotBlank() }
            .map { toMimeType(it) }
            .distinct()
            .toTypedArray()
            .ifEmpty { arrayOf("*/*") }

        if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            multiPicker.launch(mimeTypes)
        } else {
            singlePicker.launch(mimeTypes.first())
        }
    }

    /** Result of the single-file picker: null when the user cancelled. */
    fun onSingleResult(uri: Uri?) {
        pendingCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        pendingCallback = null
    }

    /** Result of the multi-file picker. */
    fun onMultipleResult(uris: List<Uri>) {
        pendingCallback?.onReceiveValue(uris.toTypedArray().takeIf { it.isNotEmpty() })
        pendingCallback = null
    }

    /** Call from Activity.onDestroy so a pending web callback never leaks. */
    fun clear() {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = null
    }

    /**
     * The file picker only understands MIME types, but pages may send file
     * extensions (".pdf"). Map the common ones; unknown -> "*/*".
     */
    private fun toMimeType(accept: String): String {
        if (!accept.startsWith(".")) return accept
        return when (accept.lowercase()) {
            ".pdf" -> "application/pdf"
            ".jpg", ".jpeg" -> "image/jpeg"
            ".png" -> "image/png"
            ".gif" -> "image/gif"
            ".webp" -> "image/webp"
            ".mp4" -> "video/mp4"
            ".mp3" -> "audio/mpeg"
            ".txt" -> "text/plain"
            ".csv" -> "text/csv"
            ".doc" -> "application/msword"
            ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ".zip" -> "application/zip"
            else -> "*/*"
        }
    }
}
