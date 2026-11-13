package com.wikirandomizerp.app.data

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Printer
import android.view.Display
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import coil3.SingletonImageLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fine-grained performance diagnostics, active ONLY while the debug log is switched on.
 * Everything is written to the debug log, so one shared log is enough to see where lag comes from:
 *
 *  - `ENV`     one-off description of the device/build: debuggable?, refresh rate, battery saver,
 *              thermal state, animation scales, ART compilation status of the app, memory class.
 *  - `JANK`    every frame slower than [JANK_MS]: the time split into the phases Android measures
 *              (waiting for the UI thread, input, animation/RECOMPOSITION, layout/measure, draw, sync,
 *              GPU...) PLUS what the UI thread was actually executing during that frame (stack samples
 *              every [SAMPLE_MS] ms, grouped into regions like "text layout" / "database" / "coil",
 *              the app functions on the stack and the busiest call chain).
 *  - `SLOWMSG` any single UI-thread message that took 20 ms or more (what ran and for how long).
 *  - `VITALS`  every 2 s: frames per second and frame-time histogram, CPU use of the process / UI
 *              thread / RenderThread / the busiest background threads, GC count and pause time, heap,
 *              CPU frequencies, thermal status, whether the finger is on the screen or scrolling.
 *
 * Sampling the UI thread costs a little CPU (a few %), which is why it is tied to the debug log.
 */
object PerfMonitor {
    private const val TAG = "Perf"
    private const val SAMPLE_MS = 6L
    private const val RING = 700
    private const val JANK_MS = 20.0
    private const val SLOWMSG_MS = 20L
    private const val MAX_JANK_LINES_PER_SECOND = 8
    private const val VITALS_MS = 2_000L

    @Volatile private var running = false
    @Volatile private var foreground = false
    @Volatile private var scrolling = false
    @Volatile private var touchDown = false
    @Volatile private var lastTouchNs = 0L

    private var appContext: Context? = null
    private var activityRef: WeakReference<Activity>? = null
    private var frameThread: HandlerThread? = null
    private var attachedWindow: Window? = null
    private var frameListener: Window.OnFrameMetricsAvailableListener? = null
    private var sampler: Thread? = null
    private var vitalsThread: Thread? = null

    // ring buffer of UI-thread stack samples
    private val ringLock = Any()
    private val ringTime = LongArray(RING)
    private val ringStack = arrayOfNulls<Array<StackTraceElement>>(RING)
    private var ringCount = 0L

    // frame statistics of the current VITALS interval
    private val statLock = Any()
    private var stFrames = 0
    private var st16 = 0
    private var st33 = 0
    private var st100 = 0
    private var stWorst = 0.0
    private var stSum = 0.0
    private var stMissedCallbacks = 0L
    private var jankWindowStart = 0L
    private var jankInWindow = 0
    private var jankSuppressed = 0

    // slow-message tracking (UI thread only)
    private var msgStart = 0L
    private var msgDesc = ""

    private val recompCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val recompDetails = ConcurrentHashMap<String, String>()
    private var measureWindowStart = 0L
    private var measureInWindow = 0

    /** Set by the article screen: describes which list items are on screen (read when a slow frame is reported). */
    @Volatile var listInfo: (() -> String)? = null

    fun recomposed(name: String) {
        recompCounts.getOrPut(name) { AtomicInteger() }.incrementAndGet()
    }

    fun recomposed(name: String, detail: String) {
        recompCounts.getOrPut(name) { AtomicInteger() }.incrementAndGet()
        if (detail.isNotEmpty()) recompDetails[name] = detail else recompDetails.remove(name)
    }

    fun slowMeasure(name: String, ms: Double, w: Int, h: Int) {
        if (!running || !foreground) return
        val now = System.nanoTime()
        if (now - measureWindowStart > 1_000_000_000L) {
            measureWindowStart = now
            measureInWindow = 0
        }
        if (++measureInWindow > 10) return
        DebugLog.w(TAG, "SLOWMEASURE $name took ${f(ms)} ms to measure+layout (result ${w}x${h}px) ${listInfoText()}")
    }

    private fun listInfoText(): String = try {
        listInfo?.invoke() ?: ""
    } catch (_: Throwable) {
        ""
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── hooks called from the app ─────────────────────────────────────────

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
        if (running) attachFrameMetrics(activity)
        else if (DebugLog.enabled) start()
    }

    fun setForeground(value: Boolean) {
        foreground = value
    }

    fun setScrolling(value: Boolean) {
        scrolling = value
    }

    fun onTouch(ev: MotionEvent) {
        lastTouchNs = System.nanoTime()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchDown = false
        }
    }

    /** Short description of what the user is doing, appended to JANK lines. */
    private fun context(): String {
        val touch = when {
            touchDown -> "finger down"
            lastTouchNs == 0L -> "no touch yet"
            else -> "last touch ${(System.nanoTime() - lastTouchNs) / 1_000_000} ms ago"
        }
        return "[$touch${if (scrolling) ", list scrolling" else ""}${if (!foreground) ", BACKGROUND" else ""}] last action: ${DebugLog.lastCrumb()}"
    }

    // ── lifecycle ──────────────────────────────────────────────────────────

    @Synchronized
    fun start() {
        if (running) return
        running = true
        DebugLog.i(TAG, "performance monitor started (UI-thread sampling every $SAMPLE_MS ms; JANK >= ${JANK_MS.toInt()} ms)")
        startSampler()
        startVitals()
        startMessageLogging()
        activityRef?.get()?.let { attachFrameMetrics(it) }
        Thread({ logEnvironment() }, "wr-perf-env").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        sampler?.interrupt()
        vitalsThread?.interrupt()
        sampler = null
        vitalsThread = null
        try {
            Looper.getMainLooper().setMessageLogging(null)
        } catch (_: Throwable) {
        }
        detachFrameMetrics()
    }

    // ── frame metrics (per-frame phase breakdown) ──────────────────────────

    private fun attachFrameMetrics(activity: Activity) {
        Handler(Looper.getMainLooper()).post {
            try {
                detachFrameMetrics()
                val win = activity.window ?: return@post
                val thread = HandlerThread("wr-perf-frames").also { it.start() }
                val listener = Window.OnFrameMetricsAvailableListener { _, fm, dropped ->
                    try {
                        onFrame(fm, dropped)
                    } catch (_: Throwable) {
                    }
                }
                frameThread = thread
                frameListener = listener
                attachedWindow = win
                win.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
            } catch (t: Throwable) {
                DebugLog.w(TAG, "frame metrics unavailable: $t")
            }
        }
    }

    private fun detachFrameMetrics() {
        try {
            val l = frameListener
            if (l != null) attachedWindow?.removeOnFrameMetricsAvailableListener(l)
        } catch (_: Throwable) {
        }
        frameListener = null
        attachedWindow = null
        frameThread?.quitSafely()
        frameThread = null
    }

    private fun ms(fm: FrameMetrics, id: Int): Double = fm.getMetric(id) / 1_000_000.0

    private fun onFrame(fm: FrameMetrics, droppedCallbacks: Int) {
        if (!foreground) return
        val end = System.nanoTime()
        val total = ms(fm, FrameMetrics.TOTAL_DURATION)
        synchronized(statLock) {
            stFrames++
            stSum += total
            if (total > stWorst) stWorst = total
            if (total > 16.7) st16++
            if (total > 33.4) st33++
            if (total > 100.0) st100++
            stMissedCallbacks += droppedCallbacks
        }
        if (total < JANK_MS) return

        // rate limit: a long scroll can produce hundreds of slow frames
        val now = end
        if (now - jankWindowStart > 1_000_000_000L) {
            if (jankSuppressed > 0) DebugLog.w(TAG, "JANK: $jankSuppressed more slow frames in the last second not listed")
            jankWindowStart = now
            jankInWindow = 0
            jankSuppressed = 0
        }
        if (++jankInWindow > MAX_JANK_LINES_PER_SECOND) {
            jankSuppressed++
            return
        }

        val delay = ms(fm, FrameMetrics.UNKNOWN_DELAY_DURATION)
        val input = ms(fm, FrameMetrics.INPUT_HANDLING_DURATION)
        val anim = ms(fm, FrameMetrics.ANIMATION_DURATION)
        val layout = ms(fm, FrameMetrics.LAYOUT_MEASURE_DURATION)
        val draw = ms(fm, FrameMetrics.DRAW_DURATION)
        val sync = ms(fm, FrameMetrics.SYNC_DURATION)
        val cmd = ms(fm, FrameMetrics.COMMAND_ISSUE_DURATION)
        val swap = ms(fm, FrameMetrics.SWAP_BUFFERS_DURATION)
        val gpu = if (Build.VERSION.SDK_INT >= 31) ms(fm, FrameMetrics.GPU_DURATION) else -1.0
        val first = fm.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L

        val parts = listOf(
            "waited-for-UI-thread" to delay, "input" to input, "anim+RECOMPOSE" to anim,
            "layout+measure" to layout, "draw" to draw, "sync" to sync, "render-cmds" to cmd, "swap" to swap
        )
        val worst = parts.maxByOrNull { it.second }!!
        val verdict = if (gpu > worst.second && gpu > 8.0) "GPU-bound" else "mostly ${worst.first}"

        val sb = StringBuilder()
        sb.append("JANK ").append(f(total)).append(" ms").append(if (first) " (first frame)" else "")
            .append(" -> ").append(verdict)
            .append(" | wait ").append(f(delay)).append(" input ").append(f(input))
            .append(" anim/recompose ").append(f(anim)).append(" layout ").append(f(layout))
            .append(" draw ").append(f(draw)).append(" sync ").append(f(sync))
            .append(" cmds ").append(f(cmd)).append(" swap ").append(f(swap))
        if (gpu >= 0) sb.append(" gpu ").append(f(gpu))
        sb.append(" | ").append(context())
        listInfoText().let { if (it.isNotEmpty()) sb.append(" | ").append(it) }
        if (droppedCallbacks > 0) sb.append(" | $droppedCallbacks frame reports lost")
        sb.append('\n').append(hotSpots(end - (total * 1_000_000).toLong() - 30_000_000L, end, full = total >= 50.0))
        DebugLog.w(TAG, sb.toString())
    }

    private fun f(x: Double) = String.format(Locale.US, "%.1f", x)

    // ── UI-thread stack sampler ────────────────────────────────────────────

    private fun startSampler() {
        val main = Looper.getMainLooper().thread
        sampler = Thread({
            while (running) {
                try {
                    Thread.sleep(SAMPLE_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (!foreground) continue
                val st = try {
                    main.stackTrace
                } catch (_: Throwable) {
                    continue
                }
                if (st.isEmpty()) continue
                val t = System.nanoTime()
                synchronized(ringLock) {
                    val i = (ringCount % RING).toInt()
                    ringTime[i] = t
                    ringStack[i] = st
                    ringCount++
                }
            }
        }, "wr-perf-sampler").apply { isDaemon = true; start() }
    }

    private fun isIdle(st: Array<StackTraceElement>): Boolean =
        st[0].methodName == "nativePollOnce" || (st.size > 1 && st[1].className == "android.os.MessageQueue" && st[1].methodName == "next")

    private fun frameText(e: StackTraceElement) = "${e.className.substringAfterLast('.')}.${e.methodName}:${e.lineNumber}"

    private fun region(st: Array<StackTraceElement>): String {
        // strongest signal first: the first rule that matches ANY frame wins
        val rules = listOf(
            "font loading" to listOf("androidx.compose.ui.text.font", "android.graphics.Typeface"),
            "text layout" to listOf("androidx.compose.ui.text", "android.text.", "android.graphics.text", "MeasuredParagraph", "StaticLayout"),
            "image decode" to listOf("android.graphics.BitmapFactory", "coil3.decode", "coil3.util"),
            "image loading (coil)" to listOf("coil3."),
            "database" to listOf("androidx.room", "android.database", "androidx.sqlite"),
            "regex" to listOf("java.util.regex"),
            "JSON" to listOf("kotlinx.serialization"),
            "lazy list" to listOf("androidx.compose.foundation.lazy"),
            "compose runtime" to listOf("androidx.compose.runtime"),
            "compose layout/measure" to listOf("androidx.compose.ui.node", "androidx.compose.ui.layout"),
            "compose draw/graphics" to listOf("androidx.compose.ui.graphics", "androidx.compose.ui.draw"),
            "navigation/saveable" to listOf("androidx.navigation", "androidx.savedstate", "androidx.lifecycle"),
            "Android view system" to listOf("android.view.", "android.app."),
            "class loading / JIT" to listOf("java.lang.ClassLoader", "dalvik.system")
        )
        for ((name, keys) in rules) {
            for (e in st) {
                val c = e.className
                if (keys.any { c.startsWith(it) || c.contains(it) }) return name
            }
        }
        return "other"
    }

    private fun hotSpots(fromNs: Long, toNs: Long, full: Boolean): String {
        val picked = ArrayList<Array<StackTraceElement>>()
        synchronized(ringLock) {
            val n = minOf(ringCount, RING.toLong()).toInt()
            for (k in 0 until n) {
                val t = ringTime[k]
                if (t in fromNs..toNs) ringStack[k]?.let { picked.add(it) }
            }
        }
        if (picked.isEmpty()) return "  UI thread: no stack samples in this frame's time window"
        var idle = 0
        val regions = HashMap<String, Int>()
        val appFrames = HashMap<String, Int>()
        val chains = HashMap<String, Int>()
        val chainSample = HashMap<String, Array<StackTraceElement>>()
        for (st in picked) {
            if (isIdle(st)) {
                idle++
                continue
            }
            regions.merge(region(st), 1, Int::plus)
            st.firstOrNull { it.className.startsWith("com.wikirandomizerp.app") }?.let { appFrames.merge(frameText(it), 1, Int::plus) }
            val key = st.take(6).joinToString(" < ") { frameText(it) }
            chains.merge(key, 1, Int::plus)
            chainSample.putIfAbsent(key, st)
        }
        val busy = picked.size - idle
        val sb = StringBuilder()
        sb.append("  UI thread: ").append(busy).append(" busy / ").append(picked.size).append(" samples (")
            .append(idle).append(" idle)")
        if (busy == 0) return sb.append(" -> UI thread was idle: the delay is outside app code (GPU, render thread, CPU starved or throttled)").toString()
        sb.append("\n  where: ").append(regions.entries.sortedByDescending { it.value }.take(4).joinToString(", ") { "${it.key} ${it.value}x" })
        if (appFrames.isNotEmpty())
            sb.append("\n  app code: ").append(appFrames.entries.sortedByDescending { it.value }.take(3).joinToString(" | ") { "${it.value}x ${it.key}" })
        val top = chains.entries.sortedByDescending { it.value }.take(3)
        for (e in top) sb.append("\n  ").append(e.value).append("x ").append(e.key)
        if (full) {
            val st = chainSample[top.first().key]
            if (st != null) {
                sb.append("\n  full stack of the busiest chain:")
                for (e in st.take(32)) sb.append("\n      at ").append(e.className).append('.').append(e.methodName).append('(').append(e.fileName).append(':').append(e.lineNumber).append(')')
            }
        }
        return sb.toString()
    }

    // ── every UI-thread message: find the slow ones ────────────────────────

    private fun startMessageLogging() {
        try {
            Looper.getMainLooper().setMessageLogging(Printer { line ->
                if (line.startsWith(">>>>>")) {
                    msgStart = System.nanoTime()
                    msgDesc = line
                } else if (line.startsWith("<<<<<") && msgStart != 0L) {
                    val d = (System.nanoTime() - msgStart) / 1_000_000
                    msgStart = 0L
                    if (d >= SLOWMSG_MS && foreground) {
                        DebugLog.w(TAG, "SLOWMSG UI thread busy for $d ms in one message: ${msgDesc.removePrefix(">>>>> Dispatching to ").take(200)}")
                    }
                }
            })
        } catch (t: Throwable) {
            DebugLog.w(TAG, "message logging unavailable: $t")
        }
    }

    // ── VITALS every 2 s ───────────────────────────────────────────────────

    private class ThreadTicks(val perName: Map<String, Long>, val main: Long, val render: Long, val count: Int)

    private val trailingDigits = Regex("[-_ ]?\\d+$")
    private var freqReadable = true

    private fun readThreadTicks(): ThreadTicks? {
        val dir = File("/proc/self/task")
        val ids = dir.list() ?: return null
        val per = HashMap<String, Long>()
        var main = 0L
        var render = 0L
        val mainId = Process.myPid().toString()
        for (id in ids) {
            val s = try {
                File(dir, "$id/stat").readText()
            } catch (_: Throwable) {
                continue
            }
            val l = s.indexOf('(')
            val r = s.lastIndexOf(')')
            if (l < 0 || r < l) continue
            val comm = s.substring(l + 1, r)
            val rest = s.substring(r + 2).split(' ')
            val ticks = (rest.getOrNull(11)?.toLongOrNull() ?: 0L) + (rest.getOrNull(12)?.toLongOrNull() ?: 0L)
            per.merge(comm.replace(trailingDigits, ""), ticks, Long::plus)
            if (id == mainId) main = ticks
            if (comm == "RenderThread") render = ticks
        }
        return ThreadTicks(per, main, render, ids.size)
    }

    private fun gcStat(key: String): Long = try {
        Debug.getRuntimeStat(key).toLong()
    } catch (_: Throwable) {
        0L
    }

    private var vitalsRound = 0

    /** More expensive facts, only every 10 s: memory breakdown, image caches, system load. */
    private fun slowExtras(): String {
        val sb = StringBuilder("EXTRAS")
        try {
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi)
            fun kb(k: String) = (mi.getMemoryStat(k)?.toLongOrNull() ?: 0L) / 1024
            sb.append(" PSS total ${kb("summary.total-pss")} MB (java ${kb("summary.java-heap")}, native ${kb("summary.native-heap")}, graphics ${kb("summary.graphics")}, code ${kb("summary.code")})")
        } catch (_: Throwable) {
        }
        try {
            val ctx = appContext
            if (ctx != null) {
                val loader = SingletonImageLoader.get(ctx)
                loader.memoryCache?.let { sb.append(" | image memory cache ${it.size / 1048576}/${it.maxSize / 1048576} MB") }
                loader.diskCache?.let { sb.append(" | image disk cache ${it.size / 1048576}/${it.maxSize / 1048576} MB") }
            }
        } catch (_: Throwable) {
        }
        try {
            sb.append(" | load avg ").append(File("/proc/loadavg").readText().split(' ').take(3).joinToString(" "))
        } catch (_: Throwable) {
        }
        return sb.toString()
    }

    private fun startVitals() {
        vitalsThread = Thread({
            var prevTicks = readThreadTicks()
            var prevNs = System.nanoTime()
            var prevGc = gcStat("art.gc.gc-count")
            var prevGcTime = gcStat("art.gc.gc-time")
            var prevBlocking = gcStat("art.gc.blocking-gc-count")
            while (running) {
                try {
                    Thread.sleep(VITALS_MS)
                } catch (_: InterruptedException) {
                    break
                }
                val nowTicks = readThreadTicks()
                val nowNs = System.nanoTime()
                val gc = gcStat("art.gc.gc-count")
                val gcTime = gcStat("art.gc.gc-time")
                val blocking = gcStat("art.gc.blocking-gc-count")
                val elapsedMs = ((nowNs - prevNs) / 1_000_000).coerceAtLeast(1)
                val pt = prevTicks
                if (foreground && pt != null && nowTicks != null) {
                    // 1 tick = 10 ms of CPU
                    fun pct(ticks: Long) = (ticks * 10 * 100 / elapsedMs).toInt()
                    val busiest = nowTicks.perName.entries
                        .map { it.key to pct(it.value - (pt.perName[it.key] ?: 0L)) }
                        .filter { it.second >= 3 }
                        .sortedByDescending { it.second }
                        .take(5)
                        .joinToString(", ") { "${it.first} ${it.second}%" }
                    val total = nowTicks.perName.values.sum() - pt.perName.values.sum()
                    val sb = StringBuilder()
                    sb.append("VITALS ").append(elapsedMs / 1000.0).append("s | ")
                    sb.append(frameSummary(elapsedMs)).append(" | ")
                    sb.append("CPU (100% = one core) process ").append(pct(total))
                        .append("% UI-thread ").append(pct(nowTicks.main - pt.main))
                        .append("% render-thread ").append(pct(nowTicks.render - pt.render))
                        .append("% | busiest: ").append(if (busiest.isEmpty()) "-" else busiest)
                    sb.append(" | threads ").append(nowTicks.count)
                    sb.append(" | GC +").append(gc - prevGc).append(" (blocking +").append(blocking - prevBlocking)
                        .append(", ").append(gcTime - prevGcTime).append(" ms)")
                    sb.append(" | ").append(DebugLog.memory())
                    sb.append(" | ").append(cpuFreqs()).append(thermal())
                    val recomp = recompCounts.entries.map { it.key to it.value.getAndSet(0) }
                        .filter { it.second > 0 }.sortedByDescending { it.second }.take(8)
                    if (recomp.isNotEmpty()) {
                        sb.append(" | recomposed: ").append(recomp.joinToString(", ") {
                            val detail = recompDetails[it.first]
                            if (detail.isNullOrEmpty()) "${it.first} ${it.second}"
                            else "${it.first} ${it.second} {$detail}"
                        })
                    }
                    if (++vitalsRound % 5 == 0) sb.append(" | ").append(slowExtras())
                    sb.append(" | ").append(context())
                    DebugLog.i(TAG, sb.toString())
                } else {
                    resetFrameStats()
                }
                prevTicks = nowTicks
                prevNs = nowNs
                prevGc = gc
                prevGcTime = gcTime
                prevBlocking = blocking
            }
        }, "wr-perf-vitals").apply { isDaemon = true; start() }
    }

    private fun resetFrameStats() {
        synchronized(statLock) {
            stFrames = 0; st16 = 0; st33 = 0; st100 = 0; stWorst = 0.0; stSum = 0.0; stMissedCallbacks = 0
        }
    }

    private fun frameSummary(elapsedMs: Long): String {
        val s: String
        synchronized(statLock) {
            s = if (stFrames == 0) "frames: none drawn (screen static)"
            else "frames ${stFrames} (~${(stFrames * 1000L / elapsedMs)} fps) avg ${f(stSum / stFrames)} ms worst ${f(stWorst)} ms | slower than 16.7ms: $st16, 33ms: $st33, 100ms: $st100" +
                    if (stMissedCallbacks > 0) " | $stMissedCallbacks frame reports lost" else ""
        }
        resetFrameStats()
        return s
    }

    private fun cpuFreqs(): String {
        if (!freqReadable) return ""
        val n = Runtime.getRuntime().availableProcessors()
        val sb = StringBuilder("CPU MHz [")
        for (i in 0 until n) {
            val v = try {
                File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLong() / 1000
            } catch (_: Throwable) {
                freqReadable = false
                return ""
            }
            if (i > 0) sb.append(' ')
            sb.append(v)
        }
        return sb.append("]").toString()
    }

    private fun thermal(): String {
        val ctx = appContext ?: return ""
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return ""
        val sb = StringBuilder()
        if (Build.VERSION.SDK_INT >= 29) sb.append(" thermal=").append(pm.currentThermalStatus) // 0 none .. 3 severe
        if (pm.isPowerSaveMode) sb.append(" BATTERY-SAVER-ON")
        return sb.toString()
    }

    // ── one-off environment description ────────────────────────────────────

    private fun logEnvironment() {
        val ctx = appContext ?: return
        val sb = StringBuilder("ENV\n")
        try {
            val debuggable = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            sb.append("  app: ").append(pi.versionName).append(" | DEBUGGABLE=").append(debuggable)
            if (debuggable) sb.append("  <-- debuggable builds run much slower (no ahead-of-time compilation); use the release/optimised build for judging smoothness")
            sb.append('\n')
        } catch (_: Throwable) {
        }
        sb.append("  device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) android ${Build.VERSION.RELEASE} sdk ${Build.VERSION.SDK_INT}, ${Runtime.getRuntime().availableProcessors()} cpus, ABI ${Build.SUPPORTED_ABIS.firstOrNull()}\n")
        try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
            val mode = d.mode
            sb.append("  display: ${mode.physicalWidth}x${mode.physicalHeight} @ ${f(d.refreshRate.toDouble())} Hz now; modes: ")
            sb.append(d.supportedModes.joinToString(", ") { "${it.physicalWidth}x${it.physicalHeight}@${f(it.refreshRate.toDouble())}" })
            sb.append("; density ${ctx.resources.displayMetrics.densityDpi} dpi\n")
        } catch (t: Throwable) {
            sb.append("  display: unavailable ($t)\n")
        }
        try {
            val cr = ctx.contentResolver
            sb.append("  animation scales: animator ${Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)}, ")
                .append("transition ${Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)}, ")
                .append("window ${Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)}\n")
        } catch (_: Throwable) {
        }
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            sb.append("  memory: class ${am.memoryClass} MB (large ${am.largeMemoryClass} MB), device ${mi.totalMem / 1048576} MB total / ${mi.availMem / 1048576} MB free, lowRam=${am.isLowRamDevice}, lowMemoryNow=${mi.lowMemory}\n")
        } catch (_: Throwable) {
        }
        sb.append("  power:${thermal().ifEmpty { " normal" }}\n")
        sb.append("  ART: ").append(compilationStatus()).append('\n')
        DebugLog.i(TAG, sb.toString().trimEnd())
    }

    /**
     * Whether Android compiled the app ahead of time. After every (re)install the app runs
     * interpreted/JIT until this happens, which feels laggy for a long time. Via reflection so the
     * app never depends on this API existing.
     */
    private fun compilationStatus(): String = try {
        val cls = Class.forName("androidx.profileinstaller.ProfileVerifier")
        val fut = cls.getMethod("getCompilationStatusAsync").invoke(null)!!
        val st = fut.javaClass.getMethod("get", java.lang.Long.TYPE, TimeUnit::class.java).invoke(fut, 5L, TimeUnit.SECONDS)!!
        fun m(name: String) = try {
            st.javaClass.getMethod(name).invoke(st).toString()
        } catch (_: Throwable) {
            "?"
        }
        "profile result code ${m("getProfileInstallResultCode")} (1 = compiled with profile, 2 = profile waiting to be compiled, 0 = no profile, 3 = compiled with a non-matching profile, negative = error); " +
                "compiledWithProfile=${m("isCompiledWithProfile")} hasReferenceProfile=${m("hasReferenceProfile")} hasCurrentProfile=${m("hasCurrentProfile")}"
    } catch (t: Throwable) {
        "status unavailable (${t.javaClass.simpleName})"
    }
}
