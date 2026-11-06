# Topic index (how "Random topic" works now)

When you press **Save** on the topics screen, the app builds a local library for any new topic:

1. **Crawl** - walks the topic's category and all sub-categories (depth <= 5, <= 2500 categories,
   <= 20 000 articles per topic), skipping maintenance/stub/list/template categories.
   If the hand-written category name doesn't exist, it searches Wikipedia for the closest category.
2. **Check** - for every article, one batched lookup stores size, whether it has a lead image,
   and whether it is a Good or Featured article.
3. **Store** - everything goes into `topic_index_<lang>.db` (plain SQLite, one file per language).

Pressing **Random** is then a local query: unseen articles from your topics, filtered by your
image / stub settings, weighted towards Featured > Good > long articles with images.
When everything has been shown, the cycle restarts. If a chosen topic isn't indexed yet, the old
live category walk is used. It never falls back to unrelated random articles when topics are set.

"Rebuild" re-crawls the selected topics (use it every few months to pick up new articles).
Cancelling keeps topics that already finished.

## Natural-language interests
Type a description ("succulent plants of North America, and American muscle cars made before 1980").
It is split into phrases; for each phrase the app (1) searches category names, (2) full-text searches
the phrase, and (3) looks at which categories the top hits share. The matching categories appear as
chips: untick the ones that don't fit, then "Add". They become normal topics and are built like any other.
Limits such as "before 1980" or "of North America" can only be honoured through categories that happen
to encode them (e.g. "1970s automobiles"); pick those chips if they appear.

## Resilience
Every request has a 45 s timeout and is retried with back-off for up to 10 minutes (3 minutes per
sub-category; failed ones are retried again at the end), honouring HTTP 429 / Retry-After. The progress
screen shows live counters, elapsed time, "last response N s ago" and any retry message. The final screen
always lists, per topic, whether it worked and how many articles / with images / Good / Featured.

## Debug log
Settings > Debug logging. Writes to `Android/data/org.nsh07.wikireader/files/logs/wikireader-debug.log`
(rotates at 4 MB). "View debug log" shows, copies or shares it. Contains: every HTTP request (URL, status,
protocol, cache hit, bytes, ms), per-section parse times, random-pick pipeline, library build steps,
memory use, uncaught exceptions. Crashes are recorded to `last_crash.txt` even with logging off.

## Relevance (v2)
- While crawling, a sub-category is followed only if it is a direct child of the topic, or shares a word
  (or a long word stem, e.g. Solanaceae ~ Solanum) with its parent or with the topic. This stops the crawl
  leaking from "nightshades" into potatoes, potato pests, and so on. Skipped categories are listed in the
  debug log as `GATE blocked '<category>'`.
- Each article remembers how far it is from the topic's own category. Closer articles are picked more often.
- Random never falls back to unrelated articles: if no selected topic is built, you get a message telling you
  to open Random topics and press Save.
- Upgrading from the first library version clears old libraries once (the per-article cache is kept, so the
  rebuild is quick). Press Save on the topics screen to rebuild.

## Resumable builds and library management (v3)
- The article list of a topic is saved right after the crawl, before the slow per-article detail phase. If the
  app is killed or you cancel, "Save" continues where it stopped (the crawl is not repeated).
- The topics screen shows, per topic: articles, with images, Good, Featured, when it was built, and whether it
  is unfinished. "Remove" deletes a topic's data. "Rebuild" re-crawls from scratch.
- "Select all" was removed: ~300 topics would take hours to build.
- Natural-language results show each category's size in brackets and demote huge generic categories;
  categories above 20 000 entries are listed but not pre-ticked.
- The library database uses write-ahead logging, so Random never waits for a running build.

## No repeats, relevance and crash diagnostics (v4)
- **Seen list:** every article that is shown (random or opened any other way) goes into a permanent list in the
  library database. Random never offers it again - it is not reset automatically, survives rebuilds and
  removing topics, and is only cleared by "Start over" in the library card. When nothing unseen is left, the
  app says so instead of repeating. Redirect pages are never indexed, and a title that resolves to a different
  article marks both as seen.
- **Descriptions:** article descriptions are stored and compared with the topic's key words. Each article gets a
  relevance score (used as a weighting). For topics created from a natural-language description, articles
  whose title and description match none of the user's words are removed ("N unrelated articles removed").
- **Better topic suggestions:** categories whose names don't contain any of your words (e.g. "Tobacco" for
  "plants in the family Solanaceae") are no longer pre-ticked.
- **Crash reports** now include the app state (back stack, loading flags), the main thread's stack and the last
  150 actions, even with logging off. On Android 11+ the log viewer also shows why the previous runs ended
  (crash, native crash, ANR, killed for low memory, ...). With debug logging on, a watchdog reports when the UI
  thread is blocked, and lifecycle / memory-pressure events are logged.

## Saving, images, popularity (v5)
- **Saving** an article is now just a bookmark (title, language, page id, thumbnail, description); the article opens
  online. Settings -> "Save articles for offline reading" also stores the text. Saved-article state changes go through
  one locked, always-unique update (this removes the save crash: a failed save returned the article to a state equal to
  the one the navigation layer was still animating away from).
- **Images**: the full-screen viewer has a Download button (Pictures/WikiReader; SVG goes to Download/WikiReader). While
  you read, all images of the article are loaded in the background (disk cache), also as further sections finish.
  In dark theme, transparent images (SVG chemical structures, formulas, transparent PNGs) get a white backing.
- **Popularity**: libraries store each article's views of the last 30 days. Settings -> "Article popularity" slider:
  only obscure / prefer less-read / none / prefer widely-read / only popular. Existing libraries are marked unfinished
  once; press Save to fetch the missing view counts (no re-crawl).

## Topic relevance engine (v6)
Categories only say which bucket an article sits in. Whether it is about *your niche* is decided locally, like a
small search engine, on the text of every article in the library (title, description, introduction):
1. **TF-IDF**: distinctive words count a lot, words found everywhere count little.
2. **Pseudo-relevance feedback (Rocchio)**: the articles matching your own words best become seeds; the vocabulary
   they share joins the query ("muscle cars" also matches V8, rear-wheel drive, Pontiac...).
3. **Score** = similarity to that query relative to a typical seed (100 = typical best match). Stored per article.
   Settings -> "Topic strictness" hides low scores at pick time (no rebuild needed).
Also:
- **Recall**: for natural-language topics, the top 300 full-text search results for your phrase are added to the
  library, then scored like everything else (niche topics are often intersections no single category covers).
- **Exclusions**: "plants in the family Solanaceae, but not tobacco products" - an article is removed when it
  mentions every word of an excluded phrase; matching categories are never suggested.
- **Feedback**: the thumbs button (bottom bar) -> "More like this" / "Not interested". Votes re-rank every later pick
  by similarity to the liked / disliked articles. "Not interested" also hides the article and loads the next one.
- Existing libraries resume once to fetch introductions (no re-crawl). Press Rebuild on a natural-language topic to
  also run the search expansion.

## Search topics, instant Random, robustness (v7)
- **Search topics**: for a niche Wikipedia has no category for, press "Also add as a search topic" under a phrase in the
  description box. Its library is built from the top 500 full-text search results, then scored and filtered like
  any other topic (exclusions, strictness, feedback all apply). Shown as "🔍 phrase".
- **Instant Random**: while you read a random article, the next one is chosen and its text downloaded in the
  background (not in data-saver mode). It is only marked as seen when actually shown, and is discarded if your
  topics, settings, votes or library change in the meantime.
- **Safer UI reads**: the back stack is changed by the loader thread, so places that checked the top screen type and then
  cast it (two separate reads) now read once. Fixes a rare crash and a deterministic one (save-button tooltip on the feed).
- The library card shows the storage used; topics with fewer than 40 articles get a hint; network errors are shown in
  plain words.
