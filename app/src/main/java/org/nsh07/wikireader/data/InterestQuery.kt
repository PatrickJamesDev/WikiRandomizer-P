package org.nsh07.wikireader.data

/**
 * Turns a natural-language interest into the phrase shapes Wikipedia categories actually use.
 *
 * People say "all F1 drivers from Finland"; the matching category is
 * "Category:Finnish Formula One drivers". Searching only the literal words used to miss that
 * category, or return the generic "Formula One drivers" one. This object is deliberately
 * network-free so it can be unit tested.
 */
object InterestQuery {

    private val synonyms = listOf(
        Regex("""\b(?:f1|f-1|formula\s*1)\b""", RegexOption.IGNORE_CASE) to "Formula One",
        Regex("""\b(?:f2|formula\s*2)\b""", RegexOption.IGNORE_CASE) to "Formula Two",
        Regex("""\b(?:f3|formula\s*3)\b""", RegexOption.IGNORE_CASE) to "Formula Three",
        Regex("""\b(?:motogp|moto\s*gp)\b""", RegexOption.IGNORE_CASE) to "MotoGP",
        Regex("""\b(?:epl|english\s+premier\s+league)\b""", RegexOption.IGNORE_CASE) to "Premier League",
        Regex("""\b(?:nba)\b""", RegexOption.IGNORE_CASE) to "National Basketball Association",
        Regex("""\b(?:nfl)\b""", RegexOption.IGNORE_CASE) to "National Football League"
    )

    private val leadingFiller = Regex(
        """^\s*(?:(?:all|every|any|some|the|articles?\s+about|things?\s+about|stuff\s+about|list\s+of)\s+)+""",
        RegexOption.IGNORE_CASE
    )
    private val fromTail = Regex("""\bfrom\s+([\p{L}][\p{L}\s.'-]*?)\s*$""", RegexOption.IGNORE_CASE)

    /** Common country -> adjective/demonym forms used by Wikipedia category names. */
    private val demonyms = mapOf(
        "afghanistan" to "Afghan", "albania" to "Albanian", "algeria" to "Algerian",
        "argentina" to "Argentine", "armenia" to "Armenian", "australia" to "Australian",
        "austria" to "Austrian", "bangladesh" to "Bangladeshi", "belgium" to "Belgian",
        "brazil" to "Brazilian", "bulgaria" to "Bulgarian", "cambodia" to "Cambodian",
        "canada" to "Canadian", "chile" to "Chilean", "china" to "Chinese",
        "colombia" to "Colombian", "croatia" to "Croatian", "cuba" to "Cuban",
        "czech republic" to "Czech", "czechia" to "Czech", "denmark" to "Danish",
        "egypt" to "Egyptian", "england" to "English", "estonia" to "Estonian",
        "ethiopia" to "Ethiopian", "finland" to "Finnish", "france" to "French",
        "georgia" to "Georgian", "germany" to "German", "ghana" to "Ghanaian",
        "greece" to "Greek", "hungary" to "Hungarian", "iceland" to "Icelandic",
        "india" to "Indian", "indonesia" to "Indonesian", "iran" to "Iranian",
        "iraq" to "Iraqi", "ireland" to "Irish", "israel" to "Israeli", "italy" to "Italian",
        "jamaica" to "Jamaican", "japan" to "Japanese", "kenya" to "Kenyan",
        "latvia" to "Latvian", "lebanon" to "Lebanese", "lithuania" to "Lithuanian",
        "malaysia" to "Malaysian", "mexico" to "Mexican", "morocco" to "Moroccan",
        "netherlands" to "Dutch", "new zealand" to "New Zealand", "nigeria" to "Nigerian",
        "norway" to "Norwegian", "pakistan" to "Pakistani", "peru" to "Peruvian",
        "philippines" to "Filipino", "poland" to "Polish", "portugal" to "Portuguese",
        "romania" to "Romanian", "russia" to "Russian", "saudi arabia" to "Saudi",
        "scotland" to "Scottish", "serbia" to "Serbian", "singapore" to "Singaporean",
        "slovakia" to "Slovak", "slovenia" to "Slovenian", "south africa" to "South African",
        "south korea" to "South Korean", "spain" to "Spanish", "sweden" to "Swedish",
        "switzerland" to "Swiss", "thailand" to "Thai", "turkey" to "Turkish",
        "ukraine" to "Ukrainian", "united kingdom" to "British", "united states" to "American",
        "usa" to "American", "u.s." to "American", "us" to "American",
        "uk" to "British", "u.k." to "British", "britain" to "British", "great britain" to "British",
        "uruguay" to "Uruguayan", "venezuela" to "Venezuelan", "vietnam" to "Vietnamese",
        "wales" to "Welsh"
    )

    /**
     * Phrase variants in priority order. The first entry is the cleaned literal phrase, followed
     * by synonym expansion and then a demonym-reordered version of "X from Country".
     */
    fun variants(phrase: String): List<String> {
        val cleaned = clean(phrase)
        if (cleaned.isBlank()) return emptyList()
        val expanded = expandSynonyms(cleaned)
        val out = LinkedHashSet<String>()
        out.add(cleaned)
        out.add(expanded)
        val match = fromTail.find(expanded)
        if (match != null) {
            val country = match.groupValues[1].trim().removePrefix("the ").trim()
            val before = expanded.removeRange(match.range).trim().trimEnd(',', ';', ':')
            val demonym = demonyms[country.lowercase()]
            if (demonym != null && before.isNotBlank()) {
                out.add("$demonym $before")
            }
        }
        return out.filter { it.isNotBlank() }.distinct()
    }

    fun clean(text: String): String {
        var out = text.trim().replace(Regex("\\s+"), " ")
        out = leadingFiller.replace(out, "")
        return out.trim().trim(',', ';', ':', '.')
    }

    fun expandSynonyms(text: String): String {
        var out = text
        for ((pattern, replacement) in synonyms) out = pattern.replace(out, replacement)
        return out.replace(Regex("\\s+"), " ").trim()
    }
}
