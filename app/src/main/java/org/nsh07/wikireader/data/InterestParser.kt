package org.nsh07.wikireader.data

/**
 * Turns a free-text description such as
 * "I am interested in succulent plants of North America, as well as American made muscle cars
 * made before 1980" into separate interest phrases:
 * ["succulent plants of North America", "American made muscle cars made before 1980"].
 */
object InterestParser {
    private val leadIns = listOf(
        Regex("""^\s*(?:i\s*am|i'm|im|i\s+am\s+really|we\s+are)\s+(?:really\s+|very\s+|mostly\s+|quite\s+|especially\s+)?(?:interested\s+in|into|a\s+fan\s+of|passionate\s+about|curious\s+about|fascinated\s+by|keen\s+on)\s*:?\s*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*i\s+(?:really\s+|also\s+)?(?:like|love|enjoy|adore|want\s+to\s+(?:read|learn)\s+about|would\s+like\s+to\s+(?:read|learn)\s+about|want\s+articles\s+(?:about|on))\s*:?\s*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:show|give)\s+me\s+(?:some\s+)?(?:articles|things|stuff|pages)?\s*(?:about|on|regarding)?\s*:?\s*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:my\s+interests?\s+(?:are|is|include)|interests?\s*:|topics?\s*:|articles?\s+(?:about|on)|things\s+(?:about|on))\s*:?\s*""", RegexOption.IGNORE_CASE),
    )

    private val separators = Regex(
        """\s*(?:[,;\n]+|\s+as\s+well\s+as\s+|\s+and\s+also\s+|\s+plus\s+|\s+and\s+|\s+&\s+|\.\s+)\s*""",
        RegexOption.IGNORE_CASE
    )

    private val leadingJunk = Regex("""^(?:as\s+well\s+as|and\s+also|also|and|plus|the|some|any|especially|particularly|mostly|lots\s+of|a\s+lot\s+of)\s+""", RegexOption.IGNORE_CASE)

    private const val MAX_INPUT_CHARS = 4_000
    private val whitespaceRun = Regex("""\s{2,}""")

    /**
     * The patterns below start with `\s*` and are retried at every offset, which is quadratic on a
     * long run of blanks: 20,000 spaces took 11 s and "i am" + 20,000 spaces effectively never
     * finished (a pasted blob froze the topic screen). Cap the length and collapse runs first;
     * a newline stays a newline because it separates interests.
     */
    private fun normalize(text: String): String =
        text.take(MAX_INPUT_CHARS).replace(whitespaceRun) { if ('\n' in it.value) "\n" else " " }

    fun split(text: String): List<String> {
        var t = normalize(text).trim()
        if (t.isEmpty()) return emptyList()
        for (r in leadIns) {
            val stripped = r.replaceFirst(t, "")
            if (stripped.length < t.length) { t = stripped; break }
        }
        val out = ArrayList<String>()
        for (raw in separators.split(t)) {
            var p = raw.trim().trim('.', '!', '?', ':', '"', '\'').trim()
            var prev: String
            do { prev = p; p = leadingJunk.replaceFirst(p, "").trim() } while (p != prev)
            if (p.length >= 3 && out.none { it.equals(p, ignoreCase = true) }) out.add(p)
        }
        return out
    }

    /** The interest phrases plus anything the user said they do NOT want. */
    class Parsed(val phrases: List<String>, val exclusions: List<String>)

    // "..., but not cigarettes or nicotine pouches"  /  "excluding tobacco products"
    private val negation = Regex(
        """(?:^|[,;]|\s)\s*(?:but\s+)?(?:not|no|excluding|except(?:\s+for)?|without|other\s+than)\s+([^,;.]+)""",
        RegexOption.IGNORE_CASE
    )
    private val exclusionSplit = Regex("""\s+(?:and|or)\s+|\s*[&/]\s*""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Parsed {
        val exclusions = ArrayList<String>()
        val cleaned = negation.replace(normalize(text)) { m ->
            for (part in m.groupValues[1].split(exclusionSplit)) {
                val e = part.trim().trim('.', ' ', '"')
                if (e.length >= 3 && exclusions.none { it.equals(e, ignoreCase = true) }) exclusions.add(e)
            }
            " "
        }
        return Parsed(split(cleaned), exclusions)
    }
}
