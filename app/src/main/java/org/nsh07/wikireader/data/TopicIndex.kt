package org.nsh07.wikireader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Per-topic breakdown shown in the UI. */
data class TopicStat(
    val topic: String,
    val resolved: String,
    val discovered: Int,
    val indexed: Int,
    val withImages: Int,
    val good: Int,
    val featured: Int,
    val builtAt: Long,
    /** False while article details are still being collected (build interrupted / running). */
    val complete: Boolean,
    /** Whether this topic takes part in random picks. Disabling keeps its downloaded library. */
    val enabled: Boolean = true
)

data class LibraryStats(
    /** Size of the library database on disk. */
    val sizeBytes: Long = 0L,
    val totalArticles: Int,
    /** Articles never shown yet (not in the permanent "seen" list). */
    val unseen: Int,
    val topics: List<TopicStat>
)

/** One indexed article. [tier]: 0 = normal, 1 = Good article, 2 = Featured article. */
data class ArticleRow(
    val title: String,
    val bytes: Int,
    val hasImage: Boolean,
    val tier: Int,
    /** Shortest category distance from the topic's own category (0 = directly in it). */
    val depth: Int = 0,
    /** Wikidata short description, used to judge relevance. null = not fetched yet. */
    val descr: String? = null,
    /** How many of the topic's key words the article's title/description matches; -1 = unknown. */
    val rel: Int = -1,
    /** Absolute topic-word coverage, 0..100; -1 = not scored yet (passes every threshold). */
    val cov: Int = -1,
    /** Views in the last 30 days; -1 = unknown. Used to prefer obscure or popular articles. */
    val views: Int = -1,
    /** One selected topic this article belongs to (used for learned topic affinity). */
    val topic: String = ""
)

/** Membership row used by the relevance pass. */
data class MemberDescr(val title: String, val depth: Int, val descr: String?)

/**
 * On-device topic index: for every topic the user selected, the articles of that category's
 * subtree, with size / image / quality / description info, so the Random button is an instant
 * local lookup instead of a flaky chain of network calls.
 *
 * - Builds are resumable (article list saved right after the crawl, details filled in later).
 * - "Seen" is a separate permanent list: an article that was ever shown is never offered again
 *   until the user explicitly presses "Start over"; it survives rebuilds and topic removal.
 *
 * Plain SQLite (no Room) on purpose: no schema/migration coupling with the app database.
 * One file per Wikipedia language.
 */
class TopicIndex private constructor(context: Context, val lang: String) :
    SQLiteOpenHelper(context.applicationContext, "topic_index_$lang.db", null, 7) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // WAL: the Random button can read while a library build is writing.
        db.enableWriteAheadLogging()
        // This is a rebuildable cache, so NORMAL is safe with WAL and avoids an fsync per
        // transaction during the many small batch writes of a library build.
        try {
            db.execSQL("PRAGMA synchronous=NORMAL")
        } catch (e: Exception) {
            DebugLog.w("TopicIndex", "could not set synchronous=NORMAL: ${e.message}")
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE articles(title TEXT PRIMARY KEY, bytes INTEGER NOT NULL, " +
                    "img INTEGER NOT NULL, tier INTEGER NOT NULL, seen INTEGER NOT NULL DEFAULT 0, " +
                    "descr TEXT, views INTEGER NOT NULL DEFAULT -1, intro TEXT)"
        )
        db.execSQL(
            "CREATE TABLE membership(topic TEXT NOT NULL, title TEXT NOT NULL, " +
                    "depth INTEGER NOT NULL DEFAULT 0, rel INTEGER NOT NULL DEFAULT -1, " +
                    "cov INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(topic, title))"
        )
        db.execSQL("CREATE INDEX membership_title ON membership(title)")
        db.execSQL("CREATE INDEX membership_topic_rel ON membership(topic, rel, cov)")
        // Covering indexes for the two shapes `pick()` uses: filtering by topic/rel/cov, and the
        // correlated depth/rel/cov lookups by title for the sampled rows.
        db.execSQL(
            "CREATE INDEX membership_topic_cover ON membership(topic, rel, cov, title, depth)"
        )
        db.execSQL(
            "CREATE INDEX membership_title_cover ON membership(title, topic, depth, rel, cov)"
        )
        db.execSQL(
            "CREATE TABLE topics(topic TEXT PRIMARY KEY, built_at INTEGER NOT NULL, " +
                    "resolved TEXT NOT NULL, article_count INTEGER NOT NULL, " +
                    "complete INTEGER NOT NULL DEFAULT 1, enabled INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL("CREATE TABLE seen(title TEXT PRIMARY KEY, seen_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX seen_at_idx ON seen(seen_at)")
        db.execSQL("CREATE TABLE feedback(title TEXT PRIMARY KEY, vote INTEGER NOT NULL)")
        // Optional behaviour-learning personalization. `reading` remembers how long one article
        // was read; `topic_affinity` turns that into a per-topic multiplier.
        db.execSQL(
            "CREATE TABLE reading(title TEXT PRIMARY KEY, topic TEXT NOT NULL, " +
                    "seconds REAL NOT NULL DEFAULT 0, progress REAL NOT NULL DEFAULT 0, " +
                    "read_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX reading_topic ON reading(topic)")
        db.execSQL(
            "CREATE TABLE topic_affinity(topic TEXT PRIMARY KEY, score REAL NOT NULL DEFAULT 0, " +
                    "reads INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)"
        )
    }

    /**
     * The file was written by a NEWER version of the app (e.g. after installing an older build).
     * SQLiteOpenHelper throws "Can't downgrade database" by default, which crashed every random
     * request. The library is only a cache of public data, so start it again from empty.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (table in listOf(
            "articles", "membership", "topics", "seen", "feedback", "reading", "topic_affinity"
        )) {
            db.execSQL("DROP TABLE IF EXISTS $table")
        }
        onCreate(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE membership ADD COLUMN depth INTEGER NOT NULL DEFAULT 0")
            db.execSQL("DELETE FROM membership")
            db.execSQL("DELETE FROM topics")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE topics ADD COLUMN complete INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 4) {
            // v4: descriptions for relevance + a permanent seen list.
            db.execSQL("ALTER TABLE articles ADD COLUMN descr TEXT")
            db.execSQL("ALTER TABLE membership ADD COLUMN rel INTEGER NOT NULL DEFAULT -1")
            db.execSQL("CREATE TABLE IF NOT EXISTS seen(title TEXT PRIMARY KEY)")
            db.execSQL("INSERT OR IGNORE INTO seen(title) SELECT title FROM articles WHERE seen = 1")
            // Existing libraries have no descriptions yet: mark them unfinished so pressing
            // Save resumes them (no re-crawl; only the descriptions are fetched).
            db.execSQL("UPDATE topics SET complete = 0")
        }
        if (oldVersion < 5) {
            // v5: page views (obscure / popular preference). Unfinished topics resume and only
            // fetch the missing views, nothing is re-crawled.
            db.execSQL("ALTER TABLE articles ADD COLUMN views INTEGER NOT NULL DEFAULT -1")
            db.execSQL("UPDATE topics SET complete = 0")
        }
        if (oldVersion < 6) {
            // v6: article introductions (the text the relevance model reads) + like/dislike votes.
            db.execSQL("ALTER TABLE articles ADD COLUMN intro TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS feedback(title TEXT PRIMARY KEY, vote INTEGER NOT NULL)")
            db.execSQL("UPDATE topics SET complete = 0")
        }
        if (oldVersion < 7) {
            // v7: non-destructive topic selection, absolute relevance coverage, seen timestamps and
            // opt-in behaviour learning. Existing libraries keep working:
            //  - cov = -1 means "not scored yet", so every threshold lets old rows through until
            //    the topic is rebuilt.
            //  - enabled starts at 0; the ViewModel reconciles it from the saved topic selection.
            db.execSQL("ALTER TABLE membership ADD COLUMN cov INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE topics ADD COLUMN enabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE seen ADD COLUMN seen_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE seen SET seen_at = CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                        "WHERE seen_at = 0"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS reading(title TEXT PRIMARY KEY, topic TEXT NOT NULL, " +
                        "seconds REAL NOT NULL DEFAULT 0, progress REAL NOT NULL DEFAULT 0, " +
                        "read_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS reading_topic ON reading(topic)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS topic_affinity(topic TEXT PRIMARY KEY, " +
                        "score REAL NOT NULL DEFAULT 0, reads INTEGER NOT NULL DEFAULT 0, " +
                        "updated_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS membership_topic_rel ON membership(topic, rel, cov)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS membership_topic_cover " +
                        "ON membership(topic, rel, cov, title, depth)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS membership_title_cover " +
                        "ON membership(title, topic, depth, rel, cov)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS seen_at_idx ON seen(seen_at)")
        }
    }

    // ── status ────────────────────────────────────────────────────────────────

    /** The topic has a usable (possibly still partial) library. */
    fun isBuilt(topic: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM topics WHERE topic = ?", arrayOf(topic))
            .use { it.moveToFirst() }

    /** Topic exists but its article details were never finished (interrupted build). */
    fun isIncomplete(topic: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM topics WHERE topic = ? AND complete = 0", arrayOf(topic)
        ).use { it.moveToFirst() }

    /**
     * Ready to serve random articles. Unlike [isBuilt], a half-finished rebuild (membership written
     * but details/relevance still missing) does not count: picking from it exposed unrelated
     * articles while the library was being rebuilt.
     */
    fun isComplete(topic: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM topics WHERE topic = ? AND complete = 1", arrayOf(topic)
        ).use { it.moveToFirst() }

    // ── selection (non-destructive) ───────────────────────────────────────────

    /** Topics the user currently wants random articles from. */
    fun enabledTopics(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT topic FROM topics WHERE enabled = 1 ORDER BY topic", null)
            .use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    fun isTopicEnabled(topic: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM topics WHERE topic = ? AND enabled = 1", arrayOf(topic)
        ).use { it.moveToFirst() }

    /** Turns [topic] on/off without deleting a single downloaded article. */
    fun setTopicEnabled(topic: String, enabled: Boolean) {
        writableDatabase.execSQL(
            "UPDATE topics SET enabled = ? WHERE topic = ?",
            arrayOf<Any?>(if (enabled) 1 else 0, topic)
        )
    }

    /** One-time migration from the old `random_topics` preference. */
    fun enableTopics(topics: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE topics SET enabled = 0")
            val stmt = db.compileStatement("UPDATE topics SET enabled = 1 WHERE topic = ?")
            for (topic in topics) {
                stmt.clearBindings()
                stmt.bindString(1, topic)
                stmt.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Topics containing [title], newest selection first. Used by personalization. */
    fun topicsForTitle(title: String): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT m.topic FROM membership m JOIN topics t ON t.topic = m.topic " +
                    "WHERE m.title = ? AND t.enabled = 1 ORDER BY m.topic",
            arrayOf(title)
        ).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    /** Needs a (re)build: missing entirely, or interrupted before it finished. */
    fun needsBuild(topic: String): Boolean = !isBuilt(topic) || isIncomplete(topic)

    fun resolvedCategory(topic: String): String? =
        readableDatabase.rawQuery("SELECT resolved FROM topics WHERE topic = ?", arrayOf(topic))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    /** title -> depth of everything stored for [topic] (used to resume an interrupted build). */
    fun membership(topic: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        readableDatabase.rawQuery(
            "SELECT title, depth FROM membership WHERE topic = ?", arrayOf(topic)
        ).use { c -> while (c.moveToNext()) out[c.getString(0)] = c.getInt(1) }
        return out
    }

    // ── writing ───────────────────────────────────────────────────────────────

    /** Sub-set of [titles] whose details (incl. description) are already stored. */
    fun knownTitles(titles: List<String>): Set<String> {
        val out = HashSet<String>()
        titles.chunked(500).forEach { chunk ->
            val ph = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT title FROM articles WHERE descr IS NOT NULL AND title IN ($ph)",
                chunk.toTypedArray()
            ).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        }
        return out
    }

    /** Subset of [titles] that still lack an article introduction (all of them when [all]). */
    fun titlesMissingIntros(titles: List<String>, all: Boolean): List<String> {
        if (all) return titles
        val have = HashSet<String>()
        titles.chunked(500).forEach { chunk ->
            val ph = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT title FROM articles WHERE intro IS NOT NULL AND length(intro) > 0 AND title IN ($ph)",
                chunk.toTypedArray()
            ).use { c -> while (c.moveToNext()) have.add(c.getString(0)) }
        }
        return titles.filter { it !in have }
    }

    /** Stores fetched introductions. The next relevance pass reads them through [memberDocs]. */
    fun updateIntros(intros: Map<String, String>) {
        if (intros.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val upd = db.compileStatement("UPDATE articles SET intro = ? WHERE title = ?")
            for ((title, intro) in intros) {
                upd.clearBindings()
                upd.bindString(1, intro)
                upd.bindString(2, title)
                upd.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Subset of [titles] that still lack page-view data (all of them when [all]). */
    fun titlesMissingViews(titles: List<String>, all: Boolean): List<String> {
        if (all) return titles
        val have = HashSet<String>()
        titles.chunked(500).forEach { chunk ->
            val ph = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT title FROM articles WHERE views >= 0 AND title IN ($ph)", chunk.toTypedArray()
            ).use { c -> while (c.moveToNext()) have.add(c.getString(0)) }
        }
        return titles.filter { it !in have }
    }

    /** Texts of everything in [topic], for the relevance model. */
    fun memberDocs(topic: String): List<RelDoc> {
        val out = ArrayList<RelDoc>()
        readableDatabase.rawQuery(
            "SELECT m.title, a.descr, a.intro FROM membership m " +
                    "JOIN articles a ON a.title = m.title WHERE m.topic = ? AND a.bytes >= 0",
            arrayOf(topic)
        ).use { c ->
            while (c.moveToNext()) {
                val t = c.getString(0)
                out.add(RelDoc(t, t, if (c.isNull(1)) null else c.getString(1), if (c.isNull(2)) null else c.getString(2)))
            }
        }
        return out
    }

    /** Texts of specific articles (candidates / voted ones), for feedback re-ranking. */
    fun docsFor(titles: Collection<String>): List<RelDoc> {
        val out = ArrayList<RelDoc>()
        titles.toList().chunked(500).forEach { chunk ->
            val ph = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT title, descr, intro FROM articles WHERE title IN ($ph)", chunk.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(0)
                    out.add(RelDoc(t, t, if (c.isNull(1)) null else c.getString(1), if (c.isNull(2)) null else c.getString(2)))
                }
            }
        }
        return out
    }

    // ── votes ─────────────────────────────────────────────────────────────────

    /** [vote]: +1 more like this, -1 not interested, 0 = remove the vote. */
    fun setVote(title: String, vote: Int) {
        if (vote == 0) writableDatabase.execSQL("DELETE FROM feedback WHERE title = ?", arrayOf<Any?>(title))
        else writableDatabase.execSQL(
            "INSERT OR REPLACE INTO feedback(title, vote) VALUES(?, ?)", arrayOf<Any?>(title, vote)
        )
    }

    /** Current vote for [title]: +1, -1, or 0 when the article has not been rated. */
    fun getVote(title: String): Int {
        readableDatabase.rawQuery(
            "SELECT vote FROM feedback WHERE title = ? LIMIT 1",
            arrayOf(title)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun votedTitles(vote: Int): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT title FROM feedback WHERE vote = ?", arrayOf(vote.toString()))
            .use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    fun updateViews(views: Map<String, Int>) {
        if (views.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val upd = db.compileStatement("UPDATE articles SET views = ? WHERE title = ?")
            for ((t, v) in views) {
                upd.clearBindings()
                upd.bindLong(1, v.toLong())
                upd.bindString(2, t)
                upd.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertArticles(rows: List<ArticleRow>) {
        if (rows.isEmpty()) return
        val t0 = System.nanoTime()
        val db = writableDatabase
        db.beginTransaction()
        try {
            // INSERT OR IGNORE + UPDATE instead of UPSERT: UPSERT needs SQLite 3.24 (Android 11+).
            val insert = db.compileStatement(
                "INSERT OR IGNORE INTO articles(title, bytes, img, tier, seen, descr) VALUES(?, ?, ?, ?, 0, ?)"
            )
            val update = db.compileStatement(
                "UPDATE articles SET bytes = ?, img = ?, tier = ?, descr = ? WHERE title = ?"
            )
            for (r in rows) {
                val d = r.descr ?: ""
                insert.clearBindings()
                insert.bindString(1, r.title)
                insert.bindLong(2, r.bytes.toLong())
                insert.bindLong(3, if (r.hasImage) 1L else 0L)
                insert.bindLong(4, r.tier.toLong())
                insert.bindString(5, d)
                insert.executeInsert()

                update.clearBindings()
                update.bindLong(1, r.bytes.toLong())
                update.bindLong(2, if (r.hasImage) 1L else 0L)
                update.bindLong(3, r.tier.toLong())
                update.bindString(4, d)
                update.bindString(5, r.title)
                update.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            DebugLog.d("TopicIndex", "PERF upsertArticles(${rows.size}) took ${(System.nanoTime() - t0) / 1_000_000} ms")
        }
    }

    /**
     * Atomically replaces the membership of [topic]. Pass [complete] = false right after the
     * crawl so a killed app can resume; call [markComplete] once details are collected.
     */
    fun replaceTopic(
        topic: String,
        resolvedCategory: String,
        titles: Map<String, Int>,
        complete: Boolean
    ) {
        val t0 = System.nanoTime()
        val db = writableDatabase
        // Preserve the user's on/off choice when a library is rebuilt; a brand-new topic starts
        // enabled because the user just selected it.
        val wasEnabled = db.rawQuery(
            "SELECT enabled FROM topics WHERE topic = ?", arrayOf(topic)
        ).use { if (it.moveToFirst()) it.getInt(0) == 1 else true }
        db.beginTransaction()
        try {
            db.delete("membership", "topic = ?", arrayOf(topic))
            val insert = db.compileStatement(
                "INSERT OR IGNORE INTO membership(topic, title, depth, rel, cov) VALUES(?, ?, ?, -1, -1)"
            )
            for ((t, d) in titles) {
                insert.clearBindings()
                insert.bindString(1, topic)
                insert.bindString(2, t)
                insert.bindLong(3, d.toLong())
                insert.executeInsert()
            }
            db.execSQL(
                "INSERT OR REPLACE INTO topics(topic, built_at, resolved, article_count, complete, enabled) " +
                        "VALUES(?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    topic, System.currentTimeMillis(), resolvedCategory, titles.size,
                    if (complete) 1 else 0, if (wasEnabled) 1 else 0
                )
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            DebugLog.d("TopicIndex", "PERF replaceTopic('$topic', ${titles.size} titles, complete=$complete) took ${(System.nanoTime() - t0) / 1_000_000} ms")
        }
    }

    fun markComplete(topic: String) {
        writableDatabase.execSQL(
            "UPDATE topics SET complete = 1, built_at = ? WHERE topic = ?",
            arrayOf<Any?>(System.currentTimeMillis(), topic)
        )
    }

    /**
     * Caps an existing library at [maxArticles]. Older app versions could store tens of thousands
     * of rows for one broad topic; resuming those used to spend hours fetching introductions and
     * making the app unusable. Shallow (more central) members are preferred, the rest is sampled
     * deterministically by SQLite's RANDOM() once, so the result is stable after it is stored.
     *
     * @return the number of members left in [topic].
     */
    fun pruneTopicMembership(topic: String, maxArticles: Int): Int {
        val before = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM membership WHERE topic = ?", arrayOf(topic)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (before <= maxArticles) return before
        val db = writableDatabase
        val t0 = System.nanoTime()
        db.beginTransaction()
        try {
            db.execSQL(
                "DELETE FROM membership WHERE topic = ? AND title NOT IN (" +
                        "SELECT title FROM membership WHERE topic = ? " +
                        "ORDER BY depth ASC, RANDOM() LIMIT ?)",
                arrayOf<Any?>(topic, topic, maxArticles.toLong())
            )
            // Titles that no remaining membership row points at are unreachable for picks; drop
            // their metadata too so the article table does not stay oversized.
            db.execSQL("DELETE FROM articles WHERE title NOT IN (SELECT title FROM membership)")
            db.execSQL(
                "UPDATE topics SET article_count = " +
                        "(SELECT COUNT(*) FROM membership WHERE topic = ?) WHERE topic = ?",
                arrayOf<Any?>(topic, topic)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val after = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM membership WHERE topic = ?", arrayOf(topic)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        DebugLog.w(
            "TopicIndex",
            "pruned '$topic' from $before to $after articles in " +
                    "${(System.nanoTime() - t0) / 1_000_000} ms (limit $maxArticles)"
        )
        return after
    }

    /** Titles, depths and descriptions of a topic's members: input for the relevance pass. */
    fun memberDescriptions(topic: String): List<MemberDescr> {
        val out = ArrayList<MemberDescr>()
        readableDatabase.rawQuery(
            "SELECT m.title, m.depth, a.descr FROM membership m " +
                    "LEFT JOIN articles a ON a.title = m.title WHERE m.topic = ?",
            arrayOf(topic)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(MemberDescr(c.getString(0), c.getInt(1), if (c.isNull(2)) null else c.getString(2)))
            }
        }
        return out
    }

    /** Stores relevance/coverage scores and removes [drop]ped titles from the topic. */
    fun applyRelevance(
        topic: String,
        rel: Map<String, Int>,
        cov: Map<String, Int>,
        drop: Collection<String>
    ) {
        val t0 = System.nanoTime()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val upd = db.compileStatement(
                "UPDATE membership SET rel = ?, cov = ? WHERE topic = ? AND title = ?"
            )
            for ((t, r) in rel) {
                upd.clearBindings()
                upd.bindLong(1, r.toLong())
                // A missing coverage value means "could not judge": keep it permissive (-1)
                // instead of hiding the article.
                upd.bindLong(2, (cov[t] ?: -1).toLong())
                upd.bindString(3, topic)
                upd.bindString(4, t)
                upd.executeUpdateDelete()
            }
            val del = db.compileStatement("DELETE FROM membership WHERE topic = ? AND title = ?")
            for (t in drop) {
                del.clearBindings()
                del.bindString(1, topic)
                del.bindString(2, t)
                del.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            DebugLog.d("TopicIndex", "PERF applyRelevance('$topic': ${rel.size} scored, ${drop.size} dropped) took ${(System.nanoTime() - t0) / 1_000_000} ms")
        }
    }

    /** Marks every remaining member as unscored so strictness does not hide the whole library. */
    fun resetRelevance(topic: String) {
        writableDatabase.execSQL(
            "UPDATE membership SET rel = -1, cov = -1 WHERE topic = ?", arrayOf(topic)
        )
    }

    /** Removes a topic from the library; article cache is kept only for articles other topics use. */
    fun deleteTopic(topic: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("membership", "topic = ?", arrayOf(topic))
            db.delete("topics", "topic = ?", arrayOf(topic))
            db.delete("reading", "topic = ?", arrayOf(topic))
            db.delete("topic_affinity", "topic = ?", arrayOf(topic))
            db.execSQL("DELETE FROM articles WHERE title NOT IN (SELECT title FROM membership)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── seen list ─────────────────────────────────────────────────────────────

    fun markSeen(title: String) {
        if (title.isBlank()) return
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO seen(title, seen_at) VALUES(?, ?)",
            arrayOf<Any?>(title, System.currentTimeMillis())
        )
    }

    /**
     * Keeps the seen list from growing forever. Oldest entries are forgotten first, so an article
     * read years ago may come back instead of the table growing without bound.
     */
    fun pruneSeen(maxEntries: Int = 50_000) {
        writableDatabase.execSQL(
            "DELETE FROM seen WHERE title IN (" +
                    "SELECT title FROM seen ORDER BY seen_at ASC LIMIT MAX(0, " +
                    "(SELECT COUNT(*) FROM seen) - $maxEntries))"
        )
    }

    fun isSeen(title: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM seen WHERE title = ?", arrayOf(title))
            .use { it.moveToFirst() }

    /** User pressed "Start over": forget everything that was shown. */
    fun clearSeen() {
        writableDatabase.execSQL("DELETE FROM seen")
    }

    // ── optional behaviour learning ──────────────────────────────────────────

    /**
     * Records one finished reading session. [seconds] is the time the article was on screen and
     * [progress] the furthest scroll depth (0..1). A quick bounce counts slightly negative, a long
     * read or a like counts positive. The score is bounded, so one odd session cannot dominate.
     */
    fun recordReading(title: String, topic: String, seconds: Double, progress: Double) {
        if (title.isBlank() || topic.isBlank()) return
        val s = seconds.coerceIn(0.0, 900.0)
        val p = progress.coerceIn(0.0, 1.0)
        // 0 = bounced immediately, 1 = read to the end for a minute or more.
        val quality = (minOf(s, 90.0) / 90.0) * 0.65 + p * 0.35
        val delta = if (s < 6.0 && p < 0.08) -0.18 else (quality - 0.30) * 0.20
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR REPLACE INTO reading(title, topic, seconds, progress, read_at) VALUES(?, ?, ?, ?, ?)",
                arrayOf<Any?>(title, topic, s, p, now)
            )
            val current = db.rawQuery(
                "SELECT score, reads FROM topic_affinity WHERE topic = ?", arrayOf(topic)
            ).use { if (it.moveToFirst()) it.getDouble(0) to it.getInt(1) else 0.0 to 0 }
            val next = (current.first + delta).coerceIn(-1.0, 1.0)
            db.execSQL(
                "INSERT OR REPLACE INTO topic_affinity(topic, score, reads, updated_at) VALUES(?, ?, ?, ?)",
                arrayOf<Any?>(topic, next, current.second + 1, now)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Explicit like/dislike also nudges the affinity of every topic containing the article. */
    fun recordVote(title: String, vote: Int) {
        if (vote == 0) return
        val delta = if (vote > 0) 0.15 else -0.20
        for (topic in topicsForTitle(title)) bumpAffinity(topic, delta)
    }

    private fun bumpAffinity(topic: String, delta: Double) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = db.rawQuery(
                "SELECT score, reads FROM topic_affinity WHERE topic = ?", arrayOf(topic)
            ).use { if (it.moveToFirst()) it.getDouble(0) to it.getInt(1) else 0.0 to 0 }
            db.execSQL(
                "INSERT OR REPLACE INTO topic_affinity(topic, score, reads, updated_at) VALUES(?, ?, ?, ?)",
                arrayOf<Any?>(
                    topic, (current.first + delta).coerceIn(-1.0, 1.0),
                    current.second, System.currentTimeMillis()
                )
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** topic -> learned multiplier (0 = avoid, 1 = neutral, 2 = strongly preferred). */
    fun topicAffinity(): Map<String, Double> {
        val out = HashMap<String, Double>()
        readableDatabase.rawQuery("SELECT topic, score FROM topic_affinity", null).use { c ->
            while (c.moveToNext()) {
                out[c.getString(0)] = (1.0 + c.getDouble(1)).coerceIn(0.2, 2.0)
            }
        }
        return out
    }

    /** "Forget what I learned" (also called when the user turns personalization off). */
    fun clearPersonalization() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("reading", null, null)
            db.delete("topic_affinity", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── reading ───────────────────────────────────────────────────────────────

    fun topicStats(): List<TopicStat> {
        val t0 = System.nanoTime()
        val out = ArrayList<TopicStat>()
        readableDatabase.rawQuery(
            "SELECT t.topic, t.resolved, t.article_count, t.built_at, t.complete, COUNT(a.title), " +
                    "COALESCE(SUM(a.img), 0), COALESCE(SUM(a.tier = 1), 0), COALESCE(SUM(a.tier = 2), 0), " +
                    "t.enabled " +
                    "FROM topics t LEFT JOIN membership m ON m.topic = t.topic " +
                    "LEFT JOIN articles a ON a.title = m.title AND a.bytes >= 0 GROUP BY t.topic ORDER BY t.topic",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    TopicStat(
                        topic = c.getString(0), resolved = c.getString(1), discovered = c.getInt(2),
                        builtAt = c.getLong(3), complete = c.getInt(4) == 1, indexed = c.getInt(5),
                        withImages = c.getInt(6), good = c.getInt(7), featured = c.getInt(8),
                        enabled = c.getInt(9) == 1
                    )
                )
            }
        }
        DebugLog.d("TopicIndex", "PERF topicStats(${out.size}) took ${(System.nanoTime() - t0) / 1_000_000} ms")
        return out
    }

    fun libraryStats(): LibraryStats {
        var total = 0
        var unseen = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(title NOT IN (SELECT title FROM seen)), 0) FROM articles " +
                    "WHERE bytes >= 0 AND title IN (SELECT title FROM membership)",
            null
        ).use { if (it.moveToFirst()) { total = it.getInt(0); unseen = it.getInt(1) } }
        val size = try { java.io.File(readableDatabase.path).length() } catch (e: Exception) { 0L }
        return LibraryStats(size, total, unseen, topicStats())
    }

    /** How many unseen articles [topics] still hold, ignoring image/stub filters. */
    fun unseenCount(topics: Collection<String>): Int {
        if (topics.isEmpty()) return 0
        val ph = topics.joinToString(",") { "?" }
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM articles a WHERE a.bytes >= 0 AND a.title NOT IN (SELECT title FROM seen) " +
                    "AND a.title IN (SELECT title FROM membership WHERE topic IN ($ph))",
            topics.toTypedArray()
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * Random sample of never-shown articles from [topics] that pass the filters.
     * [qualityOnly] restricts to Good/Featured articles.
     */
    fun pick(
        topics: Collection<String>,
        imagesOnly: Boolean,
        minBytes: Int,
        qualityOnly: Boolean,
        limit: Int,
        /** Hide articles scoring below this relevance percent (articles without a score always pass). */
        minRel: Int = 0,
        /** Hide articles whose text covers less than this percent of the topic's words. -1 = unscored rows pass. */
        minCov: Int = 0,
        /** Allow already-seen articles (used only when the whole library has been read). */
        includeSeen: Boolean = false
    ): List<ArticleRow> {
        if (topics.isEmpty()) return emptyList()
        // SQLite's variable limit is 999 on older Android; this query binds the list four times.
        // 200 topics is far beyond any real selection and stays safely inside the limit.
        val selected = if (topics.size > 200) {
            DebugLog.w("TopicIndex", "pick() got ${topics.size} topics; using the first 200")
            topics.take(200)
        } else topics
        val ph = selected.joinToString(",") { "?" }
        // Sample first, then look up depth/relevance only for the (few) sampled rows.
        val seenFilter = if (includeSeen) "" else " AND a.title NOT IN (SELECT title FROM seen)"
        val inner = StringBuilder(
            "SELECT a.title AS title, a.bytes AS bytes, a.img AS img, a.tier AS tier, a.views AS views FROM articles a " +
                    "WHERE a.bytes >= 0$seenFilter " +
                    "AND a.title IN (SELECT title FROM membership WHERE topic IN ($ph) " +
                    "AND (rel < 0 OR rel >= $minRel) AND (cov < 0 OR cov >= $minCov))"
        )
        if (imagesOnly) inner.append(" AND a.img = 1")
        if (minBytes > 0) inner.append(" AND a.bytes >= ").append(minBytes)
        if (qualityOnly) inner.append(" AND a.tier > 0")
        inner.append(" ORDER BY RANDOM() LIMIT ").append(limit)
        val sql = "SELECT s.title, s.bytes, s.img, s.tier, s.views, " +
                "(SELECT m.topic FROM membership m JOIN topics t ON t.topic = m.topic " +
                "WHERE m.title = s.title AND t.enabled = 1 ORDER BY m.topic LIMIT 1), " +
                "(SELECT MIN(m.depth) FROM membership m WHERE m.title = s.title AND m.topic IN ($ph)), " +
                "(SELECT MAX(m.rel) FROM membership m WHERE m.title = s.title AND m.topic IN ($ph)), " +
                "(SELECT MAX(m.cov) FROM membership m WHERE m.title = s.title AND m.topic IN ($ph)) " +
                "FROM ($inner) s"
        val t0 = System.nanoTime()
        val out = ArrayList<ArticleRow>()
        readableDatabase.rawQuery(sql, (selected + selected + selected + selected).toTypedArray()).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ArticleRow(
                        c.getString(0), c.getInt(1), c.getInt(2) == 1, c.getInt(3),
                        views = c.getInt(4), topic = c.getString(5) ?: "",
                        depth = c.getInt(6), rel = c.getInt(7), cov = c.getInt(8)
                    )
                )
            }
        }
        DebugLog.d(
            "TopicIndex",
            "PERF pick(topics=${selected.size}, images=$imagesOnly, minBytes=$minBytes, quality=$qualityOnly, " +
                    "limit=$limit, minRel=$minRel, minCov=$minCov, includeSeen=$includeSeen) -> ${out.size} rows in ${(System.nanoTime() - t0) / 1_000_000} ms"
        )
        return out
    }

    companion object {
        private val instances = HashMap<String, TopicIndex>()

        @Synchronized
        fun get(context: Context, lang: String): TopicIndex =
            instances.getOrPut(lang) { TopicIndex(context.applicationContext, lang) }
    }
}

/** Thrown when every article matching the user's topics and filters has already been shown. */
class LibraryExhaustedException(
    /** Unseen articles that exist in the topics but are hidden by the image / stub filters. */
    val unseenIgnoringFilters: Int
) : Exception("library exhausted")
