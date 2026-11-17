package com.wikirandomizerp.app

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.app.Activity
import android.os.Bundle
import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import com.wikirandomizerp.app.data.AppContainer
import com.wikirandomizerp.app.data.CacheConfig
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.DefaultAppContainer
import com.wikirandomizerp.app.ui.image.ImageRetryInterceptor
import java.util.concurrent.TimeUnit

class WikiRandomizerPApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer

    /**
     * Debug builds only: report accidental main-thread disk/network work and leaked resources into
     * the app's own debug log (never crashes). Release builds are untouched.
     */
    private fun enableStrictModeIfDebuggable() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (SDK_INT < 28) return
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork().detectCustomSlowCalls()
                .penaltyListener(executor) { v ->
                    DebugLog.w("StrictMode", "main thread: ${v.javaClass.simpleName}\n" + v.stackTrace.take(10).joinToString("\n"))
                }
                .build()
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects().detectLeakedSqlLiteObjects()
                .detectActivityLeaks().detectLeakedRegistrationObjects()
                .penaltyListener(executor) { v ->
                    DebugLog.w("StrictMode", "leak: ${v.javaClass.simpleName}\n" + v.stackTrace.take(10).joinToString("\n"))
                }
                .build()
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        // The article needs a custom text-selection toolbar (Highlight / Search in browser), and
        // Compose's new platform context menu does not expose a public way to add those actions.
        // The legacy TextToolbar path does, so keep it for this app. This must be set before any
        // Compose foundation code is loaded.
        ComposeFoundationFlags.isNewContextMenuEnabled = false
        super.onCreate()
        com.wikirandomizerp.app.data.PerfMonitor.init(this)
        DebugLog.init(this)
        disablePausablePrefetch()
        com.wikirandomizerp.app.data.notifications.DailyArticleNotifier.ensureChannel(this)
        enableStrictModeIfDebuggable()
        // After an install/update, keep a copy of the previous data before anything opens the database.
        com.wikirandomizerp.app.data.BackupManager.backupIfAppWasUpdated(this)
        val t0 = System.nanoTime()
        container = DefaultAppContainer(this)
        DebugLog.d("App", "PERF container created in ${(System.nanoTime() - t0) / 1_000_000} ms")

        // Building the two OkHttp clients (SSL setup, ~100 ms each) and the Coil image loader used to
        // happen on the UI thread during the first frames (visible as startup jank). Do it now, in
        // the background; whoever needs them first simply finds them ready.
        Thread({
            try {
                container.wikipediaRepository
                SingletonImageLoader.get(this)
            } catch (_: Throwable) {
            }
        }, "wr-warmup").apply { isDaemon = true; start() }

        // Lifecycle + memory-pressure breadcrumbs: a "crash" without a Java stack trace is often
        // the system killing a backgrounded app, and these show what happened just before.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private fun c(a: Activity, what: String) = DebugLog.crumb("lifecycle ${a.javaClass.simpleName} $what ${DebugLog.memory()}")
            override fun onActivityCreated(a: Activity, b: Bundle?) = c(a, "created")
            override fun onActivityStarted(a: Activity) = c(a, "started")
            override fun onActivityResumed(a: Activity) { com.wikirandomizerp.app.data.PerfMonitor.setForeground(true); c(a, "resumed") }
            override fun onActivityPaused(a: Activity) { com.wikirandomizerp.app.data.PerfMonitor.setForeground(false); c(a, "paused") }
            override fun onActivityStopped(a: Activity) = c(a, "stopped (app in background)")
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) = c(a, "destroyed")
        })
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                DebugLog.crumb("onTrimMemory level=$level ${DebugLog.memory()}")
                // Do not throw the whole image cache away at RUNNING_LOW: the visible article then
                // re-decodes every picture, which caused a wave of jank right after the trim.
                // Trimming to half keeps the pictures on screen while still releasing memory;
                // only a complete background trim clears everything.
                runCatching {
                    val cache = SingletonImageLoader.get(this@WikiRandomizerPApplication).memoryCache
                        ?: return@runCatching
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) cache.clear()
                    else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                        cache.trimToSize(cache.maxSize / 2)
                    }
                }
            }

            override fun onLowMemory() {
                DebugLog.crumb("onLowMemory ${DebugLog.memory()}")
                runCatching { SingletonImageLoader.get(this@WikiRandomizerPApplication).memoryCache?.clear() }
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
        })
    }

    private fun isLowRamDevice(): Boolean =
        (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            // A dedicated, persistent image cache in filesDir (the default lives in cacheDir and
            // can be cleared by the system at any time). Article pictures therefore survive app
            // restarts and reopen instantly; the retention policy removes old body images
            // explicitly, and pinned History/Saved thumbnails are never touched.
            // Each on-device cache gets the same 600 MB budget by default.
            .diskCache(
                coil3.disk.DiskCache.Builder()
                    .directory(java.io.File(filesDir, "image_cache").toOkioPath())
                    .maxSizeBytes(CacheConfig.DISK_CACHE_BYTES)
                    .build()
            )
            .memoryCache(
                coil3.memory.MemoryCache.Builder()
                    // Cap image memory adaptively: 25% of a 512 MB heap is 128 MB, which the app
                    // reaches quickly on article-heavy pages and then fights with GC. 20% keeps
                    // enough decoded pictures for smooth scrolling without that pressure.
                    .maxSizePercent(this, if (isLowRamDevice()) 0.12 else 0.20)
                    .build()
            )
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        OkHttpClient.Builder()
                            // Default is 5 simultaneous requests per host; articles have many pictures.
                            .dispatcher(Dispatcher().apply {
                                maxRequests = 64
                                maxRequestsPerHost = 10
                            })
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .callTimeout(60, TimeUnit.SECONDS)
                            .addNetworkInterceptor { chain ->
                                val t0 = System.nanoTime()
                                val request = chain.request()
                                var response = chain.proceed(
                                    request.newBuilder()
                                        .header("User-Agent", container.userAgentString)
                                        .build()
                                )
                                val host = response.request.url.host
                                // A thumbnail URL on upload.wikimedia.org never changes its content.
                                // Coil honours Cache-Control for its disk cache, so a short or missing
                                // max-age made every picture that was prefetched a few minutes ago
                                // re-contact the server before it could be shown.
                                if (response.isSuccessful && host == "upload.wikimedia.org") {
                                    response = response.newBuilder()
                                        .removeHeader("Pragma")
                                        .removeHeader("Expires")
                                        .header("Cache-Control", "public, max-age=31536000, immutable")
                                        .build()
                                }
                                if (DebugLog.enabled) {
                                    val ms = (System.nanoTime() - t0) / 1_000_000
                                    val line = "IMG ${response.code} ${response.protocol} ${ms}ms " +
                                        "bytes=${response.header("Content-Length") ?: "?"} " +
                                        "${request.url} -> ${response.request.url}"
                                    if (ms >= 800) DebugLog.w("Image", "SLOW $line") else DebugLog.d("Image", line)
                                }
                                response
                            }
                            .build()
                    )
                )
                add(ImageRetryInterceptor())
                add(SvgDecoder.Factory(scaleToDensity = true))
                if (SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    /**
     * Compose's lazy-list prefetch can resume a paused composition and crash with "Cannot disable
     * reuse from root if it was caused by other groups" (seen while scrolling a long article).
     * Switching the pausable prefetch off keeps the lists scrolling normally. Done by reflection so
     * it keeps compiling whichever Compose build provides the flag; if it is missing nothing happens.
     */
    private fun disablePausablePrefetch() {
        try {
            val cls = Class.forName("androidx.compose.foundation.ComposeFoundationFlags")
            val instance = cls.getField("INSTANCE").get(null)
            val field = runCatching { cls.getField("isPausableCompositionInPrefetchEnabled") }.getOrNull()
            if (field != null) {
                field.setBoolean(instance, false)
            } else {
                cls.getMethod("setPausableCompositionInPrefetchEnabled", java.lang.Boolean.TYPE)
                    .invoke(instance, false)
            }
            com.wikirandomizerp.app.data.DebugLog.d("App", "pausable lazy prefetch disabled")
        } catch (e: Throwable) {
            com.wikirandomizerp.app.data.DebugLog.d("App", "pausable prefetch flag not available: ${e.javaClass.simpleName}")
        }
    }
}
