# WikiReader v17 — Code Audit, Bug Analysis and Fix Plan

**Target:** `E:\Software_projects_etc\Wikireader_NEW_FALL_2026\18.10.2026\WikiReader-fixed-v17_AI_SLIM`
**Package:** `org.nsh07.wikireader` · **DB version:** 7 (`topic_index_<lang>.db`)
**Date:** 2026-10-22
**Status:** implemented in this build (compile + 36 unit tests + debug APK); see “Implementation status” at the end. No device/emulator profiling was possible.

This document covers:

1. the 429 throttling during topic builds,
2. why ~10 % of random articles are unrelated,
3. why plant phylogenetic trees are missing,
4. topic deselection without deleting library data,
5. optional, off-by-default behaviour learning,
6. additional correctness, database and performance/FPS problems.

Line numbers refer to the files in the target tree as it exists today. The proposed code below is the design that was applied; the final implementation status and exact parameters are recorded at the end.

---

## 0. Executive summary

| # | Severity | Problem | Primary location | Root cause |
|---|----------|---------|------------------|-----------|
| 1 | **P0** | 429 storms during topic build | `TopicIndexBuilder.kt:752-776`, `569-673`, `AppContainer.kt:52-54` | Up to 10 concurrent requests, no requests/second limit, no `maxlag`, broken `Retry-After` parsing, retry storms |
| 2 | **P0** | ~10 % unrelated random articles | `HomeScreenViewModel.kt:1111-1121`, `TopicIndexBuilder.kt:172-178/444-475`, `RelevanceModel.kt:209-215`, `TopicIndex.kt:514` | One path falls back to *global* Wikipedia random; search expansion injects 300 loosely-matched pages; relevance is relative and the threshold is very low |
| 3 | **P1** | Plant cladograms never render | `HomeScreenViewModel.kt:1781-1804`, `ParsedBodyText.kt:139-150`, `tableAndInfoboxUtil.kt:28-147` | A `{|…|}` table is emitted as one opaque block, so `{{clade}}` inside a table cell is never extracted; `Cladogram` then silently returns |
| 4 | **P1** | Deselecting a topic deletes it | `IndexUi.kt:244`, `RandomTopicsScreen.kt:333-339`, `HomeScreenViewModel.kt:1898-1903`, `TopicIndex.kt:417-428` | The only "remove" action calls `deleteTopic()`; the preference stores *only* enabled topics |
| 5 | **P2** | No behaviour-based personalization | `HomeScreenViewModel.kt:465-537`, `PreferencesState.kt`, `TopicIndex.kt` | Only explicit 👍/👎 votes exist; no dwell time, scroll depth or settings toggle |
| 6 | **P2** | Rebuild empties a working library | `TopicIndex.kt:334-364`, `HomeScreenViewModel.kt:551-559` | `replaceTopic()` deletes membership at the *start* of the build; `isBuilt()` accepts `complete = 0` |
| 7 | **P2** | Articles are marked seen before they load | `HomeScreenViewModel.kt:617-641` | `markSeen()` runs before `getPageData()`; three failed fetches permanently burn three articles |
| 8 | **P2** | Random pick is DB- and CPU-heavy | `TopicIndex.kt:499-542`, `HomeScreenViewModel.kt:566-620` | `ORDER BY RANDOM()` twice per shuffle over the whole library, `NOT IN (SELECT … seen)`, feedback re-scoring of 400 docs |
| 9 | **P3** | Selection lost on process death | `RandomTopicsScreen.kt:132-141` | Plain `remember`, not `rememberSaveable` and not derived from the DB |
| 10 | **P3** | Dead legacy code | `HomeScreenViewModel.kt:1932-2272` | Old live category walk, filtering and popularity helpers are unreachable |
| 11 | **P3** | Collapsed tables are parsed eagerly | `AsyncWikitable.kt:84-94` | `LaunchedEffect(text)` parses every table even while collapsed |
| 12 | **P3** | CPU parsing runs on `Dispatchers.IO` | `HomeScreenViewModel.kt:1701-1702` | Wikitext → `AnnotatedString` is CPU-bound; it competes with disk/network work |

The five user-reported issues are addressed in §1–§5. Everything else is in §6–§7.

---

## 1. 429 throttling while building the topics database

### 1.1 What the code does today

`TopicIndexBuilder.kt` limits *simultaneous* requests, not *requests per second*:

```kotlin
// TopicIndexBuilder.kt:752-776
private const val MAX_IN_FLIGHT = 10             // upper bound for simultaneous requests overall
private const val MIN_IN_FLIGHT = 2
private const val CONCURRENCY = 6                // crawl + page-info requests in flight
private const val EXTRA_CONCURRENCY = 4          // page views (they run together with introductions)
private const val INTRO_CONCURRENCY = 6          // introductions: 20 per request, the slowest phase
private val requestLimit = AtomicInteger(MAX_IN_FLIGHT)
private val activeRequests = AtomicInteger(0)
```

Every worker enters through `resilient()`:

```kotlin
// TopicIndexBuilder.kt:569-586
private suspend fun <T : Any> resilient(label: String, patienceMs: Long, block: suspend () -> T): T {
    ...
    awaitSlot()                       // only checks activeRequests < requestLimit
    try { withTimeoutOrNull(REQUEST_TIMEOUT_MS) { block() } }
    finally { releaseSlot() }
```

`awaitSlot()` polls every 25 ms:

```kotlin
// TopicIndexBuilder.kt:625-631
private suspend fun awaitSlot() {
    while (true) {
        val cur = activeRequests.get()
        if (cur < requestLimit.get() && activeRequests.compareAndSet(cur, cur + 1)) return
        delay(25)
    }
}
```

The client also allows a lot more parallelism than the builder assumes:

```kotlin
// AppContainer.kt:52-54
val dispatcher = Dispatcher().apply {
    maxRequests = 48
    maxRequestsPerHost = 12
}
```

And the build is bursty by design: `Semaphore(6)` for crawling, then `Semaphore(6)` intros **and** `Semaphore(4)` page-views run at the same time (`TopicIndexBuilder.kt:500,532`), all governed by a global limit that starts at 10.

### 1.2 Why this hits 429

1. **Concurrency is 3–4× the documented limit.** Wikimedia's API etiquette asks clients to *"limit the number of concurrent requests to 3 or fewer"* and to make requests in series where possible. This builder starts at 10 and only halves after a 429 (`onRateLimited()`, lines 637-647).
2. **There is no rate limit at all.** With 10 in-flight requests and fast category responses (~50–150 ms), the app can issue 50–150 requests/second in short bursts. The UA-only allowance is around **200 requests/minute (~3.3/s)**. Ten concurrent workers can exhaust a minute's budget in a few seconds.
3. **AIMD reacts late and asymmetrically.** `requestLimit` grows by **one after every 20 successes** (lines 649-656) and is halved on 429. It oscillates around the limit instead of staying below it, producing repeated small storms.
4. **429 is treated as a per-request failure.** `cooldownUntil` pauses workers, but requests already past `awaitSlot()` still complete; when the cooldown expires every worker resumes at once. The new global pause is a fixed backoff with no rate reduction, so the same burst repeats.
5. **`Retry-After` is parsed incorrectly.** `backoffMs()` does:

   ```kotlin
   // TopicIndexBuilder.kt:665-673
   val retryAfter = e.response()?.headers()?.get("Retry-After")?.toLongOrNull()
   if (retryAfter != null) wait = maxOf(wait, minOf(retryAfter * 1000, 120_000L))
   else if (e.code() == 429) wait = maxOf(wait, 10_000L)
   ```

   `Retry-After` may be an **HTTP-date** (`Wed, 21 Oct 2026 07:28:00 GMT`), not just seconds. `toLongOrNull()` returns `null` for the date form, so the app falls back to 10 s even when the server asked for 60 s, and keeps retrying into the throttle.
6. **No `maxlag` parameter.** Every API query in `WikipediaApiService.kt` lacks `maxlag=5`. Wikimedia explicitly recommends `maxlag` for non-interactive/bulk work so the server can shed load with a clear `503` + `Retry-After` instead of hard-throttling.
7. **The retry policy is too patient.** `isRetriable()` accepts `408, 425, 429, 500, 502, 503, 504` and every `IOException` (`TopicIndexBuilder.kt:658-663`). With `ROOT_PATIENCE_MS = 10 min` and `SUB_PATIENCE_MS = 3 min` (lines 772-774) a client that is being throttled keeps retrying the same request for minutes, adding load exactly when the server asked it to stop.
8. **Request volume is inflated.** Enrichment is batched well, but category pages carry no `maxage`/`smaxage` cache headers (`CATEGORY_CHILDREN_QUERY`, `WikipediaApiService.kt:52-54`), so a rebuilt or resumed topic re-downloads category listings that have not changed. Search expansion adds up to 300 result slots per natural-language topic (`SEARCH_PAGES = 6`, line 769; `expandBySearch()` lines 444-475), and search-only topics use `SEARCH_ONLY_PAGES = 10` (line 770).
9. **Other traffic shares the host.** Prefetch (`schedulePrefetch`, `HomeScreenViewModel.kt:288`) and article loads use the same OkHttp client and are not paused during a build. The dispatcher allows 12 per host, so they add concurrency on top of the builder's limit.

### 1.3 What "a rate Wikipedia can sustain" means

For an unauthenticated client that sends only a User-Agent (the current `userAgentString` in `AppContainer.kt:40-41` is compliant: app name, version, project URL, contact address), Wikimedia documents a base allowance of roughly **200 requests/minute**, and recommends **≤3 concurrent requests**, serial requests, `maxlag`, gzip (OkHttp already sends `Accept-Encoding: gzip`) and honouring `Retry-After`.

A sustainable target for this app:

| Parameter | Current | Proposed |
|---|---|---|
| Concurrent requests (global) | 10 | **3** |
| Crawl / intro / extras semaphores | 6 / 6 / 4 | **3 / 3 / 2** |
| Sustained request rate | unbounded | **3 req/s (180/min)**, burst bucket of 3 |
| `maxlag` | absent | **5** on every batch query |
| 429 backoff | 10 s fixed, `Retry-After` seconds only | `Retry-After` (seconds **or** HTTP-date) + jitter; global rate halved |
| Retries | up to 10 min per request | max 5 attempts per request, max 2 min total |
| Search expansion | 300 hits (6 pages) | **100 hits (2 pages)**, only for sparse topics |

180/min leaves ~10 % headroom for article loads, prefetch and page-view traffic, and is fast enough: a 5,000-article topic needs roughly 1,000 to 3,000 category/enrichment requests (depending on how many categories are crawled), i.e. **~6 to 17 minutes** at 3 req/s. That is *faster in wall-clock time* than the current behaviour, because the current build spends most of its time waiting in 429 cooldowns.

### 1.4 Fix 1 — a real global rate limiter

Add a token bucket that is shared by the whole build and, ideally, by the repository. Put it in `data/WikiRateLimiter.kt`:

```kotlin
package org.nsh07.wikireader.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Global token bucket for all MediaWiki API traffic.
 * 3 permits/second with a burst of 3 stays below the ~200 req/min UA-only allowance and
 * matches Wikimedia's "3 or fewer concurrent requests" guidance.
 */
internal class WikiRateLimiter(
    @Volatile private var permitsPerSecond: Double = 3.0,
    private val burst: Int = 3
) {
    private val mutex = Mutex()
    private var tokens = burst.toDouble()
    private var lastNanos = System.nanoTime()

    /** Suspends until one request may start. */
    suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                val now = System.nanoTime()
                tokens = minOf(
                    burst.toDouble(),
                    tokens + (now - lastNanos) / 1_000_000_000.0 * permitsPerSecond
                )
                lastNanos = now
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    0L
                } else {
                    (((1.0 - tokens) / permitsPerSecond) * 1000.0).toLong().coerceAtLeast(1L)
                }
            }
            if (waitMs == 0L) return
            delay(waitMs)
        }
    }

    /** A 429/503: drain the bucket and slow down until successes are observed again. */
    suspend fun onThrottled() = mutex.withLock {
        tokens = 0.0
        lastNanos = System.nanoTime()
        permitsPerSecond = (permitsPerSecond / 2).coerceAtLeast(1.0)
    }

    /** Additive recovery: +0.1 req/s every 50 successes, never above 3 req/s. */
    suspend fun onSuccess(streak: Int) {
        if (streak % 50 != 0) return
        mutex.withLock { permitsPerSecond = (permitsPerSecond + 0.1).coerceAtMost(3.0) }
    }
}
```

Wire it into `resilient()` **before** the concurrency slot, and keep the existing global cooldown:

```kotlin
// TopicIndexBuilder.kt
private val limiter = WikiRateLimiter(permitsPerSecond = 3.0, burst = 3)

private suspend fun <T : Any> resilient(...): T {
    ...
    val pause = cooldownUntil.get() - System.currentTimeMillis()
    if (pause > 0) delay(pause)
    limiter.acquire()                 // NEW: requests/second
    awaitSlot()                       // existing: requests in flight
    ...
    onRequestSucceeded()
    limiter.onSuccess(successStreak.get())
    ...
    if (e is HttpException && (e.code() == 429 || e.code() == 503)) {
        onRateLimited()
        limiter.onThrottled()         // NEW: reduce the rate as well as the concurrency
        ...
    }
}
```

and lower the constants:

```kotlin
private const val MAX_IN_FLIGHT = 3
private const val MIN_IN_FLIGHT = 1
private const val CONCURRENCY = 3
private const val EXTRA_CONCURRENCY = 2
private const val INTRO_CONCURRENCY = 3
```

Also pause prefetch while a build runs:

```kotlin
// HomeScreenViewModel.schedulePrefetch()
if (indexState.value.running) return
```

The limiter should also be used by `WikipediaRepository` for article loads, or at least the builder's limiter should be a singleton shared with `AppContainer`. Otherwise a user browsing while a 3 req/s build runs can still exceed the budget.

### 1.5 Fix 2 — parse `Retry-After` correctly

```kotlin
private fun retryAfterMs(e: HttpException): Long? {
    val raw = e.response()?.headers()?.get("Retry-After")?.trim() ?: return null
    raw.toLongOrNull()?.let { seconds ->
        return (seconds * 1000L).coerceIn(1_000L, 120_000L)
    }
    return runCatching {
        val at = java.time.ZonedDateTime.parse(
            raw, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
        ).toInstant().toEpochMilli()
        (at - System.currentTimeMillis()).coerceIn(1_000L, 120_000L)
    }.getOrNull()
}

private fun backoffMs(attempt: Int, e: Exception): Long {
    var wait = minOf(1_500L shl minOf(attempt - 1, 5), 20_000L)
    if (e is HttpException) {
        val retryAfter = retryAfterMs(e)
        if (retryAfter != null) wait = maxOf(wait, retryAfter)
        else if (e.code() == 429) wait = maxOf(wait, 5_000L)   // 5 s, not 10 s
    }
    return wait + (0..750).random()                            // jitter
}
```

Use **one** clock unit throughout. The old code multiplied seconds by 1,000 but a `Retry-After` in date form produced `null`; this version is explicit.

### 1.6 Fix 3 — send `maxlag`

Simplest and least invasive: append `maxlag=5` to the batch query constants in `WikipediaApiService.kt` (lines 19, 22, 26, 30, 34, 38, 42, 46, 50, 54, 58, 61, 69, 72). For example:

```kotlin
private const val QUERY_CATEGORY_CHILDREN =
    "w/api.php?format=json&action=query&list=categorymembers" +
    "&cmtype=page|subcat&cmprop=ids|title|type&cmlimit=500" +
    "&formatversion=2&maxlag=5&maxage=3600&smaxage=3600"
```

A `503` with `maxlag` in the error body is retriable; the existing `resilient()` already handles it, and with the new `Retry-After` parser it will wait the right amount.

If you prefer not to touch every constant, add an interceptor for the *builder's* client only:

```kotlin
private val maxlagInterceptor = Interceptor { chain ->
    val request = chain.request()
    if (request.url.encodedPath.endsWith("/w/api.php")) {
        val url = request.url.newBuilder().addQueryParameter("maxlag", "5").build()
        chain.proceed(request.newBuilder().url(url).build())
    } else chain.proceed(request)
}
```

Do **not** add `maxlag` to the interactive article-content endpoint if you want the article screen to stay responsive during replication lag; the batch builder is the important one.

### 1.7 Fix 4 — cut request volume

| Change | Location | Saving |
|---|---|---|
| Add `maxage=3600&smaxage=3600` to `CATEGORY_CHILDREN_QUERY` so resumed/rebuilt topics reuse the 50 MB OkHttp cache | `WikipediaApiService.kt:52-54` | avoids re-downloading unchanged category listings |
| Delete or stop using the separate 100-member / subcat-only constants if any caller still requests them twice | `WikipediaApiService.kt:18-22` | prevents accidental double requests |
| Lower `SEARCH_PAGES` from 6 to 2 (100 hits) and only run expansion when the category crawl found < 250 articles | `TopicIndexBuilder.kt:769`, 172-178 | up to 200 fewer result slots and 4 requests per hint |
| Lower `SEARCH_ONLY_PAGES` from 10 to 5 | line 770 | 5 requests per search topic |
| Skip intro/description fetching for titles that already have both (already done via `titlesMissingIntro`) and batch 20 per request (already `exlimit=max`) | `enrichExtras()` 481-560 | no change, keep |
| Stop retrying 500/502/504 more than 3 times | `isRetriable()` 658-663 | avoids hammering a struggling server |
| Abort the build with a clear message after 10 consecutive 429/503s instead of waiting `ROOT_PATIENCE_MS` | `resilient()` | avoids a 10-minute stuck build |
| Mark search-expansion hits as `depth = 2` (not `0` for the top 100) | `expandBySearch()` line 467 | also improves relevance (§2) |
| Do not run prefetch while `indexState.running` | `HomeScreenViewModel.schedulePrefetch()` | removes competing traffic |

### 1.8 Acceptance criteria for §1

- A build of a 5,000-article topic completes with **zero 429 responses in the log** (or at most one, followed by a correct `Retry-After` pause).
- Requests in the debug log are spaced at ≥ ~330 ms on average; never more than 3 in flight.
- A simulated `429` with `Retry-After: 30` and with `Retry-After: <HTTP-date>` both pause for ~30 s (unit test around `retryAfterMs`).
- Build wall-clock time is **no worse** than today and the progress bar keeps advancing.
- Cancelling a build stops all workers within ~1 s.

---

## 2. Why ~10 % of random articles are unrelated

There are **two different failure modes**, and they need separate fixes:

- **Mode A — "completely unrelated"** (Africa, a random European biography). The app left the library entirely and asked Wikipedia for a *global* random article.
- **Mode B — "from the library but off-topic"** (e.g. Ancient Egypt while the topic is Ancient South America). The library contains loosely-matched articles because of search expansion and a very weak relevance threshold.

### 2.1 The exact path a random article takes

```kotlin
// HomeScreenViewModel.kt:1111-1121
val topicArticle = try { getRandomArticleForTopic() } catch (...) { null }
val page = topicArticle
    ?: (if (hasSelectedTopics()) null
        else getRandomFallback(preferencesState.value.onlyArticlesWithImages))
    ?: throw NetworkException()
```

`getRandomArticleForTopic()` → `pickFromIndex()` → `pickFromIndexWithTitle()`:

```kotlin
// HomeScreenViewModel.kt:551-566
val topics = selectedTopicList()
if (topics.isEmpty()) return null
...
val built = topics.filter { index.isBuilt(it) }
if (built.isEmpty()) return null
...
val minRel = when (prefs.topicStrictness) { 0 -> 0; 1 -> 15; 2 -> 35; else -> 60 }
val pool = index.pick(built, ..., sampleSize, minRel)
```

### 2.2 Mode A — the global-random fallback

`getRandomFallback()` calls `generator=random` and returns **any** Wikipedia article:

```kotlin
// HomeScreenViewModel.kt:2015-2020
private suspend fun getRandomFallback(imagesOnly: Boolean): WikiApiPage? = ...
    wikipediaRepository.getRandomResult().query?.pages ?: emptyList()
```

It is only reached when `hasSelectedTopics()` is **false**, i.e. when the `random_topics` preference reads as empty. The user can reach that state without intending to:

1. **Save is fire-and-forget.** `onSaveTopics` calls `viewModel.saveRandomTopics(topics)` and immediately `startIndexBuild(...)` (`AppScreen.kt:454-457`). `saveRandomTopics` launches an IO coroutine (`HomeScreenViewModel.kt:1898-1903`) and returns before the value is written. If the user saves topics, goes back and presses shuffle within a few hundred milliseconds, `getRandomTopics()` can still see the old (empty) value → global random.
2. **"Clear All" + Save** (`RandomTopicsScreen.kt:236-241`) deliberately writes an empty set. After that every shuffle is global-random. There is no "no topics selected" guard; the fallback silently hides the mistake.
3. **A failed preference write** (disk full, DataStore exception) leaves the previous/empty value. The `errorHandler` logs it; the UI does not surface it.

This is the only code path that can produce a *completely* unrelated article while the user believes a topic is selected, and it matches the symptom exactly ("sometimes … completely unrelated … Africa or some European person").

**Fix A1 — make saving synchronous/awaited.**

```kotlin
// AppScreen.kt
onSaveTopics = { topics, force ->
    viewModel.saveRandomTopicsNow(topics)     // suspend, awaits DataStore
    viewModel.startIndexBuild(topics, force)
}

// HomeScreenViewModel.kt
suspend fun saveRandomTopicsNow(topics: Set<String>) = withContext(Dispatchers.IO) {
    selectedTopicsCache = topics.toSet()      // in-memory source of truth, updated first
    appPreferencesRepository.saveStringPreference("random_topics", topics.joinToString(","))
    invalidatePrefetch()
}

fun saveRandomTopics(topics: Set<String>) {   // keep old signature for other callers
    viewModelScope.launch(Dispatchers.IO + errorHandler) { saveRandomTopicsNow(topics) }
}
```

and have `getRandomTopics()` return `selectedTopicsCache ?: readPreference()`.

**Fix A2 — never fall back to global random when the user has selected topics *or* has a library.**

Track "the user has ever chosen topics for this language" in a separate boolean (`topics_configured_<lang>`). In `loadPage`:

```kotlin
val page = topicArticle
    ?: if (!hasSelectedTopics() && !hasConfiguredTopics()) {
        getRandomFallback(...)
    } else null
    ?: throw NoTopicArticleException()   // show "pick topics / build library", not a random article
```

This turns the silent, confusing behaviour into the existing explanatory error page. If the product decision is "fully random is a valid mode", gate it behind an explicit toggle or an empty-state button ("Surprise me anywhere"), not as a fallback.

**Fix A3 — make Save atomic with the build.** Disable the shuffle button while a save is in flight, or block on the preference write before navigating back. Do not rely on the async write.

### 2.3 Mode B — the library itself contains off-topic articles

Three mechanisms combine.

#### B1. Search expansion injects up to 300 loosely-matched pages

```kotlin
// TopicIndexBuilder.kt:172-178
if (hint != null && !resumable) {
    val extra = expandBySearch(hint.phrase, titles.keys)
    if (extra.isNotEmpty()) titles = titles + extra
}
```

```kotlin
// TopicIndexBuilder.kt:444-471
for (page in 0 until maxPages) {                      // SEARCH_PAGES = 6
    val hits = repo.searchArticlesPaged(phrase, page * 50).query?.search...
    for (t in applyNoiseFilter(hits)) {
        if (t !in existing && t !in out) out[t] = if (rank < 100) 0 else 1
        rank++
    }
}
```

MediaWiki search is not an AND query. For `ancient South American civilizations`, the result set legitimately contains pages that match only **"ancient"** (Ancient Egypt, Ancient Rome, Ancient Greece) or only **"civilization"**. Those pages are then:

- added to the topic with `depth = 0` for the first 100 hits — the same trust level as the topic's own category, which boosts them in `weightedPick` (`w *= 1/(1 + 0.6*depth)`), and
- scored by a model whose output is *relative* (below), so a weak match can still land above `minRel`.

With 300 extra titles against, say, 1,500 crawled titles, even a 20–30 % junk rate in the expansion is enough to produce a visible fraction of off-topic picks.

#### B2. Relevance is relative, and the threshold is tiny

```kotlin
// RelevanceModel.kt:209-215
val topIdx = finalScore.indices.sortedByDescending { finalScore[it] }.take(k)
val mean = topIdx.map { finalScore[it] }.average().takeIf { it > 0.0 } ?: 1.0
val rel = HashMap<String, Int>(docs.size)
for ((i, d) in docs.withIndex()) {
    rel[d.key] = if (corpus.isEmpty(i)) -1
    else (100.0 * finalScore[i] / mean).toInt().coerceIn(0, 300)
}
```

`rel = 100` means "as similar as the average of the best 4 %", **not** "100 % similar". Therefore:

- `minRel = 15` at the default "balanced" setting means "at least 15 % of the *average best match*", which is a very weak bar.
- The score is capped at 300 and the denominator is the mean of the top-k, so a doc that shares one frequent word with the topic can easily score 20–40.
- Nothing stores an **absolute** measure of query coverage. The only absolute constant, `DROP_SIMILARITY = 0.30`, is used exclusively for explicit 👎 feedback in `feedbackMultipliers` (line 244), not when building the topic.

#### B3. Broad roots are trusted at depth 0

```kotlin
// TopicIndexBuilder.kt:256-263
// Direct children of the topic are trusted; deeper ones must share a word…
val relevant = atDepth == 0 || related(st, parentTokens) || related(st, anchor)
```

If the resolved root is generic (e.g. `Category:Civilizations`, `Category:Ancient_history`, or a large `Category:South_America`), every direct child is followed without any check, and `MAX_DEPTH = 5` / `MAX_ARTICLES = 20_000` allow a very wide crawl. The relevance pass is then the only defence, and B2 shows it is weak.

#### B4. `MAX(rel)` across topics hides bad membership

```sql
-- TopicIndex.kt:521-522
(SELECT MIN(m.depth) FROM membership m WHERE m.title = s.title AND m.topic IN (...)),
(SELECT MAX(m.rel)   FROM membership m WHERE m.title = s.title AND m.topic IN (...))
```

An article can be a weak member of topic 1 and a strong member of topic 2; `MAX(rel)` reports the strong value. That is correct for "the user selected both", but it makes per-topic quality invisible and, combined with `rel < 0` (B5), can let a barely-related row through.

#### B5. `rel < 0` always passes

```sql
-- TopicIndex.kt:514
AND (rel < 0 OR rel >= $minRel)
```

`rel = -1` is intended for "nothing to judge by". In practice `Corpus` always includes the **title** (weight 3.0, `RelevanceModel.kt:95`), so `-1` is rare (titles made only of stopwords/2-letter words). It is not the main leak, but it is still wrong: an unscored row should not automatically be considered on-topic. Change the default to `0` for "no text" and keep `-1` only for "topic was never scored" (an incomplete build), then filter `rel >= minRel` for complete topics.

#### Why the observed rate is about 10 %

Putting rough numbers on the mechanism above (these are illustrative, not measured on your library):

- a natural-language topic crawls ~1,500 category members and adds ~300 search hits → 1,800 members;
- if ~30 % of the search hits are single-word matches, that is ~90 junk articles (5 % of the library);
- search hits are stored at `depth = 0` for the first 100, so `weightedPick` gives them `1 / (1 + 0.6·0) = 1.0`, while genuine deep members average ~0.6–0.8; the junk's effective share of the weight mass becomes ~6–8 %;
- the default `minRel = 15` lets most of them through.

That lands exactly in the "about one in ten" range. Fix B1 (cap + content-word filter), B2 (`cov`) and B3 (no free pass) attack different terms of that product, so each one reduces the rate; together they should take it to well under 1 %.

### 2.4 The fixes for Mode B

#### Fix B1 — cap and distrust search expansion

```kotlin
// TopicIndexBuilder.kt
private const val SEARCH_PAGES = 2                 // 100 hits, not 300
private const val SEARCH_ONLY_PAGES = 5

// only expand when the crawl is sparse
if (hint != null && !resumable && titles.size < 250) {
    val extra = expandBySearch(hint.phrase, titles.keys)
    ...
}
```

and in `expandBySearch`, give **every** search hit depth 2, and reject hits that share no *content* word with the phrase:

```kotlin
private val genericTopicWords = setOf(
    "ancient", "history", "historical", "people", "culture", "cultural", "civilization",
    "civilisation", "world", "country", "countries", "region", "regions", "society",
    "list", "lists", "timeline", "overview"
)

private fun sharesContentWord(title: String, phrase: String): Boolean {
    val p = RelevanceModel.terms(phrase) - genericTopicWords
    if (p.isEmpty()) return true
    val t = RelevanceModel.terms(title)
    return p.any { w -> t.any { RelevanceModel.sameWord(w, it) } }
}
```

In the loop:

```kotlin
for (t in applyNoiseFilter(hits)) {
    if (!sharesContentWord(t, phrase)) continue
    if (t !in existing && t !in out) out[t] = 2       // always depth 2
    rank++
}
```

This keeps the recall benefit for niche intersections while removing the "Ancient Egypt" class of hits.

#### Fix B2 — add an absolute coverage score and filter on it

Add a `cov` column (0–100, “percentage of the query’s IDF mass that this document actually contains”) to `membership`. This is interpretable, stable across topics, and independent of the relative `rel`.

In `RelevanceModel`:

```kotlin
data class Result(
    val rel: Map<String, Int>,
    val cov: Map<String, Int>,      // NEW: 0..100
    val excluded: Set<String>,
    val seedKeys: List<String>
)

// after computing q0 and finalScore:
val queryMass = q0.sum()                       // idf mass of the query
val cov = HashMap<String, Int>(docs.size)
for ((i, d) in docs.withIndex()) {
    var hit = 0.0
    for (j in corpus.idsOf(i)) {               // expose ids[i]
        if (q0[j] > 0.0) hit += q0[j]
    }
    cov[d.key] = if (queryMass <= 0.0) 0
        else (100.0 * hit / queryMass).toInt().coerceIn(0, 100)
}
return Result(rel, cov, excluded, topIdx.map { docs[it].key })
```

Store it in `applyRelevance()` (one extra bind in the `UPDATE`), and filter in `pick()`:

```sql
AND a.title IN (
    SELECT title FROM membership
    WHERE topic IN ($ph) AND rel >= $minRel AND cov >= $minCov
)
```

Suggested mapping, exposed through the existing "Topic strictness" setting:

| Strictness | `minRel` (relative) | `minCov` (absolute) | Effect |
|---|---|---|---|
| 0 broad | 0 | 0 | current behaviour |
| 1 balanced (default) | 25 | 20 | drops single-word matches |
| 2 strict | 45 | 35 | keeps multi-word topical matches |
| 3 very strict | 65 | 50 | near-exact topical matches |

`cov` is the part that specifically kills “Ancient Egypt” for a South-America query: it covers only the generic "ancient" token, whose IDF inside the corpus is low, so `cov` is typically 5–15.

#### Fix B3 — make `rel = -1` mean "unknown", not "on-topic"

In `RelevanceModel.kt:213`, change `-1` to `0` for empty docs, and in `pick()` stop treating `rel < 0` as a pass for topics whose build is complete:

```kotlin
// scoreTopic() after applyRelevance:
index.clearUnscoredToZero(topic)   // UPDATE membership SET rel = 0 WHERE topic = ? AND rel < 0
```

Alternatively add `AND t.complete = 1` to the `built` check in `pickFromIndexWithTitle` and keep `-1` only for in-progress builds.

#### Fix B4 — gate depth-0 children against generic roots

Keep trust for direct children of **specific** categories, but if the root name is generic, require a shared content word:

```kotlin
private val genericCategoryWords = setOf("civilizations", "history", "society", "culture", "people")

val rootIsGeneric = tokens(root).any { it in genericCategoryWords }
val relevant = atDepth == 0 && !rootIsGeneric ||
    related(st, parentTokens) ||
    related(st, anchor)
```

This is a heuristic; the `cov` filter (B2) is the safety net for cases the gate misses.

#### Fix B5 — improve the fallback message and expose quality

- `DebugLog.i` after a build: number of members with `cov < 20`, number with `rel < 25`, number of search-expansion members at depth 2, and the 20 lowest-`cov` titles. This makes regressions visible without guessing.
- Optional UI: in the Library card, show “X % of this topic passes your strictness filter”. The data is one `COUNT(*)` away.

### 2.5 Regression test for §2

Add to `app/src/test/.../RobustnessFixesTest.kt` (or a new `RelevanceModelTest.kt`):

```kotlin
@Test fun `generic word match is not enough`() {
    val docs = listOf(
        RelDoc("Moche culture", "Moche", "Andean civilization on the north coast of Peru", "The Moche ..."),
        RelDoc("Inca Empire", "Inca", "Andean civilization in South America", "The Inca ..."),
        RelDoc("Ancient Egypt", "Egypt", "Civilization of ancient Northeast Africa", "Ancient Egypt ..."),
        RelDoc("Julius Caesar", "Caesar", "Roman general and statesman", "Gaius Julius Caesar ...")
    )
    val r = RelevanceModel.score(docs, listOf("ancient South American civilizations"), emptyList())
    assertTrue(r.cov.getValue("Moche culture") >= 30)
    assertTrue(r.cov.getValue("Inca Empire") >= 30)
    assertTrue(r.cov.getValue("Ancient Egypt") < 20)
    assertTrue(r.cov.getValue("Julius Caesar") < 20)
}
```

And an end-to-end test on a small fixture database: build a topic with one junk search hit, assert `pick(..., minRel = 25, minCov = 20)` never returns it.

### 2.6 Acceptance criteria for §2

- With topics selected, `getRandomFallback()` is never reached (assert in debug builds; log a warning if it is).
- Saving topics and immediately pressing shuffle always yields a library article.
- "Clear All" + Save shows a "No topics selected" state, not global random articles.
- For a South-America topic, a sample of 100 picks contains 0 articles whose `cov < 20` while strictness ≥ 1.
- `minRel`/`minCov` are visible in the debug “CHOSEN …” line so tuning is measurable.

---

## 3. Missing phylogenetic trees for plants

### 3.1 How trees are rendered today

- `parseWikitext()` splits a section into `AnnotatedString` blocks. A block starting with `{{clade` is routed to `Cladogram` (`ParsedBodyText.kt:139-143`).
- `Cladogram` calls `parseClade(text)`; if it returns `null`, the composable **silently returns** (`Cladogram.kt:60-68`).
- `parseClade` itself is correct for well-formed `{{clade|…}}` trees. I verified this against the real wikitext of *Fern*, *Flowering plant*, *Monocotyledon*, *Eudicots*, *Orchid*, *Poaceae*, *Rose*, *Maize*, *Potato*, *Tomato*, *Wheat*, *Rice*, *Solanaceae* and *Asteraceae*: whenever the clade block reaches `parseClade`, it parses.

So the failure is **not** the clade parser. It is the block extraction.

### 3.2 The table branch swallows the tree

```kotlin
// HomeScreenViewModel.kt:1781-1804
} else if (wikitext[i] == '{') {
    if (wikitext.getOrNull(i + 1) == '|') {
        val currSubstring = wikitext.substringMatchingParen('{', '}', i)
        if (!currSubstring.substring(...).contains("{|")) {
            flush()
            out.add(AnnotatedString(currSubstring))      // whole table as ONE block
            i += currSubstring.length
            ...
        } else {
            val currSubstringNestedTable = wikitext.substringMatchingParen('{', '}', wikitext.indexOf("{|", i + 2))
            flush()
            out.add(AnnotatedString(currSubstringNestedTable))
            i += currSubstring.length
            ...
        }
    }
```

A table (`{| … |}`) is emitted as a single block. Any `{{clade …}}` **inside a table cell** is still inside that string, so it never becomes a top-level `{{clade` block and `ParsedBodyText` routes it to `AsyncWikitable`:

```kotlin
// ParsedBodyText.kt:144-150
} else if (it.startsWith("{|")) {
    AsyncWikitable(text = it.toString(), ...)
}
```

`parseWikitable()` then processes the table line by line and turns each cell into an `AnnotatedString` via `toWikitextAnnotatedStringSafe()` (`tableAndInfoboxUtil.kt:129-138`). That function handles links and basic markup, **not** block templates, so the clade is rendered as stripped text or disappears.

This is exactly how many plant articles present their phylogeny. I verified the pattern in *Monocotyledon*: a `{|…|}` table whose cell contains a `{{clade …}}` block (simplified):

```wikitext
{| class="wikitable"
|-
| Cladogram I
|-
| {{clade
   |label1=Monocots
   |1={{clade
      ...
   }}
  }}
|}
```

The clade is inside a table cell, so today it is unreachable. The same pattern appears in articles that use `{{Cladogram}}` inside a table cell (`blockTemplateAt` would unwrap it correctly — but only if it is seen as a top-level block, which it is not inside a table).

A second, less important contributor: sections default to collapsed (`PreferencesState.kt:16`, `expandedSections = false`), and many plant articles put the phylogeny under a section such as “Evolution” or “Phylogeny”. Even when the clade *is* top-level, the user sees only the section header until they expand it.

### 3.3 Fix A (recommended) — extract clade blocks from table text

Add a helper to `parser/BlockTemplates.kt`:

```kotlin
/**
 * Returns every clade tree contained in [text] (including trees nested in tables or in
 * {{Cladogram}}), plus [text] with those ranges blanked out so the table stays parseable.
 */
fun extractCladeBlocks(text: String): Pair<List<String>, String> {
    val found = ArrayList<String>()
    val cleaned = StringBuilder(text)
    var i = 0
    while (i < text.length - 1) {
        if (text[i] == '{' && text[i + 1] == '{' && isBlockTemplateStart(text, i)) {
            val block = blockTemplateAt(text, i)
            if (block != null && block.blocks.any { it.startsWith("{{clade", true) }) {
                block.blocks.filter { it.startsWith("{{clade", true) }.forEach { found.add(it) }
                for (k in i until i + block.length) cleaned.setCharAt(k, ' ')
                i += block.length
                continue
            }
        }
        i++
    }
    return found to cleaned.toString()
}
```

Then in `parseWikitext`, use it in the `{|` branch instead of emitting the raw table:

```kotlin
if (wikitext.getOrNull(i + 1) == '|') {
    val table = wikitext.substringMatchingParen('{', '}', i)
    flush()
    val (clades, tableOnly) = extractCladeBlocks(table)
    clades.forEach { out.add(AnnotatedString(it)) }
    if (tableOnly.isNotBlank()) out.add(AnnotatedString(tableOnly))
    i += table.length
    if (stack > 0) stack--
}
```

Notes:

- `blockTemplateAt` already unwraps `{{Cladogram|…|cladogram={{clade…}}}}` to the inner `{{clade…}}` (`BlockTemplates.kt:244-253`), so emitting its `blocks` (not the wrapper) is important: `parseClade` requires the first argument to be `clade`.
- Blanking the range with spaces keeps line structure, so `parseWikitable` still sees the same number of lines and row separators.
- For a table whose only content is the clade (the Monocotyledon case), `tableOnly` may become a table with an empty cell; that is acceptable, or skip emitting it when it has no non-whitespace cell text.

This is a ~30-line change and fixes the class of articles that put a phylogeny in a table.

### 3.4 Fix B (optional, better layout) — render clades inside table cells

If you want the tree to stay inside its table cell, change `parseWikitable` to return a small sealed cell type instead of `AnnotatedString`:

```kotlin
sealed interface TableCell {
    data class Text(val text: AnnotatedString) : TableCell
    data class Clade(val wikitext: String) : TableCell
    data object Empty : TableCell
}
```

Then in `AsyncWikitable`’s `Table { row, column -> … }` render `Cladogram(cell.wikitext, fontSize)` for `TableCell.Clade`. This is more faithful but touches `parseWikitable`, `AsyncWikitable` and their callers. Fix A is enough for correctness; Fix B is a polish item.

### 3.5 Fix C — do not silently drop trees

`Cladogram.kt:60-68` currently hides every failure. Change to:

```kotlin
val tree = remember(text) {
    try { parseClade(text) }
    catch (e: Throwable) {
        DebugLog.w("Clade", "could not parse a clade tree (${text.take(120)})", e)
        null
    }
}
if (tree == null) {
    // In debug builds show the raw block; in release keep the layout clean but log once.
    if (BuildConfig.DEBUG) Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
    return
}
```

At minimum, log the article title at the call site so failures can be triaged. A blank gap with no log is undebuggable.

### 3.6 Fix D — auto-expand sections that contain a tree

In `ExpandableSection`, accept an initial state derived from the body:

```kotlin
val initiallyExpanded = remember(body) {
    body.any { it.startsWith("{{clade", ignoreCase = true) }
}
var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
```

This keeps the performance benefit of collapsed sections for ordinary prose while making phylogenies visible without an extra tap. Do **not** flip the global `expandedSections` default; that would compose every section of every article and hurt scroll performance.

### 3.7 Tests for §3

Add fixtures from real articles to `app/src/test/resources/wikitext/` (fetch once and commit as small `.txt` files):

- `monocotyledon_table.txt` — clade inside a `{|…|}` table,
- `flowering_plant.txt` — top-level `{{clade}}`,
- `maize.txt` — multiple clades in one section,
- `poaceae.txt` — `{{Cladogram}}` wrapper.

Assertions:

```kotlin
@Test fun `clade inside a table is extracted`() {
    val blocks = parseBodyForTest(readResource("monocotyledon_table.txt"))
    assertTrue(blocks.any { it.startsWith("{{clade", true) })
    assertNotNull(parseClade(blocks.first { it.startsWith("{{clade", true) }))
}
```

Plus a Compose UI test that the article body contains a `Cladogram` node for that fixture.

### 3.8 Acceptance criteria for §3

- *Monocotyledon*, *Poaceae*, *Orchid* and *Maize* show at least one tree.
- No article regresses: tables still render, captions/links still work, and no raw `{{clade` text is visible.
- `DebugLog` records a parse failure with the article title if a clade block exists but `parseClade` returns null.

---

## 4. Deselect topics without deleting the library

### 4.1 Current behaviour

There are two destructive paths and no non-destructive one:

1. **Library card → Remove** calls `onRemoveTopic(topic)`, which calls `deleteTopic()`:

   ```kotlin
   // HomeScreenViewModel.kt:483-488
   suspend fun removeTopicFromLibrary(topic: String) = withContext(Dispatchers.IO) {
       topicIndexProvider(preferencesState.value.lang).deleteTopic(topic)
       invalidatePrefetch()
   }
   ```

   ```kotlin
   // TopicIndex.kt:417-428
   fun deleteTopic(topic: String) {
       db.delete("membership", "topic = ?", arrayOf(topic))
       db.delete("topics", "topic = ?", arrayOf(topic))
       db.execSQL("DELETE FROM articles WHERE title NOT IN (SELECT title FROM membership)")
   }
   ```

   That removes the membership **and** every article row that no other topic uses. Re-selecting the topic means a full re-crawl and re-download.

2. **The preference stores only the selected set.** `saveRandomTopics()` writes `topics.joinToString(",")` (`HomeScreenViewModel.kt:1898-1903`). After a “deselect”, the topic simply disappears from the string, and `selectedTopicList()` (`347-349`) returns only what is in the string. There is no record that the topic was ever built.

The UI reinforces this: saving `selected.filter { it.value }` (`RandomTopicsScreen.kt:241,455`) sends only enabled keys, and the Library card’s Remove button has no confirmation (`IndexUi.kt:244-246`).

### 4.2 Schema change (DB v7)

Add an `enabled` flag to `topics` and keep membership untouched when the user toggles:

```kotlin
// TopicIndex.kt — bump version to 7
override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    ...
    if (oldVersion < 7) {
        db.execSQL("ALTER TABLE topics ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
        // v7 migration: existing libraries start disabled; the ViewModel immediately
        // reconciles them with the old random_topics preference once (see 4.5).
        // New topics built after v7 are inserted with the default (1) because the user
        // explicitly selected them to build.
        db.execSQL("UPDATE topics SET enabled = 0")
    }
}
```

New API:

```kotlin
fun setTopicEnabled(topic: String, enabled: Boolean) {
    writableDatabase.execSQL(
        "UPDATE topics SET enabled = ? WHERE topic = ?",
        arrayOf<Any?>(if (enabled) 1 else 0, topic)
    )
}

fun enabledTopics(): List<String> =
    readableDatabase.rawQuery("SELECT topic FROM topics WHERE enabled = 1 ORDER BY topic", null)
        .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

/**
 * Sets the enabled flag for every built topic in one transaction.
 * Used both by [saveRandomTopicsNow] and by the one-time v7 migration.
 */
fun setEnabledFrom(selected: Set<String>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.execSQL("UPDATE topics SET enabled = 0")
        val upd = db.compileStatement("UPDATE topics SET enabled = 1 WHERE topic = ?")
        for (t in selected) {
            upd.clearBindings(); upd.bindString(1, t); upd.executeUpdateDelete()
        }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
```

Add `enabled` to `TopicStat` and to `topicStats()`’s SELECT, so the UI can render the toggle state.

**Rebuild caveat:** `replaceTopic()` uses `INSERT OR REPLACE` without naming `enabled`, so the column default would apply. Preserve the old value explicitly:

```sql
INSERT OR REPLACE INTO topics(topic, built_at, resolved, article_count, complete, enabled)
VALUES(?, ?, ?, ?, ?, COALESCE((SELECT enabled FROM topics WHERE topic = ?), 1))
```

### 4.3 Selection semantics

```kotlin
// HomeScreenViewModel.kt
private suspend fun selectedTopicList(): List<String> = withContext(Dispatchers.IO) {
    val index = topicIndexProvider(preferencesState.value.lang)
    val enabled = index.enabledTopics()
    val pending = getRandomTopics().filterNot { index.isBuilt(it) }  // not built yet
    (enabled + pending).distinct()
}
```

- Built topics: the DB `enabled` flag is the source of truth.
- Pending/custom topics: the preference remains the source of truth until the build inserts a `topics` row.
- `saveRandomTopics(topics)` becomes:

  ```kotlin
  suspend fun saveRandomTopicsNow(topics: Set<String>) = withContext(Dispatchers.IO) {
      val index = topicIndexProvider(preferencesState.value.lang)
      index.setEnabledFrom(topics)          // UPDATE topics SET enabled = (topic IN …)
      appPreferencesRepository.saveStringPreference("random_topics", topics.joinToString(","))
      invalidatePrefetch()
  }
  ```

  `setEnabledFrom` loops over `topics` (few rows), so no dynamic `IN` limit issues.

**Important:** do not call `deleteTopic()` from Save. Deselecting is now just `enabled = 0`; membership, intros, views and article rows stay cached.

### 4.4 UI changes

**Random topics screen** (`RandomTopicsScreen.kt`):

- Initialise `selected` from **both** `onGetTopics()` and the library’s enabled topics:

  ```kotlin
  LaunchedEffect(Unit) {
      val saved = onGetTopics()
      saved.forEach { selected[it] = true }
      libraryStats?.topics?.filter { it.enabled }?.forEach { selected[it.topic] = true }
  }
  ```

  `libraryStats` is already loaded at line 121-123; use it (or add an `onGetTopicStates` callback) instead of a second source of truth.

- Replace the plain `remember { mutableStateMapOf… }` with `rememberSaveable`-backed state, or re-derive it from the DB after process death. At minimum, survive configuration changes.

- “Clear All” should mean “deselect all”, not “delete the library”. Its label can stay, but the confirmation text must say that libraries are kept.

**Library card** (`IndexUi.kt`):

- Add a checkbox/switch per topic for **enabled** (fast, non-destructive).
- Keep the existing “Remove” as an explicitly destructive action with a confirmation dialog:

  ```kotlin
  TextButton(onClick = { showConfirm = t.topic }) { Text(stringResource(R.string.libraryRemove)) }
  // AlertDialog: "Remove the downloaded articles for “X”? You will have to build it again."
  ```

- Show “Kept, not used for random” for disabled topics instead of “not built”.
- Replace `selectedTopics.filter { it !in built }` with “enabled topics that are not built yet”.

### 4.5 Migration from v6

On the first launch after upgrading:

1. `onUpgrade` adds `enabled` with default `0`.
2. `HomeScreenViewModel` reads `random_topics` once and calls `index.setEnabledFrom(saved)`.
3. Set a preference `topics_v7_migrated = true`.

This preserves the old behaviour exactly: only the topics that were in `random_topics` are enabled; any built topic that was not selected stays disabled (but is now recoverable). Topics the user had deleted under v6 cannot be restored — their rows are gone — but from v7 onward deselection is reversible.

### 4.6 Interaction with builds and deletion

- `deleteTopic()` is still needed for “Remove permanently”. Keep it, but:
  - cancel/join a running build for that topic first, otherwise the builder can re-insert it after the deletion (`runIndexBuild` and `deleteTopic` currently have no shared lock);
  - use `NOT EXISTS` instead of `NOT IN` for the article cleanup and run it inside the same transaction;
  - add a confirmation dialog.
- `saveRandomTopicsNow` must not enable a topic whose build failed. Enable it, but `selectedTopicList()` will include it; `pickFromIndexWithTitle` filters by `isBuilt`, and `unseenCount` returns 0 → the existing “build your library” error is shown. That is acceptable, and better than silently dropping the user’s selection.

### 4.7 Acceptance criteria for §4

- Deselect a built topic → the Library card still lists it, with its article count and build date, marked “not used”.
- Re-select it → random articles from it return immediately, with **no network requests** (verify in the debug log).
- “Remove” → confirmation → article rows for that topic are deleted; other topics are unaffected.
- Existing users keep exactly their previous selection after the upgrade.
- `random_topics` is no longer the only record of a built topic.

---

## 5. Optional background behaviour learning (off by default)

### 5.1 Principles

- **Off by default**, opt-in in Settings; when off, no reading rows are written and the picker is byte-for-byte the same as today.
- **Local only.** No upload, no analytics, no Wikipedia API calls. The database already lives on-device; this adds two small tables.
- **Explainable and bounded.** The score is a multiplier with a hard cap, so the user still sees variety and quality.
- **Resettable.** One button deletes all learned data.
- **Complements, does not replace, explicit votes.** 👍/👎 remain the strongest signal.

### 5.2 Preference and settings

`PreferencesState.kt`:

```kotlin
/** Learns from reading time/scroll depth to show more of what the user actually reads. Local only. */
val personalizationEnabled: Boolean = false,
```

`SettingsViewModel.kt` (mirror the existing `prefer-quality` pattern at lines 97-98 and 289-292):

```kotlin
val personalizationEnabled =
    appPreferencesRepository.readBooleanPreference("personalization")
        ?: appPreferencesRepository.saveBooleanPreference("personalization", false)

is SettingsAction.SavePersonalization -> viewModelScope.launch(safeCoroutineHandler) {
    appPreferencesRepository.saveBooleanPreference("personalization", action.value)
    preferencesStateMutableFlow.update { it.copy(personalizationEnabled = action.value) }
    if (!action.value) topicIndexProvider(preferencesState.value.lang)
        .clearReadingData(preferencesState.value.lang)
}
```

`SettingsAction.kt`: add `data class SavePersonalization(val value: Boolean) : SettingsAction()`.

`SettingsScreen.kt`: add a switch next to “Prefer good and featured articles” (~line 749) and “Only articles with images” (~line 780), with a supporting text such as:

> “Uses how long you read and how far you scroll to show more articles like the ones you enjoy. Everything stays on this device. Off by default.”

Add a “Delete learned data” text button (only visible when enabled) that calls a new `SettingsAction.ClearPersonalization`.

### 5.3 Schema (DB v7, same migration as §4)

```sql
CREATE TABLE reading(
    lang       TEXT NOT NULL,
    title      TEXT NOT NULL,
    dwell_ms   INTEGER NOT NULL DEFAULT 0,
    scroll     REAL    NOT NULL DEFAULT 0,   -- 0..1, furthest fraction reached
    opens      INTEGER NOT NULL DEFAULT 0,
    affinity   REAL    NOT NULL DEFAULT 0,   -- -0.5..1.0
    updated_at INTEGER NOT NULL,
    PRIMARY KEY(lang, title)
);

CREATE TABLE topic_affinity(
    lang     TEXT NOT NULL,
    topic    TEXT NOT NULL,
    affinity REAL NOT NULL DEFAULT 0,        -- 0..1
    updated_at INTEGER NOT NULL,
    PRIMARY KEY(lang, topic)
);
```

`TopicIndex` API:

```kotlin
fun recordReading(lang: String, title: String, dwellMs: Long, scroll: Float, topics: List<String>)
fun readingAffinities(lang: String, titles: Collection<String>): Map<String, Double>
fun topicAffinities(lang: String): Map<String, Double>
fun clearReadingData(lang: String)
```

`deleteTopic()` should also delete the matching `topic_affinity` row; `clearReadingData()` empties both tables for that language.

### 5.4 Signals and scoring

Use signals the app already has or can observe cheaply:

| Signal | Source | Weight |
|---|---|---|
| Dwell time | `loadPage` start → next `loadPage` / `onCleared` / `ON_STOP` | 0.75 |
| Scroll depth | `HomeScreen` `LazyListState` snapshot (max fraction) | 0.25 |
| Section expansions | `ExpandableSection` callback | +0.05 each, max 3 |
| Explicit 👍/👎 | `rateArticle()` | ±1.0 (strongest) |
| Bounce (< 6 s) | dwell | −0.4 |

```kotlin
private fun signal(dwellMs: Long, scroll: Float, sections: Int): Double {
    val dwell = (dwellMs / 45_000.0).coerceIn(0.0, 1.0)       // 45 s = "read it"
    val bounce = if (dwellMs < 6_000L) -0.4 else 0.0
    val expands = 0.05 * minOf(sections, 3)
    return (0.75 * dwell + 0.25 * scroll.coerceIn(0f, 1f) + expands + bounce)
        .coerceIn(-0.5, 1.0)
}
```

EMA update (so one accidentally long article does not dominate):

```kotlin
val old = currentAffinity
val next = (0.85 * old + 0.15 * s).coerceIn(-0.5, 1.0)
```

Time decay when reading the map (`2 %/day`):

```kotlin
val days = (now - updatedAt) / 86_400_000.0
val decayed = affinity * exp(-0.02 * days)
```

Topic affinity: for each topic that contains the article (query `membership` by title, limited to enabled topics), apply the same EMA with the signal; update at most 5 topics to bound the work.

### 5.5 Applying the scores in `weightedPick`

Extend the existing function, which already has the right shape:

```kotlin
private fun weightedPick(
    items: List<ArticleRow>,
    boostQuality: Boolean,
    bias: Int,
    sortedViews: IntArray?,
    feedback: Map<String, Double>,
    reading: Map<String, Double> = emptyMap(),          // NEW
    topicAffinity: Double = 0.0                          // NEW
): ArticleRow {
    fun weight(a: ArticleRow): Double {
        var w = ln(maxOf(a.bytes, 1_000).toDouble())
        ...
        w *= feedback[a.title] ?: 1.0
        if (reading.isNotEmpty()) {
            val r = reading[a.title] ?: 0.0
            w *= (1.0 + 2.0 * r).coerceIn(0.5, 3.0)          // hard cap: 3x
        }
        if (topicAffinity > 0.0) w *= 1.0 + 1.0 * topicAffinity.coerceIn(0.0, 1.0)
        return w
    }
    ...
}
```

Load the maps once per pick (not per candidate):

```kotlin
val reading = if (prefs.personalizationEnabled)
    index.readingAffinities(prefs.lang, candidates.map { it.title }) else emptyMap()
val topicAff = if (prefs.personalizationEnabled)
    index.topicAffinities(prefs.lang) else emptyMap()
```

For multiple selected topics, use the average or max of their affinities (max is more responsive; average is more conservative). Pass a single `Double` to keep the function signature simple.

### 5.6 Hook points

`HomeScreenViewModel`:

1. **Finish the previous reading session** at the top of `loadPage()` and in `onCleared()`:

   ```kotlin
   private var reading: ReadingSession? = null

   private fun finishReading() {
       val r = reading ?: return
       reading = null
       if (!preferencesState.value.personalizationEnabled) return
       viewModelScope.launch(Dispatchers.IO + errorHandler) {
           topicIndexProvider(r.lang).recordReading(r.lang, r.title, r.dwellMs(), r.scroll, r.topics)
       }
   }
   ```

2. **Start a session** after the article is actually shown (after `pageInfo` and `parsedExtract` are committed, around line 1238-1243):

   ```kotlin
   finishReading()
   if (preferencesState.value.personalizationEnabled && pageInfo?.title != null) {
       reading = ReadingSession(pageInfo.title, setLang, System.currentTimeMillis(), topicsForTitle(setLang, pageInfo.title))
   }
   ```

   `topicsForTitle` queries `membership` for enabled topics containing that title (one indexed query, cached per pick if you already know the chosen topic).

3. **Scroll progress** from the UI:

   ```kotlin
   // HomeScreen.kt, inside the article list
   LaunchedEffect(listState, currentTitle) {
       snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
           .debounce(500)
           .collect { last -> viewModel.onScrollProgress(last, listState.layoutInfo.totalItemsCount) }
   }
   ```

   Clamp to 0..1. Ignore if personalization is off.

4. **Votes** in `rateArticle()`:

   ```kotlin
   if (prefs.personalizationEnabled) {
       topicIndexProvider(action.lang).recordVote(action.title, action.vote)
   }
   ```

5. **Lifecycle**: in `HomeScreen`, call `viewModel.finishReading()` on `ON_STOP` (app backgrounded) and in `DisposableEffect` when the article screen leaves composition, so time in the background is not counted.

### 5.7 Safeguards

- **Cap** the multiplier at 3× and keep the base weight (`bytes`, `rel`, `cov`, quality, images) intact. Personalization should reorder, not replace, relevance.
- **Explore**: with probability 10 %, ignore the multiplier for one pick, so the user is not trapped in a filter bubble.
- **Minimum data**: require at least 5 finished sessions before applying any multiplier; before that, only collect.
- **Decay**: 2 %/day, and delete rows untouched for 180 days.
- **No learning from error pages, search results, settings, or offline cache misses.**
- **Per language**: `reading.lang` keeps English/Japanese profiles separate, matching the per-language DB.

### 5.8 Privacy and backup

- The data never leaves the device.
- If `android:allowBackup` is true, Android may upload the DB to Google backup. Either set `android:allowBackup="false"` (simplest, but users lose library backups) or add a backup rule excluding `topic_index_*.db` when personalization is enabled / always.
- The Settings screen must state “Everything stays on this device” next to the toggle, and the “Delete learned data” action must be easy to find.
- Do not include article titles in crash logs/reports when personalization is on; `DebugLog` currently logs titles, and debug logs are user-exportable.

### 5.9 Tests

- `signal()` bounds: 0 s → negative, 45 s + full scroll → 1.0.
- EMA converges and decays.
- With the flag false: `recordReading` is never called and `readingAffinities` returns empty.
- With the flag true: 10 × dwell-heavy sessions on topic A increase A’s pick share in a deterministic seeded test.
- `clearReadingData` empties both tables.

---

## 6. Other correctness bugs

### B1. Global-random fallback is silent (P0, covered in §2.2)

`HomeScreenViewModel.kt:1118-1121`. Fix: remove the fallback or require `!hasConfiguredTopics()`.

### B2. `saveRandomTopics` is asynchronous and racy (P0, covered in §2.2/§4)

`HomeScreenViewModel.kt:1898-1903`, `AppScreen.kt:454-457`. Make the save `suspend` and await it.

### B3. Rebuilding empties a working library (P2)

`replaceTopic()` deletes the old membership at the start (`TopicIndex.kt:344`), and `isBuilt()` accepts a partial topic (`TopicIndex.kt:138-141`). Pressing “Save” with “rebuild” on an existing topic makes random picks draw from a half-built, partially enriched set; if the build is cancelled, the library stays partial.

**Fix:** build into a staging table and swap atomically:

```sql
CREATE TABLE membership_new(...same columns...);
-- fill membership_new
BEGIN;
DELETE FROM membership WHERE topic = ?;
INSERT INTO membership SELECT * FROM membership_new WHERE topic = ?;
DELETE FROM membership_new WHERE topic = ?;
UPDATE topics SET complete = 1 WHERE topic = ?;
COMMIT;
```

Or, simpler: keep the old rows until the crawl finishes, then `replaceTopic()`; `replaceTopic` is already transactional, so the only problem is that it is called too early (`TopicIndexBuilder.kt:183`). Move the “incomplete” insert to a separate `staging` table if you need crash-resume, or keep the old membership and store the in-progress list in a new table.

### B4. Articles are marked seen before they load (P2)

`HomeScreenViewModel.kt:620-637`: `index.markSeen(chosen.title)` runs before `getPageData()`. If the page fetch fails (deleted page, network), the article is permanently skipped. Move the call to after `page != null && page.pageId != null`, or record a “shown” timestamp only when the content is committed.

Also note `seen` is separate from `sessionSeen` and from `random_seen_<lang>`; `resetSeenArticles()` clears only the DB table (`477-481`), while `persistedSeen()` (dead code today) would still hide titles. Delete the old preference on reset for safety.

### B5. `seen` grows without bound (P2/P3)

`seen(title TEXT PRIMARY KEY)` has no timestamp and no pruning (`TopicIndex.kt:87`). Every pick runs `a.title NOT IN (SELECT title FROM seen)` (`514`, `489`, `476`). Over months this becomes tens of thousands of rows and a large anti-join.

**Fix:**

```sql
ALTER TABLE seen ADD COLUMN seen_at INTEGER NOT NULL DEFAULT 0;
CREATE INDEX seen_at ON seen(seen_at);
```

Prune on app start or every 1,000 inserts: keep the most recent 50,000 rows (or 12 months). Use `NOT EXISTS` instead of `NOT IN`:

```sql
AND NOT EXISTS (SELECT 1 FROM seen s WHERE s.title = a.title)
```

### B6. `deleteTopic()` can race a running build (P2)

`removeTopicFromLibrary()` and `startIndexBuild()` are independent coroutines (`HomeScreenViewModel.kt:484`, `runIndexBuild`). A build can re-insert `membership` rows after the delete. Add a per-language `Mutex` around build/delete/save, or cancel the build job before deleting.

### B7. `parseWikitext` brace counting is fragile (P2)

`HomeScreenViewModel.kt:1724-1733` increments `stack` for **every** `{`, including `{|` tables and `{{` templates, then manually decrements in special cases. Unbalanced braces inside a code sample, math or table can make the parser treat the rest of the article as “inside a template”, so links/images after that point stop rendering. The nested-table branch (1793-1804) also advances by the **outer** `currSubstring.length` after emitting only the **inner** table, dropping the outer table’s other cells.

**Fix:** replace the ad-hoc counter with a proper scanner that understands `{{…}}`, `{|…|}`, `<tag>…</tag>` and `[[…]]` as balanced regions, or reuse `substringMatchingParen` consistently and advance by the length actually consumed. Add a fuzz test with unbalanced braces.

### B8. `AsyncWikitable` parses every table even when collapsed (P3)

`AsyncWikitable.kt:84-94`: `LaunchedEffect(text)` runs `parseWikitable()` immediately, although the result is only shown after `expanded` becomes true (line 124). On long articles with many tables this is wasted CPU and allocations during load.

**Fix:** `LaunchedEffect(text, expanded) { if (expanded) rows = parseWikitable(...) }`, or parse lazily inside the expanded block.

### B9. Selection state is lost on process death (P3)

`RandomTopicsScreen.kt:132`: `val selected = remember { mutableStateMapOf… }`. If Android kills the process while the build runs, the screen comes back with only `onGetTopics()` (which currently returns only the saved set) — and, before §4, the UI cannot distinguish selected from built. Use `rememberSaveable` with a `Set<String>` or derive from the DB.

### B10. Dead code inflates the ViewModel and hides the real path (P3)

`HomeScreenViewModel.kt:1932-2272` contains `getRandomArticleForTopicAttempt`, `getArticlesForTopic`, `getArticlesByPopularity`, `randomCategoryWalk`, `getArticlesByIncategorySearch`, `filterCandidates`, `persistedSeen`, `rememberSeen`, etc. They are unreachable (the active path is `pickFromIndex()` at line 1930). Removing them:

- makes it obvious that there is exactly **one** random path,
- removes the stale `random_seen_<lang>` behaviour from the codebase,
- reduces method count and review surface.

Verify with “Find usages” before deleting; none of them are referenced from `AppScreen.kt`.

### B11. `rel`/`depth` are aggregated with different operators (P3)

`TopicIndex.kt:521-522` uses `MIN(depth)` and `MAX(rel)`. For multi-topic picks this can describe an article as “depth 0, rel 250” even though that combination comes from two different topics. It does not break correctness, but it makes the debug line misleading and can over-boost an article via `weightedPick`. Compute per-topic rows or use `MAX(rel)` with the matching `depth` (e.g. a correlated subquery ordered by `rel DESC LIMIT 1`).

### B12. `feedback` query binds an integer as text (P3)

`TopicIndex.kt:269`: `WHERE vote = ?` with `arrayOf(vote.toString())`. SQLite’s affinity makes this work today, but bind `vote.toLong()` for clarity and to avoid a full scan if the column type changes.

### B13. `libraryStats()` is expensive and runs on screen open (P3)

`TopicIndex.kt:472-482` counts all articles, anti-joins `seen`, and calls `topicStats()` (another grouped join). On a 20k-article library this can take tens of milliseconds on the main-adjacent IO thread; the Random topics screen calls it on every `libraryReload` (`RandomTopicsScreen.kt:121-123`). Cache the counts in the `topics` table (`article_count` already exists; add `indexed_count`, `unseen_count`) and refresh them after a build, or compute lazily.

### B14. `applyRelevance()` and `upsertArticles()` do one statement per row (P3)

`TopicIndex.kt:389-414` and the article upsert path execute 20k `UPDATE`s in a transaction. That is acceptable but measurable. Batch in chunks of 500 using a temporary table:

```sql
CREATE TEMP TABLE rel_upd(title TEXT PRIMARY KEY, rel INTEGER, cov INTEGER);
-- batch INSERT INTO rel_upd VALUES (?,?,?),...
UPDATE membership SET rel = (SELECT rel FROM rel_upd WHERE title = membership.title),
                      cov = (SELECT cov FROM rel_upd WHERE title = membership.title)
WHERE topic = ? AND title IN (SELECT title FROM rel_upd);
```

### B15. `pick()` can exceed SQLite’s variable limit for many topics (P3)

`TopicIndex.kt:526` binds `topics` **three times**. SQLite’s default `SQLITE_MAX_VARIABLE_NUMBER` is 999 (older Android: 999; newer: 32766). With >333 selected topics this throws. Cap the list (e.g. 100) or use a temporary table of selected topics and join.

### B16. `ArticleRow.rel` can be `-1` even for a complete topic (P3)

Covered by §2.4 B3. Also update the KDoc at `TopicIndex.kt:505` (“articles without a score always pass”) once the behaviour changes.

---

## 7. Performance, loading, FPS and lag

The app already has `PerfMonitor` and `DebugLog` timings; the items below are ordered by expected user-visible impact.

### P1. Random pick latency (DB + CPU)

Per shuffle, `pickFromIndexWithTitle()` does:

1. `index.pick(..., 400)` — a scan of `articles` joined to `membership` for all selected topics, with `NOT IN seen`, filters, and **`ORDER BY RANDOM()`** (`TopicIndex.kt:519`).
2. `index.pick(..., 100)` again for quality articles (line 573-575) if `preferQuality` (default **true**).
3. `index.votedTitles(±1)` and `index.docsFor(...)` for up to 400 candidates + votes (`597-609`).
4. `RelevanceModel.feedbackMultipliers()` builds a TF-IDF corpus over ~400+ documents (`RelevanceModel.kt:225-247`), which the code itself notes costs ~240 ms before the popularity filter (comment at `HomeScreenViewModel.kt:593-594`).

That is a noticeable pause between tapping shuffle and seeing loading, especially on low-end devices.

**Fixes, in order:**

- **Remove `ORDER BY RANDOM()`.** SQLite sorts the whole candidate set. Instead, choose a random `rowid` window and take a contiguous sample, then shuffle client-side:

  ```sql
  SELECT ... FROM articles a
  WHERE a.rowid >= (ABS(RANDOM()) % (SELECT MAX(rowid) FROM articles))
    AND a.bytes >= 0
    AND NOT EXISTS (SELECT 1 FROM seen s WHERE s.title = a.title)
    AND a.title IN (SELECT title FROM membership WHERE topic IN (…) AND rel >= ? AND cov >= ?)
  LIMIT ?
  ```

  This is a heuristic sample (it can under-sample the tail), so keep a fallback to the current query when the sample is empty. A cleaner long-term solution is a `candidates` table refreshed after each build with a random `sample_key`, indexed `(topic, sample_key)`.

- **Merge the two `pick()` calls.** Fetch one pool of 500 and mark quality rows in the result; `weightedPick` already boosts quality by 4×/3×. The separate 100-row query doubles the scan.

- **Cache the feedback model.** Recompute `feedbackMultipliers` only when a vote changes, not on every pick, and store the resulting multiplier per title in a small in-memory map. The affinity table from §5 can serve the same purpose.

- **Add covering indexes:**

  ```sql
  CREATE INDEX membership_topic_rel_cov ON membership(topic, rel, cov, title, depth);
  CREATE INDEX membership_title_cover  ON membership(title, topic, depth, rel, cov);
  ```

  The PK `(topic, title)` already covers `topic IN (…)`, but the `rel/cov` filter and the correlated `MIN(depth)/MAX(rel)` subqueries benefit from the covering index.

- **Measure** with the existing `DebugLog` “PERF pick(…)” line (line 536-540) before/after; target < 30 ms on a mid-range device.

### P2. Section parsing and state copies

`loadPage()` parses the lead synchronously, then parses the remaining sections **serially** and calls `updateTopArticle(shownTitle, parsedExtract.toList(), articleSections.toList())` every 3 sections (`HomeScreenViewModel.kt:1251-1279`). Each call copies the whole `List<List<AnnotatedString>>` and replaces the `HomeScreenState`, which Compose then diffs/hashes. For a 60-section article this is O(n²) copying and repeated large-object equality checks.

**Fixes:**

- Update only the newly parsed section: keep `parsedExtract` as a `SnapshotStateList` or expose sections individually, so adding section 40 does not copy sections 1-39.
- Parse with `Dispatchers.Default` (it is CPU-bound; `withContext(Dispatchers.IO)` at line 1702 competes with disk and network).
- Consider a per-section `produceState` cache so a collapsed section is parsed only when first expanded. The current code parses everything up front, even for collapsed sections.
- Keep the progressive `updateTopArticle` throttling; it is already a good idea.

### P3. Article body composition

- `ParsedBodyText` stages blocks (`STAGE_FIRST = 8`, `STAGE_STEP = 4`, lines 182-184) to avoid one huge first frame; keep this.
- Each `Text` calls `.perfMeasure("paragraph (… chars)")` and `toWikitextAnnotatedStringSafe` is invoked per block; for very long paragraphs, Compose text layout dominates. Consider splitting paragraphs at ~4,000 characters (layout is superlinear in some cases) and setting `BODY_HYPHENS = Hyphens.None` (already done, line 191).
- `AsyncWikitable` eagerly parses (see B8) and composes a `Text` per cell; for wide tables, parse lazily and consider `LazyLayout` only if profiling shows a problem.
- `Cladogram` rebuilds its drawing on every width/font/theme change (`remember(tree, availablePx, …)`, line 85). That is correct, but `BoxWithConstraints` forces a layout pass; the drawing itself is cheap. No action needed unless profiling shows otherwise.

### P4. Feed and recomposition

- `prefetchKey()` includes `preferencesState.value.hashCode()` (`HomeScreenViewModel.kt:203`), so **any** preference change invalidates the prefetch queue, including unrelated toggles (font size, theme). Narrow the key to the fields that actually affect picks (`topicStrictness`, `onlyArticlesWithImages`, `avoidStubs`, `preferQuality`, `popularityBias`, `personalizationEnabled`, language).
- `HomeScreenState` is a large data class containing nested lists; every `copy()` and `collectAsStateWithLifecycle` can trigger broad recomposition if the screen reads the whole object. Read only the fields each composable needs (or split the state). This is the usual cause of “the whole article recomposes while scrolling”.
- `LibraryCard` calls `topicStats()`/`libraryStats()` on every `libraryReload`; cache as in B13.

### P5. Build-phase CPU and logging

- `DebugLog.d` calls build strings eagerly at call sites, e.g. `debugLog.d("Random", "selected topics=${topics.size} indexed=… ${topics.filter { it !in built }}")` (`HomeScreenViewModel.kt:558`). If debug logging is off, the string is still built. Guard expensive message construction with `if (DebugLog.enabled)` or make the logger take a lambda.
- `emit()` is called after every category (`TopicIndexBuilder.kt:273`, `351`, `310`), producing thousands of `IndexBuildState` updates and progress recompositions. Throttle to ~4 Hz (timestamp check) while keeping the counters exact.
- The builder keeps `titles` as a `ConcurrentHashMap` up to 20k entries; fine, but `applyNoiseFilter` allocates a new list per category. Minor.

### P6. Startup and DB

- `TopicIndex` instances are cached per language forever (`TopicIndex.kt:544-549`); each holds an open SQLite helper. With many languages this is a few MB; acceptable, but consider closing the least-recently-used one.
- `onConfigure` enables WAL (good). Ensure `PRAGMA synchronous=NORMAL` for the index DB during bulk builds; the default `FULL` fsyncs every transaction, and `replaceTopic` inserts 20k rows in one transaction (so it is one fsync), but `applyRelevance` and per-batch writes still pay. `NORMAL` is safe with WAL for a cache DB.
- `libraryStats()` file-size read + full counts on the Random topics screen (B13) can make that screen feel slow on large libraries; cache.

### P7. Network and loading perception

- The build shows a progress overlay, but `status` strings change per category; throttling plus a smoothed progress bar reduces jank.
- Article loads: the lead is shown before the rest of the sections, which is good. Ensure the loading indicator is dismissed after the lead (line 1243) and the section parser does not compete with scrolling on the main thread (it runs on IO, so it does not block the frame, but it does compete for CPU; `Default` is better).
- The prefetch queue persists to a preference on every change (`persistPrefetchQueue()`, lines 213-224), which serializes the whole queue to disk. It is small (a few entries), but avoid rewriting on every `invalidatePrefetch()` during a build.

### P8. Image loading

Not audited in depth here, but the usual suspects apply: thumbnails at `pithumbsize=800` in `QUERY_PAGE_DATA` (line 72) are decoded at full size for list rows. Ensure Coil requests a target size (`size(...)`) so it does not decode 800 px bitmaps for 56 dp rows. This is a common source of scroll jank and memory pressure on low-end devices.

### P9. Baseline profile and release build

`app/build.gradle.kts` already sets `debug { isDebuggable = false }` (lines 35-40) and includes a `baselineprofile` module (line 7, 139). Keep both. Verify the release build actually ships the baseline profile and that R8 does not strip the Compose runtime metadata. Do not benchmark FPS on a debuggable build; the comment in the build file is correct.

### P10. Measuring

Use the existing `PerfMonitor`/`DebugLog` timings plus Android Studio Macrobenchmark:

- cold start to first frame,
- scroll a 5,000-word article (`FrameTimingMetric`, P90/P99),
- shuffle 20 times (`pick` timing from the existing PERF log),
- build a 2,000-article topic (requests, 429 count, wall clock).

Record before/after; do not tune thresholds without these numbers.

---

## 8. Priority and effort

| Priority | Item | Effort | Impact |
|---|---|---|---|
| 1 | §1 global rate limiter + `maxlag` + `Retry-After` | 1–2 days | Removes 429 storms; builds become predictable |
| 2 | §2.2 remove global-random fallback / await save | 0.5 day | Eliminates completely unrelated articles |
| 3 | §2.4 `cov` + threshold + search-expansion cap/depth | 1–2 days | Removes the off-topic tail |
| 4 | §3 extract clades from tables + auto-expand tree sections | 1 day | Plant phylogenies render |
| 5 | §4 `topics.enabled` + UI toggle + migration | 1–2 days | Non-destructive deselection |
| 6 | §6 B3 atomic rebuild + B4 seen-after-load | 0.5–1 day | No more half-empty libraries / burned articles |
| 7 | §7 P1 pick performance (`ORDER BY RANDOM`, merged queries, indexes) | 1 day | Faster shuffle, less battery |
| 8 | §5 personalization (optional, off by default) | 2–3 days | New feature, no regression when off |
| 9 | §6 B7 parser robustness + B8 lazy tables | 1 day | Fewer broken articles, less CPU |
| 10 | §7 P2/P4 state copies, prefetch key, logging | 1–2 days | Smoother scrolling and building |
| 11 | §6 B10 dead code, B13 stats cache, B14 batching | 1 day | Maintainability |

Suggested order: **1 → 2 → 3 → 4 → 5 → 6 → 7 → rest**. Items 1–3 share the build pipeline and should be tested together on a real large topic.

---

## 9. Verification plan

### Unit tests

- `WikiRateLimiter`: 10 acquisitions take ≥ ~0.83 s at the 12/s default; `onThrottled` halves at most once per 10 s and `onSuccess` recovers.
- `retryAfterMs`: seconds, HTTP-date, garbage, negative, huge values.
- `RelevanceModel.cov`: the South-America fixture above; empty query; single-word query; docs with only titles.
- `extractCladeBlocks`: clade in a table, nested clades, `{{Cladogram}}` wrapper, no clade.
- `parseClade`: existing fixtures plus the four plant articles.
- `TopicIndex` migration: create a v6 DB fixture, upgrade, assert `enabled` default and reconcile.

### Instrumented / manual

- Build a 5,000-article topic on a mid-range device with debug logging on: count 429s, measure wall clock, confirm progress updates ≤ 4 Hz.
- Shuffle 50 times immediately after saving a new selection: 0 global-random titles.
- Deselect all topics, shuffle: expect the explanatory page, not a random article.
- Toggle a topic off/on: no network requests on re-enable (check the OkHttp log).
- Open *Monocotyledon*, *Poaceae*, *Maize*: trees visible without expanding “Phylogeny” (or with one tap if you choose not to auto-expand).
- Enable personalization, read 10 long articles on one topic, then shuffle 20 times: the share of that topic rises but never exceeds the 3× cap; disable it and confirm no `reading` rows are written.

### Regression guards

- Keep `DebugLog` counters for: `rel < minRel`, `cov < minCov`, depth-2 search hits, 429 count, retry count, pick time. A single “build summary” line makes future regressions obvious.
- Add a CI check that `Retry-After` and `maxlag` appear in the API constants (simple string test) so they are not lost in a refactor.

---

## Implementation status (this build)

The fixes below are implemented in the source tree. Validation was by compilation
(`:app:compileDebugKotlin`), the unit-test suite (`:app:testDebugUnitTest`, **36 tests,
0 failures**) and debug APK assembly (`:app:assembleDebug`); no device/emulator profiling
was available.

- **429 throttling + build throughput:** one app-wide token bucket
  (`data/WikiRateLimiter.kt`) shared by all callers. It starts at **12 requests/s**
  (burst 12), halves once per 10 s on a 429/503 storm, and recovers by 25 % after 40
  consecutive successes. The builder allows 8 crawl/page-info requests, 8 introduction
  requests and 4 detail requests, with a shared cap of 12 in flight. `maxlag=5`, correct
  `Retry-After` parsing (seconds or HTTP date), smaller search page budgets, and paused
  prefetch while a library builds are retained. The crawl is capped at depth 4,
  1 200 categories and 8 000 articles per topic, so a very broad top-level category
  finishes in a bounded time instead of crawling indefinitely.
- **Page views are opt-in:** `prop=pageviews` returns at most ten pages per request and
  ignores `pvilimit` (removed from the query). Page views are now skipped unless the
  popularity preference is non-zero, and otherwise fetched in ten-title batches. This
  removes the dominant wasted-request phase from normal library builds.
- **Unrelated random articles:** relevance coverage is now IDF-weighted and stored in
  `membership.cov`; picks require both `rel` and `cov` thresholds (0/25/45/65 and
  0/20/35/50 by strictness). Generic-root category children and search hits without a
  non-generic shared title word are rejected. The global-random fallback has been removed;
  with no eligible article the app shows an explanatory page instead.
- **Non-destructive deselection:** `topics.enabled` is the source of truth for built
  libraries. Turning a topic off keeps its downloaded articles; a separate, confirmed
  **Remove** action deletes the library. Selection prefs and enabled rows are kept in sync,
  including after migration and process death.
- **Optional personalization:** behavior learning is opt-in and local-only. Reading sessions
  and explicit votes feed `reading` / `topic_affinity`; disabling or resetting clears both
  tables. Picks are affinity-weighted only while enabled.
- **Cladograms:** clade blocks inside layout/nested tables are extracted and rendered as
  trees. Multiline HTML comments are removed before parsing, so constructs such as
  `|1=<!--\n-->{{clade …}}` become real branches instead of one unreadable leaf (this was
  hiding most of the Solanum tree). Block detection tolerates leading whitespace, sections
  auto-expand when a clade arrives late, and parse failures are logged instead of silently
  rendering nothing. Wikitables still parse lazily and are not reparsed when only collapsed
  and reopened.
- **Sidebar latency and placement:** the rail/sidebar section list is a `LazyColumn` with
  stable keys, so collapsed rails no longer compose every article section. Scroll position
  is read through `derivedStateOf` only while the rail is open, and only the rows that use
  it recompose. The edge-swipe hitbox is a quarter of the screen wide (minimum 96 dp), the
  trigger is 16 dp, and both physical sides open the rail with `expand()`. The menu side is
  a setting (left/right); the rail hosts are laid out in a forced-LTR row so an RTL article
  language cannot mirror the user's physical choice, while rail labels/icons keep the app's
  real layout direction.
- **Parser quick wins:** footnote markers, `{{rp}}`, `{{sfrac}}` and small-text templates
  no longer recurse through the whole parser; common/numeric HTML entities use a bounded
  decoder (platform HTML parsing only for unknown entities); named references accept
  case-insensitive tags, spaces around `=`, single quotes and unquoted names; unhandled
  template names are logged once each (bounded registry); the table branch is simplified
  and no longer skips the character after a table; and the nobility-table regex is skipped
  unless its marker is present.
- **Correctness/performance:** DB v7 migration, covering indexes for pick queries,
  `PRAGMA synchronous=NORMAL` with WAL, startup seen-list pruning, shadowed title/offset
  fixes, parser work moved off the main thread, and nested-table content is no longer
  dropped. Article navigation now uses a stable `article-<uid>` content key; the uid is
  created once per load and progressive section/save-state updates leave it unchanged.
  (An earlier per-update revision suffix changed the key on every update and is the crash
  fixed in Round 3.) Unit tests cover zero-coverage/unrelated articles, clade extraction
  (including the real Solanum fixture), HTML section/link handling, reference/entity
  parsing, and comment cleanup (44 tests).
- **Settings latency and accessibility:** Settings now uses a normal `TopAppBar` instead of
  the collapsing `LargeFlexibleTopAppBar`; preferences are loaded asynchronously and the
  screen is a `LazyColumn`. New settings cover the article renderer, menu side (physical
  left/right), reduced motion (`MotionScheme.standard()`), haptic feedback (a no-op
  `HapticFeedback` provider when disabled), and bold article body text.
- **Native wikitext or rendered HTML:** the default renderer is the fast native wikitext
  parser. The optional HTML renderer fetches the REST `page/html` document, splits it into
  the same intro/heading/body shape as `parseSections`, removes only non-reader chrome
  (scripts, edit links, infoboxes, navigation boxes, maintenance banners and raw metadata)
  while keeping ordinary prose, captions, tables and rendered reference lists, rewrites
  relative links to absolute Wikipedia URLs (bare fragment links point at the wiki instead
  of crashing the intent handler), and renders each section through
  `AnnotatedString.fromHtml`. It skips the wikitext article cache/prefetch path and uses
  the same progress UI. The renderer is chosen when an article loads; changing the setting
  affects the next article, not the one already on screen. Round 3 replaced the regex
  sanitizer with a quote-aware, balanced scanner for REST HTML.
- **Background library builds:** when 100 articles are available, the progress overlay
  shows “The library will be built in the background” and a **Start browsing** button.
  Start browsing only dismisses the overlay; it never cancels the build. Back dismisses it
  the same way. Selected-but-unbuilt or incomplete topics resume automatically about three
  seconds after app start through the existing `IndexKeepAliveService`; explicit **Cancel**
  remains the only way to stop a build (process death also stops it, then startup resumes
  it).
- **No repeated random articles:** a visible random article is written to the persistent
  `seen` table *before* it is inserted into the visible back stack. Prefetch only prepares
  articles it can still show, and `resetSeenArticles()` clears the DB seen set, the legacy
  `random_seen_<lang>` preference, the in-memory session set, and the prefetch queue
  together. An article can still be opened again deliberately from history or search.

**Not implemented:** the atomic staging-table rebuild from §6 B3. A rebuild still replaces
the old membership rows, but picks now require `complete = 1`, so a cancelled rebuild makes
the topic unavailable (with a clear message) instead of serving a half-built or partially
enriched library. Completing the staging swap would need another schema/migration step and
was left out to avoid risking existing libraries without device testing.

A cross-scroll LRU cache for parsed wikitables was also deliberately not added. The cached
`AnnotatedString`s contain link callbacks created by the composition that parsed the table;
reusing them after an article/theme change can invoke stale callbacks. The safe part (parse
only on expand, reuse the result while collapsed/reopened) is implemented. A real cache would
need link callbacks to be resolved by the renderer instead of captured during parsing.

---

## Appendix A — file map for the changes

| File | Changes |
|---|---|
| `data/WikiRateLimiter.kt` | **new** adaptive token bucket (5/s, burst 5; halve on 429/503, slow recovery) |
| `data/TopicIndexBuilder.kt` | limiter wiring, bounded crawl (depth 4 / 1 200 categories / 8 000 articles), metadata-only critical path, relevance before completion, `onUsable` callback before optional views, 2 000-view sample, region guard, resume pruning |
| `network/WikipediaApiService.kt` | `maxlag=5`, cache headers on `CATEGORY_CHILDREN_QUERY`, removed unsupported `pvilimit` |
| `data/AppContainer.kt` | optional shared limiter, prefetch pause flag |
| `data/TopicIndex.kt` | DB v7 (`topics.enabled`, `membership.cov`, `reading`, `topic_affinity`), `pick` filter/index changes, `setTopicEnabled`, `enabledTopics`, `recordReading`, `clearReadingData`, `pruneTopicMembership` for oversized legacy libraries |
| `data/RelevanceModel.kt` | `cov` result, empty-doc `rel = 0` |
| `data/InterestResolver.kt` | multi-variant category search, exact-phrase boost, coverage-based ranking, size demotion |
| `data/InterestQuery.kt` | **new** natural-language normalization: filler removal, F1/sport acronyms, “from Finland” → “Finnish” demonym reordering |
| `data/TopicRegionGuard.kt` | **new** continent/region conflict test used by crawl, relevance scoring and tests |
| `ui/homeScreen/viewModel/HomeScreenViewModel.kt` | awaited save, no global-random fallback, `selectedTopicList`, `weightedPick` personalization, reading session hooks, parser table branch, remove dead code, prefetch key |
| `ui/homeScreen/ParsedBodyText.kt` | whitespace-tolerant `blockStartsWith` dispatch for clades/tables/files/infoboxes |
| `ui/homeScreen/Cladogram.kt` | logs unparseable clades instead of silently drawing nothing |
| `ui/homeScreen/AsyncWikitable.kt` | lazy parse, reusable result on collapse/reopen, saveable expansion |
| `ui/homeScreen/ExpandableSection.kt` | auto-expand once when a clade appears, preserving manual collapse |
| `ui/homeScreen/viewModel/HomeScreenViewModel.kt` | parser table branch simplification/off-by-one fix; passes opt-in page-view flag |
| `ui/AppNavigationDrawer.kt` | phone `ModalNavigationDrawer` (288 dp sheet, native drag), tablet wide rail, RTL content on right-hand menu, section list only reads scroll while open |
| `ui/EdgeSwipe.kt` | **removed**; Material drawer owns the edge/drag gesture |
| `ui/AppScreen.kt` | menu-side wiring, phone drawer state, background-build navigation, await save before build |
| `parser/BlockTemplates.kt` | `extractCladeBlocks`; multiline-comment removal before clade parsing |
| `parser/cleanUpWikitext.kt` | multiline comment removal (`DOT_MATCHES_ALL`), nobility-table fast guard |
| `parser/wikitextToAnnotatedString.kt` | direct ref/rp/sfrac/small styling, bounded entity decoder, robust `buildRefList`, bounded unknown-template log |
| `parser/tableAndInfoboxUtil.kt` | optional `TableCell.Clade` |
| `ui/randomTopicsScreen/RandomTopicsScreen.kt` | initialise from enabled topics, saveable state, clear-all semantics |
| `ui/randomTopicsScreen/IndexUi.kt` | enable toggle + destructive remove confirmation |
| `parser/HtmlSections.kt` | **new** REST HTML section splitting, quote-aware/balanced chrome removal, readable prose/captions/tables/reference lists, absolute/fragment-safe links, entity cleanup |
| `ui/settingsScreen/viewModel/PreferencesState.kt` | personalization, `renderMode`, `menuSide`, `reduceMotion`, `hapticFeedback`, `boldArticleText` |
| `ui/settingsScreen/viewModel/SettingsViewModel.kt` + `SettingsAction.kt` | load/save/reset the new preferences |
| `ui/settingsScreen/SettingsScreen.kt` | normal `TopAppBar`; renderer, menu side, reduced motion, haptics, bold-text controls; per-topic enable/disable switches with instant refresh, no rebuild |
| `ui/theme/Theme.kt` | `reduceMotion` selects `MotionScheme.standard()` |
| `MainActivity.kt` | `NoHapticFeedback` provider when the haptic preference is off |
| `ui/randomTopicsScreen/IndexUi.kt` | start-browsing card as soon as the first topic is complete (not a raw 100-article counter), non-cancelling Start browsing |
| `ui/homeScreen/AppHomeScreen.kt` | stable `article-<uid>` content key (no per-update revision suffix) |
| `ui/homeScreen/viewModel/HomeScreenViewModel.kt` | HTML rendering path, alpha-beta clade parser, physical menu side, background-build auto-resume, seen-before-insert, reset of all seen layers; article mutations keep the stable uid |
| `app/src/test/...` | 44 unit tests in 6 suites, including real Poaceae/Solanum clade fixtures, HTML section/link/attribute/nested-chrome tests, parser quick wins, relevance/robustness guards, natural-language query variants and region isolation |

## Appendix B — reproduced/verified findings

- **Clade parser:** verified with a Python replica of `parseClade` on real wikitext for Fern, Flowering plant, Monocotyledon, Eudicots, Orchid, Poaceae, Rose, Maize, Potato, Tomato, Wheat, Rice, Solanaceae, Asteraceae. Every **top-level** clade parses. The Monocotyledon table case confirms the block is inside `{|…|}` and never reaches `Cladogram`.
- **Rate limits:** verified against Wikimedia's published API etiquette (≤3 concurrent, ~200 req/min for UA-only clients, `maxlag`, `Retry-After`, gzip). The bulk gate is now **5 requests/s with a burst of 5**, below the 12/s setting that produced the reported 429 storms, while page-info still runs several requests in flight. It remains adaptive (halve on 429/503). This still needs a real large-topic run to confirm the remaining 429 rate.
- **Fallback path:** verified by reading `HomeScreenViewModel.kt:1111-1121` and `2015-2020`; a global random article is only reachable through the `hasSelectedTopics() == false` branch.
- **Destructive deselection:** verified in `IndexUi.kt:244`, `RandomTopicsScreen.kt:333-339`, `HomeScreenViewModel.kt:483-488`, `TopicIndex.kt:417-428`.
- **Build emptiness during rebuild:** verified in `TopicIndex.kt:334-364` (delete-first) and `138-141` (`isBuilt` ignores `complete`).

No runtime profiling on a device was performed; performance items in §7 are code-path analysis with concrete measurement instructions, not benchmark numbers.

---

# Round 2 — usable libraries, precise topics and native navigation

This round addresses the follow-up report: *“Currently building the library doesn't work at all, no
articles are saved or anything”*, cross-continent topic drift, natural-language topic requests,
instant topic on/off in Settings, and phone drawer jank/wasted space.

## 1. Why the old build never became usable

The reproduced sequence was:

1. A broad topic (reported: **Plants**) stored **84,484** membership rows.
2. Resume treated every stored row as “needs an introduction and page views”.
3. Tens of thousands of optional requests hit **HTTP 429**; the limiter slowed down but the phase
   still could not finish.
4. The user cancelled; `markComplete()` was never reached.
5. Random picks query `topics.filter { complete }`, so **nothing** in that library was served.

The build now has a hard critical path and an optional tail:

```
crawl/search membership
        ↓
page-info metadata (title, description, bytes, image, quality)
        ↓
local relevance scoring (title + short description; intro if an old row has one)
        ↓
markComplete()  ← library is now usable
        ↓
optional, bounded page-view sample (never blocks the library)
```

Key changes:

- Introductions are no longer downloaded during a build. They were the largest, least reliable
  phase and only improved relevance slightly; title + short description already separate
  on-topic from unrelated articles.
- Legacy oversized libraries are pruned to **8,000** members before enrichment. Shallow/core
  members are preferred; the remainder is sampled once. The 84k report therefore resumes in
  seconds of local work instead of hours of requests.
- `markComplete()` happens before the optional popularity download. `onUsable` reports the
  finished topic to the UI immediately, so “Start browsing” works even if the optional phase is
  cancelled.
- The optional page-view phase is capped at **2,000 lookups**. `includePageViews` is only true when
  the popularity preference is on.
- The bulk rate gate is now **5 requests/s, burst 5** (was 12/s). The previous rate reliably
  produced 429 storms; 5/s still finishes a normal 8k library's metadata in about half a minute.
- A resumed build now sets the discovered counter immediately, so the UI no longer says
  “0 articles found” while it is actually working.
- If a category's vocabulary shares no word with the query at all, the scorer keeps the
  (region-filtered) members unscored instead of assigning 0 to every article. Without this,
  the library could be marked complete yet return nothing at the default strictness.

## 2. Topic precision and natural language

Two independent guards prevent unrelated articles:

1. **`TopicRegionGuard`** extracts continent/region words from the topic and candidate. If the
   topic names a region and the candidate names only a *different* region, the candidate is
   rejected. A candidate that mentions both (“African diaspora in South America”) is kept.
   It runs on subcategory branches during the crawl and on article title/description before
   relevance scores are stored. Example: `History of Africa` is rejected for
   `South American history`; `History of Brazil` is not.
2. **Local relevance** still requires title/description coverage of the topic plus the user's
   phrase. Region filtering only removes clear conflicts; it never drops articles that simply
   do not name the region.

Natural-language discovery now normalizes the phrase before searching:

- leading filler (“all”, “every”, “articles about”, …) is removed;
- `F1` / `Formula 1` → `Formula One`, `F2` → `Formula Two`, plus MotoGP/NBA/NFL/EPL aliases;
- “X from Finland” also becomes “Finnish X”, because Wikipedia category names use demonyms;
- category-name search runs the phrase variants, gives an exact-name match a large boost and
  prefers high name coverage over generic categories. `all F1 drivers from Finland` now finds
  `Category:Finnish Formula One drivers` rather than only `Category:Formula One drivers`;
- the same expanded words are used for the local relevance query, so “Finnish” is not lost just
  because the article description says “Finnish racing driver” while the user typed “Finland”.

Built-in topic leaves were expanded with **History by region** (South America, Latin America,
North America, Africa, Asia, Europe, Oceania, Middle East) and a **Motorsport** group (Formula
One, drivers, teams, circuits, WRC, motorcycle racing, NASCAR).

## 3. Settings: disable a library without deleting it

Settings now lists every stored library topic with an enabled switch. Turning one off:

- mirrors `topics.enabled = 0`;
- removes it from the saved random-topic selection;
- invalidates the prepared article queue immediately, so the next shuffle cannot serve an article
  from that library;
- **does not** delete membership or article rows.

Turning it back on restores `enabled = 1` and the saved selection. No rebuild, no download. The
separate destructive **Remove** action in Random article topics is unchanged and still confirms
before deleting. The switch list refreshes on `ON_RESUME`, so returning from a build/remove shows
current data.

## 4. Navigation drawer

The phone UI no longer uses a custom wide modal rail or a custom edge-swipe layer.

- `ModalNavigationDrawer` + a **288 dp** `ModalDrawerSheet` on compact widths. Material owns the
  edge gesture, so the sheet follows the finger and settles on velocity.
- Labels are always visible; no 360 dp expanded rail with a label column that leaves dead space.
- A right-hand menu wraps drawer content in RTL layout direction. The sheet is placed on the
  physical right and labels are right-aligned; list semantics/direction remain natural.
- On a right-hand tablet rail, content is also RTL so labels right-align; the physical row stays
  in forced LTR, and the main app content restores the real app direction.
- Section highlighting no longer reads the feed/article scroll state while the drawer is closed.
  It subscribes only while the drawer is open, removing a scroll-frame recomposition source.
- `ui/EdgeSwipe.kt` was deleted; no duplicate gesture path remains.

## 5. Verification

- `:app:testDebugUnitTest` — **41 tests passed** (6 suites).
- New `TopicDiscoveryTest` covers `F1 + Finland → Finnish Formula One`, filler/synonym expansion,
  and region conflicts (reject other continent, keep mixed-region page, inactive for non-geographic
  topics).
- `:app:assembleDebug` — `app/build/outputs/apk/debug/app-debug.apk` produced (≈14.7 MB).
- No device/emulator was attached in this environment, so drawer gesture feel, right-side label
  alignment, Settings switches and a live Wikipedia build still need one runtime pass. The build
  architecture, database transitions and all pure logic are covered by the green compile/tests.

## 6. Remaining known limits

- The rate limit is a civil default, not a guarantee: Wikimedia can still throttle a very broad
  crawl. A throttled metadata batch now fails the topic with a visible result instead of leaving
  an unusable “complete” library.
- Pruning legacy libraries is one-time and bounded; it does not `VACUUM`, so the database file may
  keep free pages until Android compacts it or the user removes the library.
- The optional page-view sample is a popularity approximation, not an exact view count, by design.

---

# Round 3 — rapid random-navigation crash and complete HTML text

This round addresses the two follow-up reports: the app **crashed during rapid random navigation**, and
the optional **HTML renderer showed only part of an article**.

## 1. Crash: per-update navigation keys

The crash was:

```
java.lang.IllegalArgumentException: Key article-79-85 was used multiple times
    at androidx.compose.runtime.saveable.SaveableStateHolder.kt:88
```

The logged back stack was eight entries deep and had just received several `LoadRandom` calls in a
row. The relevant Navigation 3 (1.0.0-alpha11) behaviour is:

- `SinglePaneScene.key` is the top entry's `contentKey`.
- `SaveableStateHolderNavEntryDecorator` wraps each destination in
  `SaveableStateProvider(entry.contentKey)`.
- `rememberSceneState`/`rememberDecoratedNavEntries` identify shared entries by `contentKey`.

The implementation was using `article-<uid>-<rev>` and `mutateTopArticle` assigned a **new `rev` on
every progressive section update**:

```kotlin
backStack[idx] = transform(top).copy(rev = nextArticleUid())
```

`updateTopArticle` runs every three parsed sections and again at the end. During rapid random
navigation those updates arrived while the previous scene was still fading out. The same article
therefore changed its identity repeatedly, created new scenes, and could collide with the
saveable-state key still held by the outgoing composition. The crash was not a database failure.

The fix is a stable per-load identity:

```kotlin
entry<HomeSubscreen.Article>(clazzContentKey = { "article-${it.uid}" }) { entry -> … }
```

- `HomeSubscreen.Article.uid` is generated once, when the load creates the article, and is never
  copied to a new value.
- The per-update `rev` field was removed from `HomeSubscreen.Article`.
- `mutateTopArticle` now only replaces the article content; it no longer changes the navigation key.
- Different loads still get different uids, so two distinct back-stack entries can never share a
  key. The feed already used the stable key `feed`.
- The existing serialisation of loads (`previousLoad?.join()`) and the eight-entry back-stack bound
  are unchanged.

This keeps the Compose `key(...)`/`SaveableStateProvider` group stable while progressive text and
save-state updates recompose in place. It also avoids the unnecessary scene transition that caused
the jank around rapid shuffles.

## 2. HTML mode: complete article text

The old HTML extractor was deliberately skipping whole sections named `References`, `External
links`, `See also`, `Notes`, `Further reading`, `Bibliography`, `Sources`, `Citations` and
`Footnotes`. It also removed entities such as `mw-references-wrap`, `thumb` and ordinary reference
lists as “chrome”. For a page such as *Athyriaceae*, most of the visible page text lived in the
reference list, so the HTML renderer looked truncated even though the prose it did show was intact.
In addition, regex tag matching (`<[^>]+>`, `<div …>.*?</div>`) cannot safely parse REST HTML:
Parsoid puts **raw `>` characters inside quoted `data-mw` attributes**, and navigation boxes and
infoboxes contain nested tables/divs. The old expressions could leak template/citation source or
remove only part of a nested chrome element.

`parser/HtmlSections.kt` now uses a small quote-aware HTML scanner:

- Tag boundaries are found by respecting single- and double-quoted attribute values, so `>` inside
  `data-mw` is not treated as the end of the tag.
- Chrome elements are removed with balanced closing-tag matching. Nested infoboxes, sidebars and
  navboxes cannot swallow following prose. Malformed/unclosed chrome drops only its opening tag,
  never the rest of the page.
- Scripts, styles, comments, edit-section links, reference markers (`sup.reference`), infoboxes,
  navboxes, sidebars, maintenance banners, hatnotes, short descriptions, hidden categories and
  `mw-empty-elt` raw-metadata spans are removed.
- Attributes are stripped from all surviving tags except `href` on links. This prevents raw
  `data-mw`/`typeof`/`about` values from reaching `AnnotatedString.fromHtml`.
- Readable content is retained: paragraphs, lists, links, inline formatting, image **captions**,
  ordinary (non-infobox/non-navbox) tables, and the rendered reference list.
- All top-level `h2` sections are returned, including `References` and `External links`. In HTML
  mode there is no separate wikitext reference-sheet experience, so the rendered citations are the
  only way to read them. Sections remain collapsible in the reader; `References` does not force the
  whole page open.
- Reference **markers** in prose stay removed. Their link targets are fragment-only and cannot be
  navigated meaningfully through the Android intent handler.

Completeness measured on four live REST fixtures (visible characters after cleaning; the temporary
fixture test is not committed):

| Article | Before | After |
|---|---:|---:|
| *Nicotiana attenuata* | 4,888 | **7,294** |
| *Athyriaceae* | 2,733 | **5,854** |
| *Iris tigridia* | 9,524 | **15,270** |
| *Fredrik Elfving* | — | **28,966** |

The after-values are essentially the complete visible text of each page. The scanner found zero
`{{`, `<ref`, `data-mw` or `&lt;` leaks in all four fixtures, so raw template/citation source no
longer appears as article text.

## 3. Verification

- `:app:testDebugUnitTest` — **44 tests passed** in 6 suites.
- New `HtmlSectionsTest` cases cover:
  - reference text is kept while prose reference markers are removed;
  - a `data-mw` attribute containing a raw `>` neither leaks nor truncates the following paragraph;
  - a nested infobox/navbox does not eat the prose after it;
  - captions, ordinary wikitable cells and rendered reference lists survive;
  - the existing link rewrite and entity handling still work.
- A temporary local fixture test ran the Kotlin implementation against the four live REST documents
  and reproduced the table above; it was removed from the committed test sources.
- `:app:assembleDebug` — `app/build/outputs/apk/debug/app-debug.apk` produced
  (`14,748,006` bytes).

## 4. Remaining limits and decisions

- Wikitext remains the default renderer. HTML is still opt-in and still bypasses the wikitext
  article cache/prefetch path.
- HTML mode now includes rendered reference lists and external-link/see-also sections, so it can be
  longer than before. This is intentional: the requirement was complete article text. Sections are
  collapsed by default, and the reader still renders them lazily.
- Reference markers inside prose are not clickable in HTML mode. Building a second reference-sheet
  experience for HTML was out of scope; the citation text is available in the References section.
- Ordinary tables are kept as text/HTML rather than converted into the native wikitable renderer.
  This preserves their content; their visual layout through `Html.fromHtml` is simpler than the
  wikitext table renderer.
- No emulator/ADB is available in this environment. The crash fix is based on the Navigation 3
  source/decompilation and the logged back-stack sequence; rapid random navigation, progressive
  updates and long HTML pages still need one on-device pass.

## 5. Round 3 file map

| File | Change |
|---|---|
| `ui/homeScreen/AppHomeScreen.kt` | article content key is now `article-<uid>` (no revision suffix) |
| `ui/homeScreen/viewModel/HomeScreenState.kt` | removed per-update `rev`; `uid` is the stable per-load identity |
| `ui/homeScreen/viewModel/HomeScreenViewModel.kt` | `mutateTopArticle` keeps the uid unchanged; comments explain the navigation contract |
| `parser/HtmlSections.kt` | quote-aware scanner, balanced chrome removal, attribute stripping, complete readable content, all h2 sections |
| `app/src/test/.../HtmlSectionsTest.kt` | coverage for raw `>`, nested chrome, captions/tables and retained references |
| `AUDIT_AND_FIXES.md` | this Round 3 record and corrections to the earlier `article-<uid>-<rev>` note |
