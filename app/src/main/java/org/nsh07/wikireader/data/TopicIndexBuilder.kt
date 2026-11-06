package org.nsh07.wikireader.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Topics whose key starts with this prefix have no category: their library is built from full-text
 * search alone ("search:mid-century modern chairs"). For niches Wikipedia has no category for.
 */
const val SEARCH_TOPIC_PREFIX = "search:"

enum class IndexPhase { CRAWLING, CHECKING, INTROS, VIEWS }

/** What the user said about a topic in their own words (natural-language interests). */
data class TopicHint(val phrase: String, val exclusions: List<String>)

/** Live snapshot, emitted after every single request so the UI can always show movement. */
data class IndexProgress(
    val phase: IndexPhase,
    val depth: Int,
    val maxDepth: Int,
    val categoriesDone: Int,
    val categoriesKnown: Int,
    val articlesFound: Int,
    val requests: Int,
    val checked: Int,
    val toCheck: Int,
    /** Non-null while the builder is retrying / waiting for the connection. */
    val status: String?
)

/** Outcome of building one topic, shown in the result screen and the library breakdown. */
data class TopicBuildResult(
    val topic: String,
    val label: String,
    val resolvedCategory: String,
    val discovered: Int,
    val indexed: Int,
    val withImages: Int,
    val good: Int,
    val featured: Int,
    val categoriesFailed: Int,
    val batchesFailed: Int,
    val durationMs: Long,
    val error: String? = null,
    /** Articles removed because their description had nothing to do with the topic. */
    val filteredOut: Int = 0
) {
    val ok: Boolean get() = error == null && indexed > 0
    val incomplete: Boolean get() = ok && (categoriesFailed > 0 || batchesFailed > 0)
}

/**
 * Builds [TopicIndex] entries by walking a category's whole subtree (breadth-first) and then
 * batch-checking every article's size, lead image and Good/Featured status.
 *
 * Resilience: every request has its own timeout and is retried with exponential back-off for
 * minutes (not seconds) if the connection drops or Wikipedia rate-limits us (HTTP 429/5xx,
 * honouring Retry-After). Sub-categories that still fail are queued and retried at the end.
 * Gentle on the API: at most [MAX_IN_FLIGHT] requests in flight, hard caps on crawl size.
 *
 * One instance handles topics sequentially (it keeps per-topic counters).
 */
class TopicIndexBuilder(
    private val repo: WikipediaRepository,
    private val index: TopicIndex,
    /**
     * Page views are only used by the optional popularity preference. Fetching them for every
     * article costs a request per ten titles and PageViewInfo ignores larger batches, so the
     * default build skips them unless the user actually asked for popularity ordering.
     */
    private val includePageViews: Boolean = false
) {
    private var onProgress: (IndexProgress) -> Unit = {}

    @Volatile private var status: String? = null
    @Volatile private var phase = IndexPhase.CRAWLING
    @Volatile private var depth = 0
    @Volatile private var toCheck = 0
    private val catsDone = AtomicInteger()
    private val catsKnown = AtomicInteger()
    private val articlesFound = AtomicInteger()
    private val requests = AtomicInteger()
    private val checked = AtomicInteger()

    private fun emit() {
        onProgress(
            IndexProgress(
                phase, depth, MAX_DEPTH, catsDone.get(), catsKnown.get(), articlesFound.get(),
                requests.get(), checked.get(), toCheck, status
            )
        )
    }

    /** Never throws except for cancellation; failures are reported in the result. */
    suspend fun buildTopic(
        rootCategory: String,
        label: String,
        refresh: Boolean,
        /** The user's own words for this topic (natural-language interests), and what they excluded. */
        hint: TopicHint?,
        progress: (IndexProgress) -> Unit,
        /**
         * Called as soon as the topic is complete and usable, before the optional page-view
         * download. The UI can then offer "start browsing" even if the user later cancels the
         * optional phase.
         */
        onUsable: (TopicBuildResult) -> Unit = {}
    ): TopicBuildResult {
        onProgress = progress
        status = null
        phase = IndexPhase.CRAWLING
        depth = 0
        toCheck = 0
        catsDone.set(0); catsKnown.set(0); articlesFound.set(0); requests.set(0); checked.set(0)
        val started = System.currentTimeMillis()
        var resolved = rootCategory
        var categoriesFailed = 0
        var batchesFailed = 0

        fun fail(msg: String) = TopicBuildResult(
            rootCategory, label, resolved, 0, 0, 0, 0, 0, categoriesFailed, batchesFailed,
            System.currentTimeMillis() - started, msg
        )

        DebugLog.i(TAG, "=== BUILD TOPIC '$label' (category '$rootCategory', refresh=$refresh) ${DebugLog.memory()}")
        try {
            // Resume an interrupted build: the article list was already stored, only the
            // per-article details are missing (those that were fetched are skipped below).
            val searchPhrase = rootCategory.takeIf { it.startsWith(SEARCH_TOPIC_PREFIX) }?.removePrefix(SEARCH_TOPIC_PREFIX)
            val resumable = !refresh && index.isIncomplete(rootCategory)
            var titles: Map<String, Int> = if (resumable) index.membership(rootCategory) else emptyMap()
            if (resumable && titles.isNotEmpty()) {
                resolved = index.resolvedCategory(rootCategory) ?: rootCategory
                DebugLog.i(TAG, "RESUMING '$label': ${titles.size} stored articles, skipping the crawl")
                // Libraries built by older versions were unbounded (one real report had 84k rows
                // for "Plants"). Resuming those made the app fetch tens of thousands of optional
                // introductions and never finish. Cap the stored set once, keeping the most central
                // members; after that a resume is quick and the topic becomes usable immediately.
                if (titles.size > MAX_ARTICLES) {
                    val kept = index.pruneTopicMembership(rootCategory, MAX_ARTICLES)
                    if (kept > 0) titles = index.membership(rootCategory)
                    DebugLog.w(TAG, "resume of '$label' capped to ${titles.size} articles")
                }
            } else if (searchPhrase != null) {
                // No category: the library is whatever full-text search finds for the phrase.
                titles = expandBySearch(searchPhrase, emptySet(), SEARCH_ONLY_PAGES)
            } else {
                val firstCrawl = DebugLog.time(TAG, "crawl '$rootCategory'") { crawl(rootCategory, label, isRootAttempt = true) }
                titles = firstCrawl.titles
                categoriesFailed = firstCrawl.failedCategories
            }

            if (titles.isEmpty() && searchPhrase == null) {
                DebugLog.w(TAG, "'$rootCategory' is empty/unknown; searching category names for '$label'")
                val alternatives = try {
                    resilient("category-name-search '$label'", ENRICH_PATIENCE_MS) {
                        repo.searchCategoryNames(label)
                    }.query?.search?.map { it.title.removePrefix("Category:").replace(' ', '_') } ?: emptyList()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return fail("Could not search for a matching category (${describe(e)})")
                }
                DebugLog.i(TAG, "alternatives for '$label': $alternatives")
                for (alt in alternatives.filter { !it.equals(rootCategory, ignoreCase = true) }.take(3)) {
                    val found = crawl(alt, label, isRootAttempt = false)
                    if (found.titles.isNotEmpty()) {
                        titles = found.titles
                        categoriesFailed = found.failedCategories
                        resolved = alt
                        DebugLog.i(TAG, "using alternative category '$alt' (${titles.size} articles)")
                        break
                    }
                }
            }
            // Recall: for natural-language topics, also take what full-text search finds. Niche
            // topics are often intersections ("succulents of North America") that no single
            // category covers; the relevance model below filters whatever search over-reaches on.
            if (hint != null && !resumable && titles.size < MAX_ARTICLES / 2) {
                val extra = expandBySearch(hint.phrase, titles.keys)
                if (extra.isNotEmpty()) titles = titles + extra
            }
            // The crawl is concurrent, so a category response can overshoot the cap by a few
            // hundred titles before every worker notices. Trim once, keeping the shallowest
            // members, so stored libraries never exceed the documented bound.
            if (titles.size > MAX_ARTICLES) {
                titles = titles.entries
                    .sortedWith(compareBy({ it.value }, { it.key }))
                    .take(MAX_ARTICLES)
                    .associate { it.key to it.value }
                DebugLog.w(TAG, "trimmed '$label' to $MAX_ARTICLES articles")
            }
            if (titles.isEmpty()) return fail("No articles found for this topic on this Wikipedia")
            // Show the resume/search count too: a resumed build used to display “0 articles found”
            // while it was actually enriching thousands, which looked like a stuck build.
            articlesFound.set(titles.size)

            // Store the article list NOW (incomplete): if the app is killed during the long
            // detail-collection phase, nothing is lost and the next build resumes here.
            if (!resumable) index.replaceTopic(rootCategory, resolved, titles, complete = false)

            batchesFailed = DebugLog.time(TAG, "enrich ${titles.size} titles") { enrich(titles.keys.toList(), refresh) }

            // Relevance is local, fast, and the only step that makes category membership safe to
            // serve. Introductions used to be downloaded before this point, which is why broad
            // topics spent hours on network calls and never became usable. Title + short
            // description are enough to score the page; old libraries may still have intros, and
            // those are used when present.
            // Drop the "Category:" namespace word: it appears everywhere and inflated both the
            // relevance query and the coverage denominator.
            val topicWords = (searchPhrase ?: rootCategory.removePrefix("Category:")).replace('_', ' ')
            // Use the same natural-language normalization as topic discovery: "F1 drivers from
            // Finland" must contribute "Formula One" and "Finnish", not only the literal words
            // (otherwise a Finnish driver's description shares just the generic word "driver").
            val queryWords = listOf(topicWords, label) +
                    (hint?.let { InterestQuery.variants(it.phrase) } ?: emptyList())
            val filteredOut = DebugLog.time(TAG, "relevance '$label'") {
                scoreTopic(rootCategory, queryWords, hint?.exclusions ?: emptyList())
            }
            index.markComplete(rootCategory)

            // From here on the topic can serve random articles, even if the optional popularity
            // download below is cancelled or rate-limited. Report it immediately so the build UI
            // stops pretending nothing is ready.
            val usable = buildResult(
                rootCategory, label, resolved, titles.size, categoriesFailed, batchesFailed,
                filteredOut, System.currentTimeMillis() - started
            )
            onUsable(usable)
            DebugLog.i(TAG, "=== USABLE '$label': $usable ${DebugLog.memory()}")

            // Relevance reads more than the title and Wikidata description: a bounded sample of
            // article introductions is fetched after the library is usable, then the topic is
            // re-scored. Random picks stay available the whole time; the extra text makes later
            // picks noticeably more on-topic. Bounded so a huge library cannot turn this into a
            // second full build.
            val introTodo = index.titlesMissingIntros(titles.keys.toList(), refresh)
                .take(MAX_INTRO_LOOKUPS)
            if (introTodo.isNotEmpty()) {
                phase = IndexPhase.INTROS
                toCheck = introTodo.size
                checked.set(0)
                emit()
                DebugLog.time(TAG, "intros for ${introTodo.size} titles") { enrichIntros(introTodo) }
                DebugLog.time(TAG, "relevance with intros '$label'") {
                    scoreTopic(rootCategory, queryWords, hint?.exclusions ?: emptyList())
                }
            }

            // Page views are only needed by the optional popularity preference. They are fetched
            // after the library is usable, for a bounded sample, so they cannot block the build.
            if (includePageViews) {
                val viewsTodo = index.titlesMissingViews(titles.keys.toList(), refresh).take(MAX_VIEW_LOOKUPS)
                if (viewsTodo.isNotEmpty()) {
                    phase = IndexPhase.VIEWS
                    toCheck = viewsTodo.size
                    checked.set(0)
                    emit()
                    DebugLog.time(TAG, "views for ${viewsTodo.size} titles") { enrichViews(viewsTodo) }
                }
            }

            val result = buildResult(
                rootCategory, label, resolved, titles.size, categoriesFailed, batchesFailed,
                filteredOut, System.currentTimeMillis() - started
            )
            DebugLog.i(TAG, "=== DONE '$label': $result ${DebugLog.memory()}")
            return result
        } catch (e: CancellationException) {
            DebugLog.w(TAG, "build of '$label' cancelled")
            throw e
        } catch (e: Exception) {
            DebugLog.e(TAG, "build of '$label' failed", e)
            return fail(describe(e))
        } finally {
            status = null
        }
    }

    private fun buildResult(
        topic: String,
        label: String,
        resolved: String,
        discovered: Int,
        categoriesFailed: Int,
        batchesFailed: Int,
        filteredOut: Int,
        durationMs: Long
    ): TopicBuildResult {
        val stat = index.topicStats().firstOrNull { it.topic == topic }
        return TopicBuildResult(
            topic = topic, label = label, resolvedCategory = resolved,
            discovered = discovered, indexed = stat?.indexed ?: 0,
            withImages = stat?.withImages ?: 0, good = stat?.good ?: 0,
            featured = stat?.featured ?: 0, categoriesFailed = categoriesFailed,
            batchesFailed = batchesFailed, durationMs = durationMs,
            filteredOut = filteredOut
        )
    }

    // ── Phase 1: category subtree crawl ───────────────────────────────────────

    private class Children(val pages: List<String>, val subcats: List<String>)
    /** title -> shortest category distance from the topic root. */
    private class CrawlResult(val titles: Map<String, Int>, val failedCategories: Int)

    private suspend fun crawl(root: String, label: String, isRootAttempt: Boolean): CrawlResult = coroutineScope {
        phase = IndexPhase.CRAWLING
        // Words that identify the topic; deep sub-categories must relate to them (or to their own
        // parent) to be followed, otherwise the crawl leaks into unrelated parts of Wikipedia
        // (e.g. nightshades -> potatoes -> potato pests).
        val anchor = tokens(root) + tokens(label)
        // A generic root ("History", "Culture", "Society") says almost nothing about its direct
        // children. Trusting them unconditionally is how "Ancient Egypt" ended up in unrelated
        // topics; require a shared content word there too.
        // Only a root made *entirely* of generic words is gated; \"Ancient Egypt\" is specific
        // because of \"Egypt\", while \"History\" or \"Ancient history\" are not.
        val rootTerms = RelevanceModel.terms("$root $label")
        val genericRoot =
            rootTerms.isNotEmpty() && rootTerms.all { it in genericTopicWords }
        val blocked = AtomicInteger(0)
        val visited = ConcurrentHashMap.newKeySet<String>()
        visited.add(root)
        val titles = ConcurrentHashMap<String, Int>()
        val failed = ConcurrentLinkedQueue<Pair<String, Int>>()
        val gate = Semaphore(CONCURRENCY)
        catsKnown.incrementAndGet()

        suspend fun processLevel(cats: List<String>, atDepth: Int): List<String> {
            val next = ConcurrentLinkedQueue<String>()
            cats.map { cat ->
                async {
                    gate.withPermit {
                        val r = fetchChildren(cat, isRoot = isRootAttempt && atDepth == 0 && cat == root)
                        if (r == null) {
                            failed.add(cat to atDepth)
                        } else {
                            for (p in r.pages) {
                                if (titles.size >= MAX_ARTICLES) break
                                titles.merge(p, atDepth) { old, new -> minOf(old, new) }
                            }
                            articlesFound.set(titles.size)
                            if (atDepth < MAX_DEPTH) {
                                val parentTokens = tokens(cat)
                                for (sc in r.subcats) {
                                    if (visited.size >= MAX_CATEGORIES || !isUsableSubcategory(sc)) continue
                                    // Never descend into another continent's branch just because
                                    // the parent category is loosely worded. This is the direct
                                    // fix for "South American history" pulling in African history.
                                    if (TopicRegionGuard.conflicts(sc, "$root $label")) {
                                        blocked.incrementAndGet()
                                        DebugLog.d(TAG, "REGION blocked '$sc' (child of '$cat' at depth $atDepth)")
                                        continue
                                    }
                                    // Direct children of a specific topic are trusted; deeper ones
                                    // (and children of generic roots) must share a word with their
                                    // parent or with the topic itself.
                                    val st = tokens(sc)
                                    val relevant = (atDepth == 0 && !genericRoot) ||
                                            related(st, parentTokens) || related(st, anchor)
                                    if (!relevant) {
                                        blocked.incrementAndGet()
                                        DebugLog.d(TAG, "GATE blocked '$sc' (child of '$cat' at depth $atDepth): unrelated to topic")
                                        continue
                                    }
                                    if (visited.add(sc)) {
                                        next.add(sc)
                                        catsKnown.incrementAndGet()
                                    }
                                }
                            }
                        }
                        catsDone.incrementAndGet()
                        emit()
                    }
                }
            }.awaitAll()
            return next.toList()
        }

        suspend fun bfs(start: List<String>, startDepth: Int) {
            var level = start
            var d = startDepth
            while (level.isNotEmpty() && d <= MAX_DEPTH &&
                visited.size < MAX_CATEGORIES && titles.size < MAX_ARTICLES
            ) {
                depth = d
                val t0 = System.nanoTime()
                val levelSize = level.size
                level = processLevel(level, d)
                DebugLog.i(
                    TAG,
                    "PERF level $d: $levelSize categories in ${(System.nanoTime() - t0) / 1_000_000} ms, " +
                            "next level ${level.size}, articles so far ${titles.size}, failed ${failed.size}, " +
                            "unrelated sub-categories skipped so far ${blocked.get()}"
                )
                d++
            }
        }

        bfs(listOf(root), 0)

        // Anything that failed even after minutes of retrying gets two more chances at the end.
        var pass = 0
        while (failed.isNotEmpty() && pass < 2) {
            pass++
            val retry = failed.toList()
            failed.clear()
            status = "Retrying ${retry.size} category requests that failed (pass $pass)"
            DebugLog.w(TAG, "retry pass $pass for ${retry.size} failed categories")
            emit()
            for ((d, group) in retry.groupBy({ it.second }, { it.first })) bfs(group, d)
        }
        status = null
        if (failed.isNotEmpty()) {
            DebugLog.e(TAG, "${failed.size} categories could not be read: ${failed.take(10).map { it.first }}")
        }
        DebugLog.i(TAG, "crawl '$root' finished: ${titles.size} articles, ${visited.size} categories, ${blocked.get()} unrelated sub-categories skipped")
        CrawlResult(HashMap(titles), failed.size)
    }

    /** @return null if the category could not be read (non-root); throws for the root. */
    private suspend fun fetchChildren(category: String, isRoot: Boolean): Children? {
        val pages = ArrayList<String>()
        val subs = ArrayList<String>()
        var cont: WikiContinue? = null
        var n = 0
        try {
            do {
                val t0 = System.nanoTime()
                val resp = resilient(
                    "children '$category' p${n + 1}",
                    if (isRoot) ROOT_PATIENCE_MS else SUB_PATIENCE_MS
                ) { repo.getCategoryChildren(category, cont) }
                requests.incrementAndGet()
                var subN = 0
                var pageN = 0
                resp.query?.categorymembers?.forEach { m ->
                    if (m.type == "subcat") {
                        subs.add(m.title.removePrefix("Category:").replace(' ', '_')); subN++
                    } else if (m.type == "page" && m.ns == 0) {
                        pages.add(m.title); pageN++
                    }
                }
                cont = resp.cont
                n++
                DebugLog.d(
                    TAG,
                    "PERF '$category' p$n: $pageN articles, $subN subcats, " +
                            "${(System.nanoTime() - t0) / 1_000_000} ms, more=${cont?.cmcontinue != null}"
                )
                emit()
            } while (cont?.cmcontinue != null && n < MAX_REQUESTS_PER_CATEGORY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.e(TAG, "category '$category' failed permanently (root=$isRoot): ${describe(e)}", e)
            if (isRoot) throw e
            return null
        }
        return Children(applyNoiseFilter(pages), subs)
    }

    // ── Phase 2: size / image / quality for every new article ─────────────────

    /** @return number of batches that failed even after retries. */
    private suspend fun enrich(titles: List<String>, refresh: Boolean): Int {
        phase = IndexPhase.CHECKING
        status = null
        val known = if (refresh) emptySet<String>() else index.knownTitles(titles)
        val todo = titles.filter { it !in known }
        toCheck = todo.size
        checked.set(0)
        DebugLog.i(TAG, "enrich: ${titles.size} titles, ${known.size} already known, ${todo.size} to check")
        emit()
        if (todo.isEmpty()) return 0

        val gate = Semaphore(CONCURRENCY)
        val failed = ConcurrentLinkedQueue<List<String>>()

        suspend fun runBatches(batches: List<List<String>>) {
            coroutineScope {
                batches.map { batch ->
                    async {
                        gate.withPermit {
                            val t0 = System.nanoTime()
                            val pages = try {
                                resilient("page-info x${batch.size}", ENRICH_PATIENCE_MS) {
                                    repo.getPageInfo(batch)
                                }.query?.pages ?: emptyList()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                DebugLog.e(TAG, "page-info batch failed permanently: ${describe(e)}", e)
                                failed.add(batch)
                                null
                            }
                            if (pages != null) {
                                requests.incrementAndGet()
                                index.upsertArticles(
                                    pages.filter { it.title.isNotEmpty() }.map { p ->
                                        if (p.missing || p.redirect) {
                                            // Redirects/deleted pages would show up as duplicates of
                                            // another article: store a never-pickable marker (bytes -1).
                                            ArticleRow(p.title, -1, false, 0, descr = "")
                                        } else {
                                            val tier = when {
                                                p.categories.any { it.title.endsWith("Featured articles") } -> 2
                                                p.categories.any { it.title.endsWith("Good articles") } -> 1
                                                else -> 0
                                            }
                                            ArticleRow(p.title, p.length, p.thumbnail != null, tier, descr = p.description ?: "")
                                        }
                                    }
                                )
                                DebugLog.d(
                                    TAG,
                                    "PERF page-info batch of ${batch.size}: ${pages.size} pages, " +
                                            "${(System.nanoTime() - t0) / 1_000_000} ms"
                                )
                            }
                            checked.addAndGet(batch.size)
                            emit()
                        }
                    }
                }.awaitAll()
            }
        }

        runBatches(todo.chunked(50))
        val retry = failed.toList()
        failed.clear()
        if (retry.isNotEmpty()) {
            status = "Retrying ${retry.size} article batches that failed"
            DebugLog.w(TAG, "retrying ${retry.size} failed page-info batches")
            checked.addAndGet(-retry.sumOf { it.size })
            emit()
            runBatches(retry)
        }
        status = null
        return failed.size
    }

    /** Articles found by full-text search for [phrase] that the category crawl did not contain. */
    private suspend fun expandBySearch(
        phrase: String,
        existing: Set<String>,
        maxPages: Int = SEARCH_PAGES
    ): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        phase = IndexPhase.CRAWLING
        status = "Searching for more articles about \"$phrase\""
        emit()
        for (page in 0 until maxPages) {
            val hits = try {
                resilient("search '$phrase' p$page", SUB_PATIENCE_MS) {
                    repo.searchArticlesPaged(phrase, page * 50)
                }.query?.search ?: emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w(TAG, "search expansion stopped: ${describe(e)}")
                break
            }
            if (hits.isEmpty()) break
            // Reject page types that never belong in a reading library (lists, disambiguations,
            // maintenance pages) before doing any relevance work.
            val clean = applyNoiseFilter(hits.map { it.title }).toHashSet()
            for (hit in hits) {
                val t = hit.title
                // Search can return wildly unrelated pages for a short/ambiguous phrase. The title
                // must share a *content* word with the user's phrase; generic words ("ancient",
                // "history", "world") do not count.
                if (t !in clean || !sharesContentWord(t, phrase)) continue
                if (t !in existing && t !in out) {
                    // Search results are less direct than category members; always depth 2 so they
                    // are never mistaken for core members.
                    out[t] = 2
                }
            }
            if (hits.size < 50) break
        }
        status = null
        DebugLog.i(TAG, "search expansion for '$phrase': ${out.size} extra articles (top: ${out.keys.take(8)})")
        return out
    }

    /** Fetches first paragraphs for [todo]. Never fails the build. */
    private suspend fun enrichIntros(todo: List<String>) {
        if (todo.isEmpty()) return
        val gate = Semaphore(EXTRA_CONCURRENCY)
        var failedBatches = 0
        coroutineScope {
            // exlimit=max accepts 20 titles per request.
            todo.chunked(20).map { batch ->
                async {
                    gate.withPermit {
                        val t0 = System.nanoTime()
                        val result = try {
                            resilient("intros x${batch.size}", VIEWS_PATIENCE_MS) { repo.getIntros(batch) }
                                .query?.pages?.mapNotNull { p ->
                                    p.extract?.takeIf { it.isNotBlank() }?.let { p.title to it }
                                }?.toMap() ?: emptyMap()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            DebugLog.w(TAG, "introductions unavailable for a batch: ${describe(e)}")
                            synchronized(this@TopicIndexBuilder) { failedBatches++ }
                            emptyMap<String, String>()
                        }
                        if (result.isNotEmpty()) index.updateIntros(result)
                        requests.incrementAndGet()
                        DebugLog.d(TAG, "PERF intro batch of ${batch.size}: ${result.size} with text, ${(System.nanoTime() - t0) / 1_000_000} ms")
                        checked.addAndGet(batch.size)
                        emit()
                    }
                }
            }.awaitAll()
        }
        if (failedBatches > 0) DebugLog.w(TAG, "$failedBatches intro batches failed; relevance uses titles/descriptions for those articles")
    }

    /** Fetches page views for [todo]. Never fails the build. */
    private suspend fun enrichViews(todo: List<String>) {
        if (todo.isEmpty()) return
        val gate = Semaphore(EXTRA_CONCURRENCY)
        var failedBatches = 0
        coroutineScope {
            // PageViewInfo silently returns at most ten pages per request regardless of
            // `pvilimit` (which it does not support) and asks for a continuation for the rest.
            // Chunking to ten avoids requesting forty titles that are then discarded.
            todo.chunked(10).map { batch ->
                async {
                    gate.withPermit {
                        val t0 = System.nanoTime()
                        val result = try {
                            resilient("page-views x${batch.size}", VIEWS_PATIENCE_MS) { repo.getPageViews(batch) }
                                .query?.pages?.mapNotNull { p ->
                                    p.pageviews?.let { days -> p.title to days.values.sumOf { it ?: 0 } }
                                }?.toMap() ?: emptyMap()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            DebugLog.w(TAG, "page views unavailable for a batch: ${describe(e)}")
                            synchronized(this@TopicIndexBuilder) { failedBatches++ }
                            emptyMap<String, Int>()
                        }
                        if (result.isNotEmpty()) index.updateViews(result)
                        requests.incrementAndGet()
                        DebugLog.d(TAG, "PERF page-views batch of ${batch.size}: ${result.size} with data, ${(System.nanoTime() - t0) / 1_000_000} ms")
                        checked.addAndGet(batch.size)
                        emit()
                    }
                }
            }.awaitAll()
        }
        if (failedBatches > 0) DebugLog.w(TAG, "$failedBatches page-view batches failed; those articles keep unknown popularity")
    }

    // ── resilient networking ──────────────────────────────────────────────────

    /**
     * Runs [block] with a timeout; on a connection problem / rate limit waits (exponential
     * back-off, honouring Retry-After) and tries again until [patienceMs] has passed.
     */
    private suspend fun <T : Any> resilient(label: String, patienceMs: Long, block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        var attempt = 0
        while (true) {
            attempt++
            // After a rate limit every worker waits, not just the request that was refused.
            val pause = cooldownUntil.get() - System.currentTimeMillis()
            if (pause > 0) delay(pause)
            awaitSlot()
            // Politeness gate shared by every builder: token bucket + global cool-down. This caps
            // the request *rate*, not just the number of requests in flight (the old behaviour
            // still hammered Wikipedia on a fast connection and earned 429s).
            // Release the in-flight slot if this coroutine is cancelled while waiting for a token.
            // Without this, cancelling a build leaks slots permanently and later builds eventually
            // stop sending requests because every worker is stuck in awaitSlot().
            try {
                WikiRateLimiter.shared.acquire()
            } catch (e: Throwable) {
                releaseSlot()
                throw e
            }
            val t0 = System.nanoTime()
            try {
                val r = try {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) { block() }
                        ?: throw IOException("no response within ${REQUEST_TIMEOUT_MS / 1000}s")
                } finally {
                    releaseSlot()
                }
                onRequestSucceeded()
                WikiRateLimiter.shared.onSuccess()
                if (attempt > 1) {
                    DebugLog.i(TAG, "$label succeeded on attempt $attempt")
                    status = null
                }
                return r
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val ms = (System.nanoTime() - t0) / 1_000_000
                val elapsed = System.currentTimeMillis() - start
                val retriable = isRetriable(e, attempt)
                DebugLog.w(
                    TAG,
                    "$label attempt $attempt failed after ${ms}ms (retriable=$retriable, " +
                            "waited ${elapsed / 1000}s of ${patienceMs / 1000}s): ${e.javaClass.simpleName}: ${e.message}"
                )
                if (!retriable || elapsed >= patienceMs) throw e
                val wait = backoffMs(attempt, e)
                if (e is HttpException && (e.code() == 429 || e.code() == 503)) {
                    onRateLimited()
                    WikiRateLimiter.shared.onThrottled(wait)
                    val until = System.currentTimeMillis() + wait
                    while (true) {
                        val cur = cooldownUntil.get()
                        if (until <= cur || cooldownUntil.compareAndSet(cur, until)) break
                    }
                }
                status = "Connection problem (${describe(e)}). Retrying in ${wait / 1000}s, attempt ${attempt + 1}"
                emit()
                delay(wait)
            }
        }
    }

    // ── adaptive request limit ────────────────────────────────────────────────
    // Without this, a 429 made every parallel request wait and then all of them restarted at the
    // same moment, which got refused again. Now the number of simultaneous requests is halved on
    // every 429/503 and grows back by one after each 20 successful requests (AIMD, like TCP).

    private suspend fun awaitSlot() {
        while (true) {
            val cur = activeRequests.get()
            if (cur < requestLimit.get() && activeRequests.compareAndSet(cur, cur + 1)) return
            delay(25)
        }
    }

    private fun releaseSlot() {
        activeRequests.decrementAndGet()
    }

    private fun onRateLimited() {
        successStreak.set(0)
        while (true) {
            val cur = requestLimit.get()
            val next = maxOf(MIN_IN_FLIGHT, cur / 2)
            if (next == cur || requestLimit.compareAndSet(cur, next)) {
                DebugLog.w(TAG, "rate limited: simultaneous requests $cur -> $next")
                return
            }
        }
    }

    private fun onRequestSucceeded() {
        if (successStreak.incrementAndGet() < 20) return
        successStreak.set(0)
        while (true) {
            val cur = requestLimit.get()
            if (cur >= MAX_IN_FLIGHT || requestLimit.compareAndSet(cur, cur + 1)) return
        }
    }

    private fun isRetriable(e: Exception, attempt: Int): Boolean = when (e) {
        // Do not retry a persistent rate limit forever: after a handful of attempts the topic is
        // reported as failed instead of holding the build for ten minutes.
        is HttpException -> e.code() in RETRIABLE_HTTP && (e.code() != 429 || attempt <= 8)
        is IOException -> true
        is SerializationException -> attempt < 3 // truncated JSON from a dropped connection
        else -> false
    }

    private fun backoffMs(attempt: Int, e: Exception): Long {
        var wait = minOf(1_500L shl minOf(attempt - 1, 5), 20_000L)
        if (e is HttpException) {
            val retryAfter = e.response()?.headers()?.get("Retry-After")
                ?.let { parseRetryAfterMs(it) }
            if (retryAfter != null) wait = maxOf(wait, minOf(retryAfter, 120_000L))
            else if (e.code() == 429) wait = maxOf(wait, 10_000L)
        }
        return wait + (0..500).random()
    }

    /**
     * `Retry-After` is either a number of seconds or an HTTP date (RFC 1123). Only the numeric
     * form used to be understood, so a date was ignored and the crawler retried too early.
     */
    private fun parseRetryAfterMs(value: String): Long? {
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { return (it * 1_000L).coerceAtLeast(0L) }
        return try {
            val whenMs = java.time.ZonedDateTime.parse(
                trimmed, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant().toEpochMilli()
            (whenMs - System.currentTimeMillis()).coerceAtLeast(0L)
        } catch (e: Exception) {
            null
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is UnknownHostException -> "no internet connection"
        is ConnectException -> "cannot connect"
        is SocketTimeoutException -> "timeout"
        is HttpException -> "HTTP ${e.code()}"
        is IOException -> e.message ?: "network error"
        else -> e.message ?: e.javaClass.simpleName
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private val noisePrefixes = listOf(
        "List of ", "Lists of ", "Index of ", "Indices of ", "Outline of ", "Timeline of "
    )

    private fun applyNoiseFilter(titles: List<String>): List<String> =
        titles.filter { t ->
            noisePrefixes.none { t.startsWith(it, ignoreCase = true) } &&
                    !t.contains("(disambiguation)", ignoreCase = true)
        }

    private val junkSubcatMarkers = listOf(
        "stubs", "wikipedia", "articles with", "articles needing", "articles lacking",
        "templates", "redirects", "list of", "lists of", "maintenance", "commons",
        "navigational", "portal", "wikiproject", "unassessed", "disambiguation",
        "wikidata", "user pages", "by country", "by nationality", "in popular culture",
        "in fiction"
    )

    /**
     * Scores every member of [topic] with the local relevance model (TF-IDF over title,
     * description and introduction, with feedback from the best matches) and removes members that
     * match one of the user's exclusions.
     * @return number of removed articles
     */
    private fun scoreTopic(topic: String, queryWords: List<String>, exclusions: List<String>): Int {
        val docs = index.memberDocs(topic)
        val result = RelevanceModel.score(docs, queryWords, exclusions)
        val context = queryWords.joinToString(" ")
        // A category tree can contain a cross-link to another continent (or an article about a
        // different region that happens to sit under the same parent). Relevance alone often lets
        // those through because they share the generic word "history". Drop only clear conflicts:
        // articles that mention another region and never mention the topic's own region.
        val regionDrops = docs.asSequence()
            .filter { TopicRegionGuard.conflicts("${it.title} ${it.descr.orEmpty()}", context) }
            .map { it.key }
            .toSet()
        val drop = result.excluded + regionDrops
        val scored = result.rel.values.count { it > 0 }
        if (scored == 0 && docs.isNotEmpty()) {
            // The query and every title/description share no usable word. That is a mismatch in
            // vocabulary, not proof that the whole category is unrelated. Leaving rel = 0 would
            // make the library appear built but return no article at the default strictness.
            // Keep the region-filtered members as unscored (the same state as "could not judge").
            index.applyRelevance(topic, emptyMap(), emptyMap(), drop)
            index.resetRelevance(topic)
            DebugLog.w(
                TAG,
                "relevance '$topic': no shared vocabulary; kept ${docs.size - drop.size} " +
                        "members unscored so the library can still serve articles"
            )
        } else {
            index.applyRelevance(topic, result.rel, result.cov, drop)
        }
        DebugLog.i(
            TAG,
            "relevance '$topic': ${docs.size} articles, $scored scored, seeds=${result.seedKeys.take(8)}, " +
                    "excluded ${result.excluded.size}, other region ${regionDrops.size} " +
                    "${regionDrops.take(10)}"
        )
        val low = result.rel.values.count { it in 0..14 }
        val lowCov = result.cov.values.count { it in 0..9 }
        DebugLog.i(
            TAG,
            "relevance '$topic': $low articles score under 15%, $lowCov share under 10% of the topic's words " +
                    "(hidden at 'balanced' strictness and above)"
        )
        return drop.size
    }

    private val wordSplit = Regex("[^\\p{L}\\p{N}]+")
    private val stopWords = setOf("of", "in", "by", "and", "the", "from", "to", "for", "with", "on", "at", "as")

    /**
     * Words that describe a broad field rather than a topic. A search hit that shares only one of
     * these with the user's phrase is not evidence of relevance ("ancient" does not make Ancient
     * Egypt part of "ancient South American civilizations").
     */
    private val genericTopicWords = setOf(
        "ancient", "history", "historical", "people", "culture", "cultural", "civilization",
        "civilisation", "world", "country", "countries", "region", "regions", "society",
        "list", "lists", "timeline", "overview", "general"
    )

    /** True when [title] shares at least one non-generic content word with [phrase]. */
    private fun sharesContentWord(title: String, phrase: String): Boolean {
        val wanted = RelevanceModel.terms(phrase).filterNot { it in genericTopicWords }
        if (wanted.isEmpty()) return true // nothing specific to check against
        val have = RelevanceModel.terms(title)
        return wanted.any { w -> have.any { RelevanceModel.sameWord(w, it) } }
    }

    /** Lower-case, de-pluralised content words (>= 4 letters) of a category / label. */
    private fun tokens(name: String): Set<String> =
        name.lowercase().split(wordSplit)
            .filter { it.length >= 4 && it !in stopWords }
            .map { it.trimEnd('s') }
            .toSet()

    /** Two token sets share a word, or words with a long common stem (Solanaceae ~ Solanum). */
    private fun related(a: Set<String>, b: Set<String>): Boolean = a.any { x ->
        b.any { y ->
            x == y || run {
                val common = x.commonPrefixWith(y).length
                common >= 5 || (common >= 4 && minOf(x.length, y.length) <= 5)
            }
        }
    }

    private fun isUsableSubcategory(name: String): Boolean {
        val t = name.replace('_', ' ').lowercase()
        return junkSubcatMarkers.none { t.contains(it) }
    }

    companion object {
        private const val TAG = "IndexBuilder"
        private const val MAX_IN_FLIGHT = 12             // upper bound for simultaneous requests overall
        private const val MIN_IN_FLIGHT = 1

        // Shared by every builder in this process: what was learned about Wikipedia's limits
        // stays valid for the next topic.
        private val requestLimit = java.util.concurrent.atomic.AtomicInteger(MAX_IN_FLIGHT)
        private val activeRequests = java.util.concurrent.atomic.AtomicInteger(0)
        private val successStreak = java.util.concurrent.atomic.AtomicInteger(0)
        /** Wall-clock time before which no request is sent (set when Wikipedia answers 429/503). */
        private val cooldownUntil = java.util.concurrent.atomic.AtomicLong(0L)
        private const val CONCURRENCY = 8                // crawl + page-info requests in flight
        private const val EXTRA_CONCURRENCY = 4          // optional page views after the topic is usable
        private const val MAX_DEPTH = 4
        // A library of a few thousand articles is plenty for random reading; bounding the crawl
        // here is what makes a broad topic finish in minutes instead of crawling forever.
        private const val MAX_CATEGORIES = 1_200
        const val MAX_ARTICLES = 8_000
        // Popularity is a preference, not a requirement: 2,000 sampled views are enough for a
        // percentile and keep the optional phase short.
        private const val MAX_VIEW_LOOKUPS = 2_000
        // Introductions are the strongest cheap relevance signal. 1,200 articles is enough to
        // learn the topic's vocabulary without turning a big library into a second crawl.
        private const val MAX_INTRO_LOOKUPS = 1_200
        private const val MAX_REQUESTS_PER_CATEGORY = 10
        private const val SEARCH_PAGES = 2                  // 2 x 50 = top 100 search results
        private const val SEARCH_ONLY_PAGES = 5             // search-only topics: top 250
        private const val REQUEST_TIMEOUT_MS = 45_000L
        private const val ROOT_PATIENCE_MS = 10 * 60_000L   // the topic's own category
        private const val ENRICH_PATIENCE_MS = 10 * 60_000L
        private const val SUB_PATIENCE_MS = 3 * 60_000L     // one sub-category; retried later
        private const val VIEWS_PATIENCE_MS = 90_000L       // optional data: don't stall the build
        private val RETRIABLE_HTTP = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}
