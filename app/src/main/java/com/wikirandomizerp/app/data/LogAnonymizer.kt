package com.wikirandomizerp.app.data

/**
 * Removes what a debug log reveals about reading habits before it is put in a bug report:
 * article titles, search words, topic names, file names, e-mail addresses and URL details.
 * Technical content (timings, error classes, stack traces, counts, settings) stays.
 */
object LogAnonymizer {
    private val quoted = Regex("'[^'\\n]{1,200}'")
    private val keyedTitle = Regex(
        "\\b(title|query|search|topic|label|name|requestedTitle|lastQuery)=(.+?)(?=\\s+[A-Za-z]+=|\\s*$|,)",
        RegexOption.MULTILINE
    )
    private val bracketLists = Regex("(topics?|indexed|todo|enabled)([^\\[\\n]{0,40})\\[[^\\]\\n]*]", RegexOption.IGNORE_CASE)
    private val url = Regex("https?://[^\\s\"'<>)]+")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val noTitle = Regex("(No article with title ).+")
    private val safeParams = setOf(
        "action", "format", "prop", "list", "generator", "piprop", "pithumbsize", "lllimit",
        "grnlimit", "formatversion", "grnnamespace", "maxlag", "cmlimit", "srlimit", "rvprop",
        "cmtype", "pilicense", "redirects", "origin", "width"
    )

    fun anonymize(text: String): String {
        var out = text
        out = email.replace(out, "<email>")
        out = url.replace(out) { anonymizeUrl(it.value) }
        out = noTitle.replace(out, "$1<title>")
        out = bracketLists.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}[<redacted>]" }
        out = keyedTitle.replace(out) { "${it.groupValues[1]}=<redacted>" }
        // Last: any remaining quoted text is almost always an article title or search word in this
        // app's log messages. Short technical words in quotes (modes, kinds) are kept.
        out = quoted.replace(out) { m ->
            val inner = m.value.trim('\'')
            if (inner.length <= 12 && inner.all { it.isLowerCase() || it == '-' || it == '_' }) m.value
            else "'<redacted>'"
        }
        return out
    }

    private fun anonymizeUrl(raw: String): String {
        val noScheme = raw.substringAfter("://")
        val host = noScheme.substringBefore('/').substringBefore('?')
        val rest = noScheme.removePrefix(host)
        val path = rest.substringBefore('?')
        val query = if (rest.contains('?')) rest.substringAfter('?') else ""
        val keptPath = when {
            path.contains("api.php") -> "/w/api.php"
            path.startsWith("/wiki/") -> "/wiki/<redacted>"
            path.contains("/thumb/") -> "/thumb/<redacted>." + path.substringAfterLast('.', "img").take(4)
            path.isEmpty() || path == "/" -> path
            else -> "/<redacted>"
        }
        val keptQuery = query.split('&').filter { it.isNotEmpty() }.joinToString("&") { pair ->
            val key = pair.substringBefore('=')
            if (key in safeParams) pair else "$key=<redacted>"
        }
        return "https://$host$keptPath" + if (keptQuery.isNotEmpty()) "?$keptQuery" else ""
    }
}
