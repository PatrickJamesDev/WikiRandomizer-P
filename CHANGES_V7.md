# Changes in this version (v7)

## Images missing from articles (root cause fixed)
* The article parser counted braces to know whether it was "inside a template". After an **infobox or table**
  the counter never returned to zero, so **every picture later in the same section was dropped**. Fixed.
* More picture formats are recognised (webp, tif, bmp ...), captions spanning several lines work, and file
  names containing `?` or `#` no longer break the address.
* Picture templates now show their pictures: `{{Multiple image}}`, `{{Double/Triple image}}`,
  `{{Image array}}`, `{{Photomontage}}`, `{{Wide image}}`, `{{Panorama}}`, `{{Gallery}}`, `{{Image frame}}`
  and `<imagemap>`. (Several pictures become a swipeable gallery.)

## Phylogenetic trees
* `{{Clade}}` / `{{Cladogram}}` are drawn as a real horizontal tree (branch lines, branch labels, taxon names
  at the tips; wide trees scroll sideways). See `Cladogram.kt`, `BlockTemplates.kt`.

## Gestures
* Slide right from the left side of the screen (left fifth of the screen, not the very edge, which Android
  keeps for "back") to open the menu.
* Pull an article down = new random article. Setting: *Pull down for a new random article* (off = old
  "reload page" behaviour).

## Speed
* Articles are cached on disk (`ArticleCache`): re-opening an article, or a redirect to it, needs no network
  (kept up to 3 days; pulling to reload always re-downloads).
* The app reopens the article you were reading when you closed it (straight from disk). The feed fills in
  underneath.
* Up to 3 random articles are prepared in the background (text, page info and first pictures), also right
  after launch, and they are remembered across restarts. Setting: *Preload articles* (off in data-saver mode).
* Picture loading placeholders inside articles are now static (no animation per loading picture).
* **Biggest factor:** the debug APK is slow by design. `build_windows.bat release` builds an optimised APK
  (debug-signed, installable) that scrolls far more smoothly.

## Welcome tutorial
* Interactive tour on first launch (topics, random, pull-down, left-swipe, thumbs, save/search).
  Replay it from Settings > *Welcome tutorial*.

# Fixes after v7 (v8)
* **Crash on every article** (`BlockTemplatesKt ... ExceptionInInitializerError`): the regex `\{\{[^{}]*}}` had an
  unescaped `}}`. Java accepts that, Android's regex engine (ICU) rejects it, and since the regex is created when the
  file loads, every article failed. Now `\{\{[^\{\}]*\}\}`. All other regexes in the project were checked with ICU.
* **Library rebuild speed**: the v6 relevance engine added two extra download phases (introductions, 20 per request,
  and 30-day page views). They now run at the same time, with more parallel requests (crawl/page-info 3 -> 6,
  introductions/views 2 -> 4 each) and OkHttp's per-host limit raised from 5 to 12 (it silently capped parallelism).

# Further changes (v9)
* **Pictures use a standard Wikimedia size** (960 px instead of 720): Wikimedia rate-limits (HTTP 429) every
  thumbnail size outside its standard list (20, 40, 60, 120, 250, 330, 500, 960, 1280, 1920, 3840) when it is not
  already cached, which makes pictures slow or missing.
* Picture downloads (Coil) may run 10 at a time per host instead of 5.
* Library builder: when Wikipedia answers 429/503 **all** parallel requests now wait (shared cool-down) instead of
  every request retrying on its own and hitting the limit again.
* `{{Image frame|content=[[File:..]]|caption=..}}` keeps its caption.
* `build_windows.bat releaseinstall` builds the fast (optimised) APK and installs it on the phone in one go.
* Gradle: 3 GB heap (R8 + Compose were tight at 2 GB), parallel projects, build cache.

# Further changes (v10)
* **Library builder keeps working under rate limits**: the number of simultaneous requests is now adaptive (halved on
  every HTTP 429/503, +1 after each 20 successful requests, between 2 and 10) and shared by all phases and topics.
  Before, a 429 made all parallel requests wait the same time and then restart together, which got them refused
  again (simulated: a server allowing 5 parallel requests never finished before, now it finishes in about 50 s).
* Simulated benchmark (fake Wikipedia, 150 ms latency, 2100 articles): v7 35.0 s, now 14.7 s.
* Math formulas (when "render math" is off) were converted again on every recomposition by a slow parser; now once.
* Start-up: the "why did the last process end" lookup (system call + file I/O) no longer runs on the main thread.

# Further changes (v11)
* Introductions (20 per request, the slowest download) now use 6 parallel requests while page views use 4; the shared
  adaptive limit (max 10 in total) still protects against rate limiting.
* Simulated benchmark (fake Wikipedia, 150 ms latency, 2100 articles): v7 35.0 s -> v10 14.7 s -> v11 10.6 s.
  Strict server (refuses more than 5 parallel requests) still completes (58 s, 4 refusals).

# Android best-practice pass (v12)
* **Foreground service while the library is built** (`IndexKeepAliveService`, type dataSync): a long download in a
  backgrounded app used to be frozen or killed by Android. The service only holds the process (and shows an ongoing
  notification if notifications are allowed); the download itself still runs as before. Stops by itself when the
  build ends or is cancelled, and cleanly at Android 15's data-sync time limit.
* **Backup rules** were the empty templates, so the whole app (including the library database, tens of MB, and the
  article cache) went into the 25 MB cloud backup quota - when over quota the WHOLE backup fails. Now cloud backup
  holds only the small irreplaceable data (settings, history, saved articles); library and caches are excluded. A
  device-to-device transfer still keeps the library.
* **StrictMode in debuggable builds only**: main-thread disk/network work and leaked resources are written to the
  app's own debug log (never crashes, release builds untouched).
* **Accessibility**: the table expand/collapse button and the "use suggestion" arrow in search had no label for
  screen readers.
* Already fine and left alone: lifecycle-aware state collection everywhere, edge-to-edge, predictive back, themed
  icon, per-app language, baseline-profile plumbing, R8 + resource shrinking, HTTP cache.

# Reading experience (v13)
* **Parser: no more quadratic string copying.** For every link, tag, list item and heading the parser copied the
  whole rest of the section (`input.substring(i)`), so long list-like sections took seconds. It now copies only what
  it uses (`substringBeforeFrom` / `substringMatchingParenFrom`). Verified identical on 2.7 million random cases;
  on a 275 000-character list section the scanning part dropped from ~100 ms to ~0-10 ms (desktop JVM; the gain
  on a phone, with garbage collection, is larger).
* **Text no longer jumps when pictures load.** Pictures were a 160 dp grey block that jumped to their real height
  when the download finished, pushing the text you were reading. Now one small request per article (50 pictures per
  call, sizes only) gives every picture's real proportions, so the exact height is reserved up front
  (`ImageAspectCache`; also learns from pictures that were already shown). Skipped in data-saver mode.
* **Long sections no longer stall one frame when they open.** A section with many paragraphs was laid out in a
  single frame; it is now built in slices (8 blocks first, then 4 per frame). Not used when an article opens at a
  saved scroll position (that needs full height) and not repeated for a section that scrolled out and back.

# v14
* **Debug log sharing** now writes a timestamped `.txt` file (cache dir, shared through a FileProvider) instead of
  pasting up to 150 KB of text into the share intent; the whole report (crash + exit reasons + log tail, up to 2 MB) is included.
* **Random pick**: the vote-feedback scoring (~240 ms CPU per pick on 1200 candidates) now runs after the popularity
  filter (~90 candidates) and is capped at 400 candidates, so background prefetch steals far less CPU from the UI thread.
* **Body text**: automatic hyphenation off (`BODY_HYPHENS` in `ParsedBodyText.kt`); it makes text layout slower.
* **Debug log** now records `Jank` lines (frame gaps >= 40 ms with the last action) to pinpoint what stutters.

# v15
* **Main finding from the v14 log: it was a debuggable build** (StrictMode lines). ART runs those in slow
  "debug" mode (no ahead-of-time compile). The `debug` build type is now `isDebuggable = false`
  (`app/build.gradle.kts`), and `install`/`releaseinstall` end with `adb shell cmd package compile -m speed -f`.
* **UI-thread stalls fixed**: `ImagePrefetcher.prefetch` scanned every text block of the article on the UI thread
  (100-150 ms freeze every time pictures were queued); now on a background thread. The OkHttp clients and the Coil
  image loader (SSL setup) are built in a background `wr-warmup` thread instead of during the first frames.
* **New `PerfMonitor`** (only while the debug log is ON): per-frame phase breakdown (`JANK`), what the UI thread was
  executing during slow frames (stack sampling: region, app function, call chain), slow UI messages (`SLOWMSG`),
  2-second `VITALS` (fps, frame histogram, CPU per thread, GC, CPU MHz, thermal, touch/scroll state), and an `ENV`
  header (debuggable?, refresh rate, battery saver, animation scales, ART compile status).

# v16
* Debug log (performance monitor) additions: `recomposed: Name N` in VITALS (how often PageContent / ParsedBodyText /
  ExpandableSection recompose per 2 s), `SLOWMEASURE` (measure+layout of a section body or a paragraph >= 6 ms),
  a `list: N items on screen [keys] of M` suffix on JANK/SLOWMEASURE lines (which sections were visible), and an
  `EXTRAS` block every 10 s (PSS split incl. graphics, Coil memory/disk cache fill, system load average).
* Article list items now declare a `contentType` so Compose can reuse compositions while scrolling.

# v17
* Build fix: with the debug build no longer debuggable, Gradle ran lint as a gate and failed on two backup-rule
  files (excludes outside any included path). Removed those redundant excludes (only app_database is included, so
  everything else is already left out) and turned the release-lint gate off (app/build.gradle.kts).
