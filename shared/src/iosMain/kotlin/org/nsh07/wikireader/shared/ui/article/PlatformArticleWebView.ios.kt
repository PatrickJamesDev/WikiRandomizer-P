package org.nsh07.wikireader.shared.ui.article

import androidx.compose.runtime.Composable
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
    val styleScript = readerStyleScript(
        lineSpacingPercent,
        horizontalMarginDp,
        justifyText,
        sepia,
        fontFamily
    )
    UIKitView(
        factory = {
            WKWebView().apply {
                allowsBackForwardNavigationGestures = true
                pageZoom = fontScale.toDouble()
                navigationDelegate = ReaderNavigationDelegate { styleScript }
                loadRequest(NSURLRequest(uRL = NSURL(string = url)))
            }
        },
        modifier = modifier,
        update = { webView ->
            webView.pageZoom = fontScale.toDouble()
            webView.navigationDelegate = ReaderNavigationDelegate { styleScript }
            webView.evaluateJavaScript(styleScript, completionHandler = null)
            if (webView.URL?.absoluteString != url) {
                webView.loadRequest(NSURLRequest(uRL = NSURL(string = url)))
            }
        },
        onRelease = { webView -> webView.stopLoading() }
    )
}
