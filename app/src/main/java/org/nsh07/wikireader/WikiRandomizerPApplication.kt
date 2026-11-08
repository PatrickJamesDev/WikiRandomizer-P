package org.nsh07.wikireader

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
import org.nsh07.wikireader.data.AppContainer
import org.nsh07.wikireader.data.DebugLog
import org.nsh07.wikireader.data.DefaultAppContainer
import org.nsh07.wikireader.ui.image.ImageRetryInterceptor
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
        org.nsh07.wikireader.data.PerfMonitor.init(this)
        DebugLog.init(this)
        enableStrictModeIfDebuggable()
        // After an install/update, keep a copy of the previous data before anything opens the database.
        org.nsh07.wikireader.data.BackupManager.backupIfAppWasUpdated(this)
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
            override fun onActivityResumed(a: Activity) { org.nsh07.wikireader.data.PerfMonitor.setForeground(true); c(a, "resumed") }
            override fun onActivityPaused(a: Activity) { org.nsh07.wikireader.data.PerfMonitor.setForeground(false); c(a, "paused") }
            override fun onActivityStopped(a: Activity) = c(a, "stopped (app in background)")
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) = c(a, "destroyed")
        })
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                DebugLog.crumb("onTrimMemory level=$level ${DebugLog.memory()}")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    runCatching { SingletonImageLoader.get(this@WikiRandomizerPApplication).memoryCache?.clear() }
                }
            }

            override fun onLowMemory() {
                DebugLog.crumb("onLowMemory ${DebugLog.memory()}")
                runCatching { SingletonImageLoader.get(this@WikiRandomizerPApplication).memoryCache?.clear() }
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
        })
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
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
                                chain.proceed(
                                    chain.request()
                                        .newBuilder()
                                        .header(
                                            "User-Agent",
                                            container.userAgentString
                                        )
                                        .build()
                                )
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
}