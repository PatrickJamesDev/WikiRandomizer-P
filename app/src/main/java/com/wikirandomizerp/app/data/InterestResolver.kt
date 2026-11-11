package com.wikirandomizerp.app.data

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/** A Wikipedia category that probably covers (part of) an interest phrase. */
data class InterestCategory(
    val category: String,        // with underscores, as used by the topic system
    val score: Double,
    val seedArticles: Int,       // how many top search hits belong to it
    val nameMatch: Boolean,      // the category's own name matched the phrase
    val size: Int = 0,           // members (articles + sub-categories); 0 = unknown
    /** The category's name shares a word with what the user wrote (or matched it by name). */
    val relevant: Boolean = true
) {
    val label: String get() = category.replace('_', ' ')
}

/** Everything found for one phrase of the user's description. */
data class InterestMatch(
    val phrase: String,
    val categories: List<InterestCategory>,
    val sampleArticles: List<String>,
    /** What the user said they do NOT want ("but not tobacco products"). */
    val exclusions: List<String> = emptyList()
)

/**
 * Maps free text to Wikipedia categories without any language model:
 *  1. find categories whose NAME matches the phrase,
 *  2. full-text search the phrase and take the top articles as "seeds",
 *  3. look at which categories those seeds share (shared by several = on topic).
 * The user then confirms / edits the result, and the normal topic library is built from it.
 */
class InterestResolver(private val repo: WikipediaRepository) {

    suspend fun resolve(text: String, onStatus: (String) -> Unit): List<InterestMatch> {
        val parsed = InterestParser.parse(text)
        val phrases = parsed.phrases
        exclusions = parsed.exclusions
        DebugLog.i(TAG, "interest text '$text' -> phrases $phrases, exclusions ${parsed.exclusions}")
        val out = ArrayList<InterestMatch>()
        phrases.forEachIndexed { i, phrase ->
            onStatus("Looking up \"$phrase\" (${i + 1}/${phrases.size})")
            out.add(DebugLog.time(TAG, "resolve '$phrase'") { resolvePhrase(phrase) })
        }
        return out
    }

    private var exclusions: List<String> = emptyList()

    /** A category that is itself what the user excluded ("Tobacco products" for "not tobacco products"). */
    private fun isExcluded(category: String): Boolean {
        if (exclusions.isEmpty()) return false
        val cw = words(category.replace('_', ' '))
        if (cw.isEmpty()) return false
        return exclusions.any { e ->
            val ew = words(e)
            ew.isNotEmpty() && (cw.all { c -> ew.any { sameWord(c, it) } } || ew.all { w -> cw.any { sameWord(w, it) } })
        }
    }

    private suspend fun resolvePhrase(phrase: String): InterestMatch {
        val score = HashMap<String, Double>()
        val seedCount = HashMap<String, Int>()
        val nameHits = HashSet<String>()
        val exactHits = HashSet<String>()
        val variants = InterestQuery.variants(phrase).ifEmpty { listOf(phrase) }
        DebugLog.d(TAG, "query variants for '$phrase': $variants")

        // Search category names with phrase variants. "all F1 drivers from Finland" becomes
        // "Formula One drivers from Finland" and "Finnish Formula One drivers"; the latter is the
        // actual category name and must outrank the generic "Formula One drivers".
        variants.take(3).forEachIndexed { vi, variant ->
            retry("category-name search '$variant'") { repo.searchCategoryNames(variant) }
                .query?.search?.forEachIndexed { rank, hit ->
                    val c = normalize(hit.title)
                    if (isUsable(c) && !isExcluded(c)) {
                        score[c] = (score[c] ?: 0.0) +
                                (2.5 / (1 + 0.3 * rank)) * (1.0 - 0.18 * vi)
                        nameHits.add(c)
                        if (variants.any { it.equals(c.replace('_', ' '), ignoreCase = true) }) {
                            exactHits.add(c)
                        }
                    }
                }
        }

        // Full-text search gives seed articles and categories shared by the seeds. Use the
        // normalized phrase here (not the raw sentence), otherwise "all F1 ..." matches prose.
        val primary = variants.getOrElse(1) { variants.first() }
        val seeds = retry("article search '$primary'") { repo.searchArticleTitles(primary) }
            .query?.search?.map { it.title } ?: emptyList()
        DebugLog.d(TAG, "seeds for '$primary': ${seeds.take(10)}")

        seeds.take(20).chunked(10).forEach { chunk ->
            val pages = retry("categories of seeds") { repo.getPageCategories(chunk) }.query?.pages ?: emptyList()
            for (p in pages) {
                val rank = seeds.indexOf(p.title).coerceAtLeast(0)
                val w = 1.0 / (1 + 0.08 * rank)
                for (c in p.categories) {
                    val name = normalize(c.title)
                    if (!isUsable(name) || isExcluded(name)) continue
                    score[name] = (score[name] ?: 0.0) + w
                    seedCount[name] = (seedCount[name] ?: 0) + 1
                }
            }
        }

        // The user's words must show up in a category's name for it to count as on-topic.
        // Seeds alone are not enough: "plants in the family Solanaceae" finds the Tobacco
        // article, whose category "Tobacco" also contains cigarettes and nicotine pouches.
        val phraseWords = variants.flatMap { words(it) }.toSet()
        val variantWords = variants.map { words(it) }
        // A one-word interest may be singular or plural: "fruits" must treat Category:Fruit as
        // the exact match, not as a prefix like Category:Fruit flies.
        val stemExact = if (phraseWords.size == 1) {
            val only = phraseWords.first()
            score.keys.filterTo(HashSet()) { key ->
                val cw = words(key.replace('_', ' '))
                cw.size == 1 && cw.first() == only
            }
        } else emptySet()
        val exactOrStem = exactHits + stemExact
        val coverage = HashMap<String, Double>()
        for (c in score.keys.toList()) {
            val cw = words(c.replace('_', ' '))
            val hits = phraseWords.count { pw -> cw.any { sameWord(pw, it) } }
            val bestCoverage = variantWords.maxOf { vw ->
                if (vw.isEmpty()) 0.0
                else vw.count { pw -> cw.any { sameWord(pw, it) } }.toDouble() / vw.size
            }
            coverage[c] = bestCoverage
            val exactBoost = if (c in exactOrStem) 1.8 else 0.0
            score[c] = (score[c] ?: 0.0) *
                    (if (hits > 0) 1.0 + 0.9 * minOf(hits, 3) + 1.2 * bestCoverage + exactBoost else 0.35)
        }
        val ranked = score.entries.sortedByDescending { it.value }
        // On topic = named like the phrase, shared by at least two seeds, or high name coverage.
        var shortlist = ranked.filter {
            it.key in nameHits || (seedCount[it.key] ?: 0) >= 2 || (coverage[it.key] ?: 0.0) >= 0.66
        }.take(16)
        if (shortlist.isEmpty()) shortlist = ranked.take(5)

        // Look up how big each candidate is. Huge generic categories ("Rear-wheel-drive
        // vehicles") are demoted so the specific ones win, and empty/non-existent ones vanish.
        val sizes = HashMap<String, Int>()
        try {
            shortlist.map { it.key }.chunked(25).forEach { chunk ->
                retry("category sizes") { repo.getCategoryInfo(chunk) }.query?.pages?.forEach { p ->
                    sizes[normalize(p.title)] = p.categoryinfo?.size ?: 0
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w(TAG, "could not fetch category sizes: ${e.message}")
        }
        fun isPrefixOnly(key: String): Boolean {
            if (key in exactOrStem || phraseWords.size != 1) return false
            val cw = words(key.replace('_', ' '))
            return cw.size > phraseWords.size &&
                    phraseWords.all { pw -> cw.any { sameWord(pw, it) } }
        }
        fun adjusted(e: Map.Entry<String, Double>): Double {
            val exact = e.key in exactOrStem
            // A one-word interest ("fruit") must prefer the category that *is* that word over
            // every category that merely begins with it ("Fruit flies"). Smaller sub-categories
            // used to win through the size penalty, which is why the user got fruit flies.
            val prefixOnly = isPrefixOnly(e.key)
            val size = sizes[e.key]
            val sizePenalty = if (size == null) 1.0
            else 1.0 + maxOf(0.0, Math.log10(size / 300.0))
            val specificity = 1.0 + 1.5 * (coverage[e.key] ?: 0.0) +
                    if (exact) 3.5 else 0.0
            return e.value * specificity *
                    (if (prefixOnly) 0.35 else 1.0) /
                    (if (prefixOnly) 1.0 else sizePenalty)
        }
        val chosen = shortlist
            .filter { (sizes[it.key] ?: 1) > 0 }
            .sortedWith(
                compareByDescending<Map.Entry<String, Double>> { it.key in exactOrStem }
                    .thenByDescending { adjusted(it) }
            )
            .take(8)
        val categories = chosen.map {
            InterestCategory(
                it.key, adjusted(it), seedCount[it.key] ?: 0, it.key in nameHits, sizes[it.key] ?: 0,
                // A prefix-only category is never pre-selected: "Fruit flies" shares the word
                // "fruit" but is a different subject, so it stays available but unticked.
                relevant = !isPrefixOnly(it.key) &&
                        ((coverage[it.key] ?: 0.0) >= 0.55 || it.key in exactOrStem)
            )
        }
        DebugLog.i(TAG, "'$phrase' -> ${categories.map { "${it.category}(%.1f,seeds=${it.seedArticles},name=${it.nameMatch})".format(it.score) }}")
        return InterestMatch(phrase, categories, seeds.take(6), exclusions)
    }

    private val wordSplit = Regex("[^\\p{L}\\p{N}]+")
    private val stop = setOf("the", "and", "for", "with", "from", "that", "this", "are", "was", "made", "such", "into", "about", "also", "any", "all", "not", "its", "their", "other", "some", "most")

    private fun words(text: String): Set<String> =
        text.lowercase().split(wordSplit)
            .filter { it.length >= 3 && it !in stop }
            .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
            .filter { it.length >= 3 }
            .toSet()

    private fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        val common = a.commonPrefixWith(b).length
        return common >= 5 || (common >= 4 && minOf(a.length, b.length) <= 5)
    }

    private fun normalize(title: String) = title.removePrefix("Category:").replace(' ', '_')

    private val junk = listOf(
        "articles", "pages ", "wikipedia", "cs1", "webarchive", "short description", "use ",
        "living people", "births", "deaths", "stubs", "commons", "wikidata", "all ", "dmy", "mdy",
        "described in", "named by", "taxa named", "templates", "redirects", "lists of", "list of",
        "disambiguation", "infobox", "pages using", "maintenance", "people from", "people by"
    )

    private fun isUsable(category: String): Boolean {
        val t = category.replace('_', ' ').lowercase()
        return junk.none { t.contains(it) }
    }

    /** A few quick retries so one dropped packet doesn't fail the whole lookup. */
    private suspend fun <T> retry(label: String, block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w(TAG, "$label attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                if (attempt >= 4) throw e
                delay(1_500L * attempt)
            }
        }
    }

    companion object {
        private const val TAG = "Interest"
    }
}
