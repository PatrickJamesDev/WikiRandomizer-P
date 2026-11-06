package org.nsh07.wikireader.shared

/** Small, dependency-free text helpers shared by Android and iOS. */
object WikiText {
    private val htmlTag = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")

    /** Turns the snippets returned by the REST API into readable plain text. */
    fun plainText(input: String): String = normalizeWhitespace(
        input
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace(htmlTag, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    )

    fun normalizeWhitespace(input: String): String =
        input.replace(whitespace, " ").trim()

    /** A short, sentence-safe snippet for lists and cards. */
    fun snippet(input: String, maxChars: Int = 280): String {
        val text = plainText(input)
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val sentenceEnd = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
        val end = if (sentenceEnd >= maxChars / 2) sentenceEnd + 1 else maxChars
        return text.take(end).trimEnd(' ', ',', ';', ':') + "…"
    }

    /** Estimated reading time, rounded up; useful on both the article and the iOS shell. */
    fun readingMinutes(text: String, wordsPerMinute: Int = 200): Int {
        if (wordsPerMinute <= 0) return 0
        val words = plainText(text)
            .split(' ')
            .count { it.any { char -> char.isLetterOrDigit() } }
        return if (words == 0) 0 else (words + wordsPerMinute - 1) / wordsPerMinute
    }
}
