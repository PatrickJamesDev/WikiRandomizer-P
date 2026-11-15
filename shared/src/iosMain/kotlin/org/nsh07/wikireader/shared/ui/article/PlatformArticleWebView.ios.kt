package org.nsh07.wikireader.shared.ui.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSObject
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView

private class ReaderNavigationDelegate(
    private val scriptProvider: () -> String
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        webView.evaluateJavaScript(scriptProvider(), completionHandler = null)
    }
}

private class ReaderWebViewState(var url: String? = null, var styleScript: String? = null)

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformArticleWebView(
    url: String,
    fontScale: Float,
    lineSpacingPercent: Int,
    horizontalMarginDp: Int,
    justifyText: Boolean,
    sepia: Boolean,
    fontFamily: String,
    modifier: Modifier
) {
    val styleScript = remember(
        lineSpacingPercent,
        horizontalMarginDp,
        justifyText,
        sepia,
        fontFamily
    ) {
        readerStyleScript(
            lineSpacingPercent,
            horizontalMarginDp,
            justifyText,
            sepia,
            fontFamily
        )
    }
    val currentScript = rememberUpdatedState(styleScript)
    val state = remember { ReaderWebViewState() }
    UIKitView(
        factory = {
            WKWebView().apply {
                allowsBackForwardNavigationGestures = true
                pageZoom = fontScale.toDouble()
                navigationDelegate = ReaderNavigationDelegate { currentScript.value }
                state.url = url
                state.styleScript = currentScript.value
                loadRequest(NSURLRequest(uRL = NSURL(string = url)))
            }
        },
        modifier = modifier,
        update = { webView ->
            val script = currentScript.value
            val urlChanged = state.url != url
            webView.pageZoom = fontScale.toDouble()
            if (!urlChanged && state.styleScript != script) {
                webView.evaluateJavaScript(script, completionHandler = null)
            }
            state.url = url
            state.styleScript = script
            if (urlChanged) {
                webView.loadRequest(NSURLRequest(uRL = NSURL(string = url)))
            }
        },
        onRelease = { webView -> webView.stopLoading() }
    )
}
