package org.nsh07.wikireader.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** The text of one library article. [intro] is the first lines of the article, if fetched. */
class RelDoc(val key: String, val title: String, val descr: String?, val intro: String?)

/**
 * Local relevance engine (no network, no language model).
 *
 * "Does this article belong to the topic the user described?" is answered the way a small
 * search engine would, in three steps:
 *  1. TF-IDF over title + description + introduction of every article in the library, so a word
 *     that appears everywhere ("plant" inside a botany topic) counts little and a distinctive
 *     one counts a lot.
 *  2. Pseudo-relevance feedback (Rocchio): the articles that match the user's own words best are
 *     taken as seeds, and the vocabulary they share becomes part of the query. "Muscle cars"
 *     then also matches "V8", "rear-wheel drive", "Pontiac" - words the user never typed.
 *  3. Every article is scored by similarity to that query, relative to how well the seeds
 *     themselves score (100 = as on-topic as a typical seed).
 *
 * User feedback (liked / disliked articles) re-ranks candidates the same way, instantly.
 */
object RelevanceModel {

    class Result(
        /** key -> relevance percent (100 = typical seed, 0..300). */
        val rel: Map<String, Int>,
        /**
         * key -> how much of the topic's own vocabulary the article actually contains, 0..100.
         * Unlike [rel], this is absolute: an article can score high relative to a weak field while
         * sharing almost nothing with the user's words. Used to reject those unrelated picks.
         */
        val cov: Map<String, Int>,
        /** Keys that matched one of the user's exclusions ("not tobacco products"). */
        val excluded: Set<String>,
        val seedKeys: List<String>
    )

    private const val DROP_SIMILARITY = 0.30
    private const val FEEDBACK_STRENGTH = 4.5

    private val splitter = Regex("[^\\p{L}\\p{N}]+")
    private val stop = setOf(
        "the", "and", "for", "with", "from", "that", "this", "are", "was", "were", "has", "have", "had",
        "its", "their", "his", "her", "which", "who", "whose", "what", "where", "when", "also", "other",
        "such", "than", "then", "into", "over", "under", "between", "some", "many", "most", "very",
        "more", "can", "may", "not", "but", "all", "any", "one", "two", "three", "been", "being", "both",
        "each", "about", "after", "before", "during", "while", "often", "usually", "commonly",
        "known", "called", "named", "used", "use", "using", "made", "make", "including", "includes",
        "include", "within", "without", "through", "among", "several", "various", "well", "only", "form",
        "type", "types", "kind", "part", "parts", "since", "until", "because", "however", "although",
        "there", "these", "those", "they", "them", "will", "would", "could", "should", "its", "out", "per"
    )

    fun terms(text: String): List<String> =
        text.lowercase().split(splitter)
            .filter { it.length >= 3 && it !in stop }
            .map(::stem)
            .filter { it.length >= 3 && it !in stop }

    private fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
        else -> w
    }

    /** Same word, or the same stem (Solanaceae ~ Solanum). */
    fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        val common = a.commonPrefixWith(b).length
        return common >= 5 || (common >= 4 && minOf(a.length, b.length) <= 5)
    }

    // ── corpus (compact: interned terms, parallel int/float arrays) ───────────

    private class Corpus(docs: List<RelDoc>) {
        val n = docs.size
        private val vocab = HashMap<String, Int>()
        val termList = ArrayList<String>()
        val ids: Array<IntArray>
        val weights: Array<FloatArray>
        val idf: DoubleArray

        init {
            val df = ArrayList<Int>()
            val idsList = ArrayList<IntArray>(n)
            val tfList = ArrayList<FloatArray>(n)
            for (d in docs) {
                val m = HashMap<Int, Double>()
                fun add(text: String?, w: Double) {
                    if (text == null) return
                    for (t in terms(text)) {
                        val id = vocab.getOrPut(t) { termList.add(t); df.add(0); termList.size - 1 }
                        m[id] = (m[id] ?: 0.0) + w
                    }
                }
                add(d.title, 3.0)
                add(d.descr, 2.0)
                add(d.intro, 1.0)
                val keys = m.keys.sorted().toIntArray()
                for (k in keys) df[k] = df[k] + 1
                idsList.add(keys)
                tfList.add(FloatArray(keys.size) { m[keys[it]]!!.toFloat() })
            }
            idf = DoubleArray(termList.size) { ln((n + 1.0) / (df[it] + 0.5)).coerceAtLeast(0.05) }
            ids = idsList.toTypedArray()
            weights = Array(n) { i ->
                val tf = tfList[i]
                val ks = ids[i]
                var norm = 0.0
                val w = FloatArray(tf.size) { j ->
                    val x = (1.0 + ln(tf[j].toDouble())) * idf[ks[j]]
                    norm += x * x
                    x.toFloat()
                }
                if (norm > 0.0) {
                    val inv = (1.0 / sqrt(norm)).toFloat()
                    for (j in w.indices) w[j] *= inv
                }
                w
            }
        }

        val vocabSize: Int get() = termList.size
        fun idOf(term: String): Int? = vocab[term]
        fun isEmpty(i: Int) = ids[i].isEmpty()
        fun hasTerm(i: Int, id: Int) = java.util.Arrays.binarySearch(ids[i], id) >= 0

        /** Sum of the query's term weight present in document [i] (for absolute coverage). */
        fun queryMassIn(i: Int, q: DoubleArray): Double {
            var hit = 0.0
            for (id in ids[i]) {
                val w = q[id]
                if (w > 0.0) hit += w
            }
            return hit
        }

        fun dot(i: Int, q: DoubleArray): Double {
            var s = 0.0
            val ks = ids[i]
            val w = weights[i]
            for (j in ks.indices) s += w[j] * q[ks[j]]
            return s
        }

        fun addTo(acc: DoubleArray, i: Int) {
            val ks = ids[i]
            val w = weights[i]
            for (j in ks.indices) acc[ks[j]] += w[j].toDouble()
        }
    }

    private fun normalize(v: DoubleArray) {
        var s = 0.0
        for (x in v) s += x * x
        if (s <= 0.0) return
        val inv = 1.0 / sqrt(s)
        for (i in v.indices) v[i] *= inv
    }

    /** Sum of the documents' vectors, reduced to the [keep] strongest terms and normalised. */
    private fun centroid(corpus: Corpus, docIdx: List<Int>, keep: Int = 40): DoubleArray {
        val c = DoubleArray(corpus.vocabSize)
        for (i in docIdx) corpus.addTo(c, i)
        val used = c.indices.filter { c[it] > 0.0 }
        if (used.size > keep) {
            val top = used.sortedByDescending { c[it] }.take(keep).toHashSet()
            for (i in used) if (i !in top) c[i] = 0.0
        }
        normalize(c)
        return c
    }

    // ── topic scoring ─────────────────────────────────────────────────────────

    /**
     * @param queryWords the user's own words (already split, any case)
     * @param exclusions phrases that must NOT apply; a doc is excluded when it contains every
     *        word of any one phrase ("tobacco products" -> contains tobacco AND product)
     */
    fun score(docs: List<RelDoc>, queryWords: Collection<String>, exclusions: List<String>): Result {
        if (docs.isEmpty()) return Result(emptyMap(), emptyMap(), emptySet(), emptyList())
        val corpus = Corpus(docs)

        // Exclusions first: they are explicit user decisions.
        val excluded = HashSet<String>()
        val negPhrases = exclusions.map { phrase -> terms(phrase).map { corpus.idOf(it) } }
            .filter { it.isNotEmpty() && it.none { id -> id == null } }
        if (negPhrases.isNotEmpty()) {
            for ((i, d) in docs.withIndex()) {
                if (negPhrases.any { p -> p.all { id -> corpus.hasTerm(i, id!!) } }) excluded.add(d.key)
            }
        }

        // Initial query: the user's words, expanded to every corpus word with the same stem.
        val q0 = DoubleArray(corpus.vocabSize)
        var any = false
        for (w in queryWords.flatMap { terms(it) }.toSet()) {
            for (v in 0 until corpus.vocabSize) {
                if (sameWord(w, corpus.termList[v])) {
                    q0[v] = maxOf(q0[v], corpus.idf[v]); any = true
                }
            }
        }
        // Nothing in common with the user's words is *not* "unknown": those articles are exactly
        // the unrelated ones the random button must not show. Score them 0 so a strictness
        // threshold hides them instead of letting them through as rel = -1.
        if (!any) return Result(
            docs.associate { it.key to 0 }, docs.associate { it.key to 0 }, excluded, emptyList()
        )
        normalize(q0)
        // IDF mass of the query. Coverage is the fraction of this mass a document contains, so
        // matching a rare, distinctive word counts much more than matching a generic one.
        val queryMass = q0.sum()

        val s0 = DoubleArray(docs.size) { corpus.dot(it, q0) }
        val k = (docs.size * 0.04).toInt().coerceIn(8, 60)
        val seedIdx = s0.indices.filter { s0[it] > 0.0 }.sortedByDescending { s0[it] }.take(k)

        val finalScore: DoubleArray
        if (seedIdx.size >= 3) {
            val c = centroid(corpus, seedIdx)
            val q = DoubleArray(corpus.vocabSize) { 0.5 * q0[it] + 0.5 * c[it] }
            normalize(q)
            finalScore = DoubleArray(docs.size) { corpus.dot(it, q) }
        } else finalScore = s0

        val topIdx = finalScore.indices.sortedByDescending { finalScore[it] }.take(k)
        val mean = topIdx.map { finalScore[it] }.average().takeIf { it > 0.0 } ?: 1.0
        val rel = HashMap<String, Int>(docs.size)
        val cov = HashMap<String, Int>(docs.size)
        for ((i, d) in docs.withIndex()) {
            if (corpus.isEmpty(i)) {
                // No title/description/introduction words at all: never a reliable match.
                rel[d.key] = 0
                cov[d.key] = 0
                continue
            }
            rel[d.key] = (100.0 * finalScore[i] / mean).toInt().coerceIn(0, 300)
            val hit = if (queryMass <= 0.0) 0.0 else corpus.queryMassIn(i, q0)
            cov[d.key] = if (queryMass <= 0.0) 0
            else (100.0 * hit / queryMass).toInt().coerceIn(0, 100)
        }
        return Result(rel, cov, excluded, topIdx.map { docs[it].key })
    }

    // ── instant re-ranking from user feedback ─────────────────────────────────

    /**
     * Multiplier per candidate key: > 1 for articles like the liked ones, < 1 like the disliked
     * ones, 0 = drop (clearly resembles something the user rejected).
     */
    fun feedbackMultipliers(
        candidates: List<RelDoc>,
        liked: List<RelDoc>,
        disliked: List<RelDoc>
    ): Map<String, Double> {
        if (candidates.isEmpty() || (liked.isEmpty() && disliked.isEmpty())) return emptyMap()
        val all = LinkedHashMap<String, RelDoc>()
        for (d in candidates + liked + disliked) all.putIfAbsent(d.key, d)
        val list = all.values.toList()
        val corpus = Corpus(list)
        val index = list.withIndex().associate { it.value.key to it.index }
        val likeC = if (liked.isNotEmpty()) centroid(corpus, liked.map { index.getValue(it.key) }) else null
        val dislikeC = if (disliked.isNotEmpty()) centroid(corpus, disliked.map { index.getValue(it.key) }) else null
        val out = HashMap<String, Double>(candidates.size)
        for (c in candidates) {
            val i = index.getValue(c.key)
            val simL = likeC?.let { corpus.dot(i, it) } ?: 0.0
            val simD = dislikeC?.let { corpus.dot(i, it) } ?: 0.0
            // Clearly resembling something the user rejected: drop. Otherwise scale by taste.
            out[c.key] = if (simD >= DROP_SIMILARITY && simD > simL) 0.0
            else exp(FEEDBACK_STRENGTH * (simL - 1.5 * simD)).coerceIn(0.03, 4.0)
        }
        return out
    }
}
