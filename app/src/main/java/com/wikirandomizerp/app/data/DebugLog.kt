package com.wikirandomizerp.app.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Very detailed, opt-in debug log.
 *
 *  - Toggle in Settings -> "Debug logging". Persisted, so it also covers the next app start.
 *  - Written to `<external files>/logs/wikirandomizerp-debug.log` (rotates at 4 MB, keeps one backup),
 *    viewable / copyable / shareable from Settings -> "View debug log".
 *  - Crashes are ALWAYS recorded (even with logging off) to `last_crash.txt`, because a crash
 *    log is the single most useful thing for fixing "the app crashes when I press X".
 *
 * Every public call is cheap and thread-safe; file writes happen on a background thread.
 */
object DebugLog {
    @Volatile
    var enabled: Boolean = false
        private set

    private const val PREFS = "wikirandomizerp_debug"
    private const val KEY_ENABLED = "enabled"
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    private var appContext: Context? = null

    /**
     * Breadcrumbs: the last actions, kept in memory ALWAYS (even with logging off) and written
     * into every crash report, so a crash report says what the app was doing.
     */
    private data class Crumb(val atMs: Long, val message: String)
    private val crumbs = java.util.ArrayDeque<Crumb>()
    private const val MAX_CRUMBS = 150

    /** Lets the app add its own state (back stack, loading flags, topics...) to crash reports. */
    @Volatile
    var stateProvider: (() -> String)? = null

    @Volatile private var lastCrumbMsg = ""
    @Volatile private var lastMainBeat = System.nanoTime()
    @Volatile private var watchdogStarted = false
    private var logDir: File? = null
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DebugLogWriter").apply { isDaemon = true }
    }
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())
    private val startNanos = System.nanoTime()

    fun init(context: Context) {
        appContext = context.applicationContext
        logDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("logs")
            .also { it.mkdirs() }
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        installCrashHandler()
        Thread({ recordPreviousExit() }, "wr-exit-info").apply { isDaemon = true }.start()
        if (enabled) logSessionHeader()
        if (enabled) { startWatchdog(); PerfMonitor.start() }
    }

    /** The most recent breadcrumb text (what the app was last doing). */
    fun lastCrumb(): String = lastCrumbMsg

    /** Cheap always-on breadcrumb (no disk I/O). */
    fun crumb(msg: String) {
        lastCrumbMsg = msg
        // Formatting every breadcrumb eagerly meant a DateTimeFormatter call on the UI thread for
        // every random pick / page load. The timestamp is only needed if a crash report is written.
        synchronized(crumbs) {
            if (crumbs.size >= MAX_CRUMBS) crumbs.removeFirst()
            crumbs.addLast(Crumb(System.currentTimeMillis(), msg))
        }
        if (enabled) writeLine("D", "Crumb", msg, null)
    }

    private fun crumbText(): String = synchronized(crumbs) {
        crumbs.joinToString("\n") { "${formatter.format(Instant.ofEpochMilli(it.atMs))} ${it.message}" }
    }

    /**
     * Android 11+ remembers WHY the previous process of this app ended (crash, native crash, ANR,
     * killed for low memory, user swipe...). A "crash" with no Java stack trace is usually one of
     * the non-Java reasons, so this is saved to last_exit.txt on every start.
     */
    private fun recordPreviousExit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val am = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val exits = am.getHistoricalProcessExitReasons(null, 0, 6)
            if (exits.isEmpty()) return
            val sb = StringBuilder()
            for (e in exits) {
                sb.append(formatter.format(Instant.ofEpochMilli(e.timestamp))).append("  ")
                    .append(exitReasonName(e.reason)).append("  status=").append(e.status)
                    .append("  importance=").append(e.importance)
                    .append("  pss=").append(e.pss / 1024).append("MB rss=").append(e.rss / 1024).append("MB")
                    .append("  desc=").append(e.description ?: "").append('\n')
                // ANR and native crashes come with a trace; keep the start of it.
                if (e.reason == ApplicationExitInfo.REASON_ANR || e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    try {
                        e.traceInputStream?.use { st ->
                            sb.append(String(st.readBytes().take(12_000).toByteArray())).append('\n')
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            exitFile()?.writeText(sb.toString())
            if (enabled) writeLine("I", "ExitInfo", "previous process exits:\n$sb", null)
        } catch (_: Throwable) {
        }
    }

    private fun exitReasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java exception)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (native crash)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (killed by the system)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED (force-stopped)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (killed by signal)"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN($r)"
    }

    /**
     * Debug-log only: detects the UI thread being blocked (the usual cause of freezes / ANRs)
     * and logs what it is doing at that moment.
     */
    private fun startWatchdog() {
        if (watchdogStarted) return
        watchdogStarted = true
        val main = Handler(Looper.getMainLooper())
        Thread({
            var reported = false
            while (true) {
                try {
                    main.post { lastMainBeat = System.nanoTime() }
                    val before = System.nanoTime()
                    Thread.sleep(1_000)
                    val blockedMs = (System.nanoTime() - lastMainBeat) / 1_000_000
                    // If this thread itself overslept, the whole process was frozen (app in the
                    // background): that is not a blocked UI thread, and used to be logged as one.
                    val oversleptMs = (System.nanoTime() - before) / 1_000_000 - 1_000
                    if (oversleptMs > 1_500) {
                        reported = false
                    } else if (enabled && blockedMs > 3_000) {
                        if (!reported) {
                            reported = true
                            val st = Looper.getMainLooper().thread.stackTrace.joinToString("\n    at ")
                            writeLine("W", "Watchdog", "MAIN THREAD BLOCKED for ${blockedMs} ms; it is currently at:\n    at $st", null)
                        }
                    } else reported = false
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }, "DebugWatchdog").apply { isDaemon = true }.start()
    }

    private fun exitFile(): File? = logDir?.resolve("last_exit.txt")


    fun setEnabled(value: Boolean) {
        val was = enabled
        enabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        if (value && !was) { logSessionHeader(); startWatchdog(); PerfMonitor.start() }
        else if (!value && was) { writeLine("I", "DebugLog", "Debug logging turned OFF", null); PerfMonitor.stop() }
    }

    // ── logging API ────────────────────────────────────────────────────────

    fun d(tag: String, msg: String) { if (enabled) writeLine("D", tag, msg, null) }
    fun i(tag: String, msg: String) { if (enabled) writeLine("I", tag, msg, null) }
    fun w(tag: String, msg: String, t: Throwable? = null) { if (enabled) writeLine("W", tag, msg, t) }
    fun e(tag: String, msg: String, t: Throwable? = null) { if (enabled) writeLine("E", tag, msg, t) }

    /** Times [block] and logs "label took N ms" (also when it throws). */
    inline fun <T> time(tag: String, label: String, block: () -> T): T {
        val t0 = System.nanoTime()
        var ok = false
        try {
            val r = block()
            ok = true
            return r
        } finally {
            if (enabled) {
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                d(tag, "PERF $label took ${"%.1f".format(ms)} ms" + if (ok) "" else " (FAILED)")
            }
        }
    }

    /** Heap / native memory snapshot, e.g. "heap 48/256 MB, native 31 MB". */
    fun memory(): String {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        val maxMb = rt.maxMemory() / 1_048_576
        val nativeMb = Debug.getNativeHeapAllocatedSize() / 1_048_576
        return "heap ${usedMb}/${maxMb} MB, native ${nativeMb} MB"
    }

    // ── viewing ────────────────────────────────────────────────────────────

    fun logFile(): File? = logDir?.resolve("wikirandomizerp-debug.log")
    private fun crashFile(): File? = logDir?.resolve("last_crash.txt")

    fun lastCrash(): String? = try {
        crashFile()?.takeIf { it.exists() }?.readText()
    } catch (e: Exception) { null }

    /** The last [maxBytes] of the log (whole lines), plus the last crash report if any. */
    fun readTail(maxBytes: Int = 200_000): String {
        flush()
        val sb = StringBuilder()
        lastCrash()?.let { sb.append("=== LAST CRASH ===\n").append(it).append("\n=== END CRASH ===\n\n") }
        try {
            exitFile()?.takeIf { it.exists() }?.readText()?.let {
                sb.append("=== WHY THE PREVIOUS RUNS ENDED (newest first) ===\n").append(it).append("=== END ===\n\n")
            }
        } catch (_: Exception) {
        }
        val f = logFile()
        if (f != null && f.exists()) {
            try {
                java.io.RandomAccessFile(f, "r").use { raf ->
                    val len = raf.length()
                    val start = maxOf(0L, len - maxBytes)
                    raf.seek(start)
                    val bytes = ByteArray((len - start).toInt())
                    raf.readFully(bytes)
                    var text = String(bytes, Charsets.UTF_8)
                    if (start > 0) text = text.substringAfter('\n', text)
                    sb.append(text)
                }
            } catch (e: Exception) {
                sb.append("(could not read log: ${e.message})")
            }
        } else sb.append("(log is empty - turn on Debug logging and use the app)")
        return sb.toString()
    }

    /**
     * Writes the report (last crash + exit reasons + log tail) to a timestamped .txt file in the
     * cache dir and returns it, ready to be shared through the FileProvider. Sharing a file avoids
     * the ~1 MB Binder limit that forced the old text-sharing to truncate the log.
     */
    fun exportToFile(maxBytes: Int = 2_000_000): File? {
        val ctx = appContext ?: return null
        return try {
            val dir = File(ctx.cacheDir, "shared_logs").also { it.mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only keep the newest export
            val stamp = java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            File(dir, "wikirandomizerp-debug-$stamp.txt").also { it.writeText(readTail(maxBytes)) }
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        flush()
        logFile()?.delete()
        logDir?.resolve("wikirandomizerp-debug.log.1")?.delete()
        crashFile()?.delete()
        exitFile()?.delete()
    }

    private fun flush() {
        try { writer.submit(Runnable { }).get(2, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) {}
    }

    // ── internals ──────────────────────────────────────────────────────────

    private fun logSessionHeader() {
        val ctx = appContext
        val version = try {
            ctx?.packageManager?.getPackageInfo(ctx.packageName, 0)?.versionName
        } catch (e: Exception) { "?" }
        writeLine("I", "Session", "================ NEW SESSION ================", null)
        writeLine(
            "I", "Session",
            "app=$version device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} " +
                    "sdk=${Build.VERSION.SDK_INT} cpus=${Runtime.getRuntime().availableProcessors()} " +
                    "pid=${Process.myPid()} ${memory()}", null
        )
    }

    private fun writeLine(level: String, tag: String, msg: String, t: Throwable?) {
        val now = Instant.now()
        val uptime = (System.nanoTime() - startNanos) / 1_000_000
        val thread = Thread.currentThread().name
        val line = StringBuilder()
            .append(formatter.format(now)).append(" +").append(uptime).append("ms [")
            .append(thread).append("] ").append(level).append('/').append(tag).append(": ")
            .append(msg)
        if (t != null) line.append('\n').append(stackTrace(t))
        line.append('\n')
        val text = line.toString()
        android.util.Log.println(
            when (level) { "E" -> android.util.Log.ERROR; "W" -> android.util.Log.WARN; else -> android.util.Log.DEBUG },
            "WR/$tag", msg
        )
        writer.execute { append(text) }
    }

    private fun append(text: String) {
        try {
            val f = logFile() ?: return
            if (f.exists() && f.length() > MAX_FILE_BYTES) {
                val backup = f.resolveSibling("wikirandomizerp-debug.log.1")
                backup.delete()
                f.renameTo(backup)
            }
            f.appendText(text)
        } catch (_: Exception) {
        }
    }

    private fun stackTrace(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString().trimEnd()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = StringBuilder()
                    .append("time=").append(formatter.format(Instant.now())).append('\n')
                    .append("thread=").append(thread.name).append('\n')
                    .append(memory()).append('\n')
                    .append(stackTrace(throwable)).append('\n')
                    .append("--- app state ---\n").append(try { stateProvider?.invoke() ?: "(none)" } catch (t: Throwable) { "(state failed: $t)" }).append('\n')
                    .append("--- main thread stack ---\n")
                    .append(Looper.getMainLooper().thread.stackTrace.joinToString("\n    at ", "    at ")).append('\n')
                    .append("--- last actions (oldest first) ---\n").append(crumbText()).append('\n')
                    .toString()
                crashFile()?.writeText(report) // synchronous: the process is about to die
                if (enabled) logFile()?.appendText("!!!!! CRASH !!!!!\n$report\n")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}


/**
 * Catches anything that escapes a background coroutine so it is logged instead of killing the
 * app (a failed database write used to crash the whole process). Use as
 * `viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) { ... }`.
 */
val safeCoroutineHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
    android.util.Log.e("WikiRandomizer-P", "Uncaught exception in a background task (app kept alive)", t)
    DebugLog.e("Coroutine", "UNCAUGHT exception in a background task (app kept alive)", t)
}
