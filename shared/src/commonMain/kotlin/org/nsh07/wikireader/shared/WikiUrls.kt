package org.nsh07.wikireader.shared

/**
 * Canonical Wikipedia URL construction, shared by the Android and iOS apps.
 *
 * Keeping this in one place avoids the small differences that used to creep in between the
 * share sheet, deep links, image loading and the iOS shell (spaces vs underscores, `m.` host,
 * missing URL encoding, and so on).
 */
object WikiUrls {
    private const val HEX = "0123456789ABCDEF"
    private val repeatedUnderscores = Regex("_+")

    /** Host for the desktop/mobile-agnostic Wikipedia site of [lang]. */
    fun host(lang: String): String = "${cleanLang(lang)}.wikipedia.org"

    fun articleUrl(lang: String, title: String): String =
        "https://${host(lang)}/wiki/${encodeTitle(title)}"

    fun mobileArticleUrl(lang: String, title: String): String =
        "https://${cleanLang(lang)}.m.wikipedia.org/wiki/${encodeTitle(title)}"

    fun mainPageUrl(lang: String): String = articleUrl(lang, "Main_Page")

    fun randomSummaryUrl(lang: String): String =
        "https://${host(lang)}/api/rest_v1/page/random/summary"

    fun summaryUrl(lang: String, title: String): String =
        "https://${host(lang)}/api/rest_v1/page/summary/${encodeTitle(title)}"

    fun searchUrl(lang: String, query: String): String =
        "https://${host(lang)}/w/index.php?search=${encodeQuery(query)}"

    /** Full-text search via the MediaWiki generator API, including thumbnails and snippets. */
    fun searchApiUrl(lang: String, query: String, limit: Int = 20): String =
        "https://${host(lang)}/w/api.php?action=query&generator=search" +
            "&gsrsearch=${encodeQuery(query)}&gsrnamespace=0&gsrlimit=$limit" +
            "&gsrprop=snippet|titlesnippet|redirecttitle" +
            "&prop=pageimages&piprop=thumbnail&pithumbsize=250" +
            "&format=json&formatversion=2"

    fun categoryMembersUrl(lang: String, category: String, limit: Int = 50): String =
        "https://${host(lang)}/w/api.php?action=query&list=categorymembers" +
            "&cmtitle=${encodeQuery("Category:$category")}" +
            "&cmtype=page&cmnamespace=0&cmlimit=$limit&format=json&formatversion=2"

    /** Special:FilePath is the stable, resizing image endpoint used by the Android image loader. */
    fun filePathUrl(lang: String, fileName: String, width: Int = 0): String {
        val file = encodeTitle(fileName.substringAfter("File:", fileName))
        val size = if (width > 0) "?width=$width" else ""
        return "https://${host(lang)}/wiki/Special:FilePath/$file$size"
    }

    /**
     * MediaWiki treats spaces and underscores in the title part identically, but encoded URLs
     * should use underscores. `#` starts a fragment, so encode it rather than dropping it.
     */
    fun normalizeTitle(title: String): String =
        title.trim()
            .replace(' ', '_')
            .replace(repeatedUnderscores, "_")
            .trim('_')

    /** Percent-encodes a title for use as a single URL path segment. */
    fun encodeTitle(title: String): String = encodePathSegment(normalizeTitle(title))

    /** Percent-encodes an arbitrary search query. Spaces become `%20`, which is always valid. */
    fun encodeQuery(query: String): String = encodePathSegment(query.trim(), plusForSpace = false)

    private fun encodePathSegment(value: String, plusForSpace: Boolean = false): String =
        buildString {
            for (byte in value.encodeToByteArray()) {
                val code = byte.toInt() and 0xFF
                val char = code.toChar()
                when {
                    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' -> append(char)
                    char == '-' || char == '_' || char == '.' || char == '~' -> append(char)
                    char == ' ' && plusForSpace -> append('+')
                    else -> {
                        append('%')
                        append(HEX[code shr 4])
                        append(HEX[code and 0x0F])
                    }
                }
            }
        }

    private fun cleanLang(lang: String): String =
        lang.trim().lowercase().ifBlank { "en" }
}
