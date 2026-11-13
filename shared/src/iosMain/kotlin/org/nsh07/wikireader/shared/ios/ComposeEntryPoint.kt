package org.nsh07.wikireader.shared.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.nsh07.wikireader.shared.settings.IosSettingsStore
import org.nsh07.wikireader.shared.ui.ReaderApp
import platform.UIKit.UIViewController

private val imageHttpClient by lazy { HttpClient(Darwin) }

@Composable
internal fun ComposeReaderRoot() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { imageHttpClient }))
            }
            .crossfade(true)
            .build()
    }

    val source = remember { KtorRandomArticleSource() }
    val searchSource = remember { KtorWikipediaSearchSource() }
    val settingsStore = remember { IosSettingsStore() }
    ReaderApp(source = source, searchSource = searchSource, settingsStore = settingsStore)
}

fun ReaderViewController(): UIViewController = ComposeUIViewController {
    ComposeReaderRoot()
}

/** Stable Obj-C entry point for the SwiftUI host; avoids relying on Kotlin file-class naming. */
class ReaderViewControllerFactory {
    fun makeReaderViewController(): UIViewController = ReaderViewController()
}
