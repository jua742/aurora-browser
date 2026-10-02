package com.aurora.browser.permissions

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aurora.browser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Central place for all Android runtime permission requests (spec B-13/B-14).
 *
 * Rules enforced here:
 * - Runtime-only, in-context: permissions are asked exactly when the feature
 *   needs them (camera/mic when a site requests capture, POST_NOTIFICATIONS
 *   before the first download), never up front.
 * - Rationale first: when Android says a rationale is warranted, an AlertDialog
 *   explains why before the system prompt appears.
 * - Never loops: exactly one system request per [requestPermissions] call. When
 *   the user has permanently denied ("don't ask again"), the caller - not this
 *   class - should route to [openAppSettings] (see DownloadsViewModel for the
 *   pattern).
 *
 * Registration note: the launcher is registered in init via
 * [androidx.activity.result.ActivityResultRegistry.register] directly, NOT via
 * `registerForActivityResult`. That API throws IllegalStateException when called
 * after onStart, which would make lazy construction impossible - but this class
 * IS constructed lazily (right before the first download, or when a site asks
 * for camera/mic mid-browsing). The direct registry call is safe at any time;
 * keys are unique per instance.
 *
 * Wiring: Worker A's AuroraWebChromeClient calls [requestPermissions] from
 * onPermissionRequest (camera/mic) and onGeolocationPermissionsShowPrompt
 * (location); DownloadsViewModel calls it for POST_NOTIFICATIONS.
 * MainActivity does NOT need to pre-create this class.
 */
class PermissionManager(private val activity: ComponentActivity) {

    /** Completes the currently pending [requestPermissions] call, if any. */
    private var pendingResult: ((Map<String, Boolean>) -> Unit)? = null

    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.activityResultRegistry.register(
            "PermissionManager#" + launcherIds.getAndIncrement(),
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants: Map<String, Boolean> ->
            val handler = pendingResult
            pendingResult = null
            handler?.invoke(grants)
        }

    /**
     * Requests [permissions]. Safe to call from a coroutine (suspends until the
     * user answers). Returns a map of every requested permission -> granted.
     * Makes a single system request per call - never loops.
     */
    suspend fun requestPermissions(vararg permissions: String): Map<String, Boolean> {
        // Already granted? Answer immediately without touching the system prompt.
        val pending = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (pending.isEmpty()) return permissions.associateWith { true }
        // A dead activity can't show the prompt - report current state instead of crashing.
        if (activity.isFinishing || activity.isDestroyed) {
            return permissions.associateWith { it !in pending }
        }
        // Dialogs and the launcher must run on the main thread.
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                // resume() must happen exactly once no matter which path wins.
                val finished = AtomicBoolean(false)
                fun finish(result: Map<String, Boolean>) {
                    if (finished.compareAndSet(false, true)) cont.resume(result)
                }
                pendingResult = { grants ->
                    // The system only returns what we asked for; merge with the
                    // already-granted ones so every requested permission is reported.
                    finish(permissions.associateWith { p -> grants[p] ?: (p !in pending) })
                }
                var dialog: AlertDialog? = null
                cont.invokeOnCancellation { dialog?.dismiss() }

                // User backed out of our rationale: treat everything as denied,
                // without ever showing the system prompt.
                val deniedByRationale = {
                    finish(permissions.associateWith { it !in pending })
                }

                if (pending.any { activity.shouldShowRequestPermissionRationale(it) }) {
                    dialog = AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.perm_rationale_title))
                        .setMessage(rationaleMessage(pending))
                        .setPositiveButton(activity.getString(R.string.perm_rationale_continue)) { _, _ ->
                            launcher.launch(pending.toTypedArray())
                        }
                        .setNegativeButton(activity.getString(R.string.perm_rationale_not_now)) { _, _ ->
                            deniedByRationale()
                        }
                        .setOnCancelListener { deniedByRationale() }
                        .show()
                } else {
                    launcher.launch(pending.toTypedArray())
                }
            }
        }
    }

    /**
     * Opens this app's system settings page (used after a "don't ask again"
     * denial). Resolve-checked per spec B-13 before the external intent.
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        }
    }

    /** Picks the most specific rationale text for the permissions being asked. */
    private fun rationaleMessage(permissions: List<String>): String {
        val byPermission = listOf(
            Manifest.permission.CAMERA to R.string.perm_rationale_camera,
            Manifest.permission.RECORD_AUDIO to R.string.perm_rationale_microphone,
            Manifest.permission.ACCESS_FINE_LOCATION to R.string.perm_rationale_location,
            Manifest.permission.ACCESS_COARSE_LOCATION to R.string.perm_rationale_location,
            // POST_NOTIFICATIONS is a plain String constant; referencing it here
            // is compile-safe on all API levels.
            Manifest.permission.POST_NOTIFICATIONS to R.string.perm_rationale_notifications
        )
        val res = byPermission.firstOrNull { (permission, _) -> permission in permissions }?.second
            ?: R.string.perm_rationale_generic
        return activity.getString(res)
    }

    companion object {
        private val launcherIds = AtomicInteger(0)
    }
}
