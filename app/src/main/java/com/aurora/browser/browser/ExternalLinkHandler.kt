package com.aurora.browser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * External (non-http) link handling (spec B-3, B-13).
 *
 * Allowlist: tel:, mailto:, sms:, smsto: — opened with ACTION_VIEW and ONLY
 * when resolveActivity() finds an app that can handle them. EVERYTHING else
 * (including intent: URLs — no Intent.parseUri on untrusted input in v1) is
 * blocked. This prevents intent-hijacking from malicious pages.
 */
object ExternalLinkHandler {

    private val ALLOWED_SCHEMES = setOf("tel", "mailto", "sms", "smsto")

    sealed interface Result {
        /** The intent was fired. */
        data object Opened : Result

        /** Allowlisted scheme, but no installed app can handle it. */
        data object NoHandler : Result

        /** Not on the allowlist: blocked. */
        data object Blocked : Result
    }

    fun handle(context: Context, url: String): Result {
        val scheme = try {
            Uri.parse(url).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }
        if (scheme !in ALLOWED_SCHEMES) return Result.Blocked
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Resolve check BEFORE firing (spec B-13): never send an intent
            // no app can handle.
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Result.Opened
            } else {
                Result.NoHandler
            }
        } catch (_: Exception) {
            Result.NoHandler
        }
    }
}
