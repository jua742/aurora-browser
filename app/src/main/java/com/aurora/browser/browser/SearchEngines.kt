package com.aurora.browser.browser

/**
 * Search engine model (spec B-4): the template contains exactly one %s
 * placeholder, replaced with the URL-encoded query. The suggest template is
 * intentionally unused — v1 suggestions are local-only by design, so no
 * keystroke is ever sent to a server while typing.
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val searchUrlTemplate: String,
    val suggestUrlTemplate: String? = null,
)

object SearchEngines {

    const val CUSTOM_ID = "custom"
    const val DEFAULT_ID = "google"

    val GOOGLE = SearchEngine("google", "Google", "https://www.google.com/search?q=%s")
    val BING = SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s")
    val DUCKDUCKGO = SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s")
    val YAHOO = SearchEngine("yahoo", "Yahoo", "https://search.yahoo.com/search?p=%s")
    val CUSTOM = SearchEngine(CUSTOM_ID, "Custom", "")

    val BUILT_IN: List<SearchEngine> = listOf(GOOGLE, BING, DUCKDUCKGO, YAHOO)

    /**
     * Resolve the active template. Unknown ids (corrupt prefs) fall back to
     * Google; a blank custom template also falls back instead of crashing.
     */
    fun templateFor(engineId: String, customTemplate: String): String {
        if (engineId == CUSTOM_ID) return customTemplate.ifBlank { GOOGLE.searchUrlTemplate }
        return BUILT_IN.firstOrNull { it.id == engineId }?.searchUrlTemplate
            ?: GOOGLE.searchUrlTemplate
    }

    fun engineFor(engineId: String): SearchEngine =
        BUILT_IN.firstOrNull { it.id == engineId }
            ?: if (engineId == CUSTOM_ID) CUSTOM else GOOGLE
}
