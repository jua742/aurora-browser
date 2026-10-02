package com.aurora.browser.browser

import java.net.URI
import java.net.URLEncoder

/**
 * Central URL validation + address-bar resolution (spec B-4, B-13).
 *
 * The 5-step address-bar algorithm, exactly as specified:
 *  1. Trim input; empty -> do nothing.
 *  2. Starts with http:// or https:// -> load as-is (after basic validation).
 *  3. Else, if it "looks like a URL" — no whitespace AND (contains a dot OR
 *     starts with "localhost" OR matches IPv4 n.n.n.n, optionally with
 *     :port/path) -> prepend https:// and load.
 *     (Any space anywhere -> it is a search: "example.com foo" searches.)
 *  4. Else -> search via the active engine template (%s = URL-encoded query).
 *  5. A manually typed known-safe scheme (tel:, mailto:, sms:, smsto:) ->
 *     route through the external-link handler, never the WebView.
 */
object UrlResolver {

    /** Result of resolving one address-bar input. */
    sealed interface Resolution {
        /** Load this URL in the WebView. */
        data class LoadUrl(val url: String) : Resolution

        /** Search for this query with the active search engine. */
        data class Search(val query: String) : Resolution

        /** Hand to Android as an external link (tel:, mailto:, ...). */
        data class External(val url: String) : Resolution

        /** Empty input: do nothing. */
        data object Empty : Resolution

        /** Rejected input: the UI shows a toast and stays on the current page. */
        data object Invalid : Resolution
    }

    /** Schemes a user may type that must go through the external-link handler. */
    private val TYPED_EXTERNAL_SCHEMES = setOf("tel", "mailto", "sms", "smsto")

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun resolve(input: String): Resolution {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Resolution.Empty
        // Spec B-13 input validation: control characters are never loadable.
        if (trimmed.any { it.isISOControl() }) return Resolution.Invalid

        val lower = trimmed.lowercase()
        // Step 2: explicit http(s) URL.
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return if (isValidHttpUrl(trimmed)) Resolution.LoadUrl(trimmed)
            else Resolution.Invalid
        }

        // Step 5: a manually typed safe scheme goes to the external handler.
        val schemeEnd = trimmed.indexOf(':')
        if (schemeEnd > 0 && trimmed.substring(0, schemeEnd).lowercase() in TYPED_EXTERNAL_SCHEMES) {
            return Resolution.External(trimmed)
        }

        // Step 3: looks like a URL (no whitespace AND dot/localhost/IPv4).
        if (trimmed.none { it.isWhitespace() }) {
            val hostPart = trimmed.substringBefore('/').substringBefore('?').substringBefore(':')
            if ('.' in trimmed || lower.startsWith("localhost") || IPV4.matches(hostPart)) {
                return Resolution.LoadUrl("https://$trimmed")
            }
        }

        // Step 4: search.
        return Resolution.Search(trimmed)
    }

    /** Build a search URL from a template containing exactly one %s placeholder. */
    fun buildSearchUrl(template: String, query: String): String =
        template.replace("%s", URLEncoder.encode(query, "UTF-8"))

    /**
     * A custom engine template is valid only if it is http(s) and contains
     * exactly one %s (spec B-4/B-13). Invalid templates are rejected with an
     * error message in the settings UI.
     */
    fun validateCustomSearchTemplate(template: String): Boolean {
        val t = template.trim()
        val schemeOk = t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true)
        return schemeOk && t.contains("%s") && t.indexOf("%s") == t.lastIndexOf("%s")
    }

    /** True for http:// and https:// URLs — the only schemes the WebView may load. */
    fun isHttpUrl(url: String): Boolean {
        val l = url.lowercase()
        return l.startsWith("http://") || l.startsWith("https://")
    }

    private fun isValidHttpUrl(url: String): Boolean {
        if (url.any { it.isWhitespace() }) return false
        return try {
            val uri = URI(url)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /** Host of an http(s) URL without a leading "www.", or null. */
    fun hostOf(url: String): String? = try {
        URI(url).host?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }
}
