package org.nsh07.wikireader.shared.ui.article

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformArticleWebView(
    url: String,
    fontScale: Float,
    modifier: Modifier
) {
    UIKitView(
        factory = {
            WKWebView().apply {
                allowsBackForwardNavigationGestures = true
                pageZoom = fontScale.toDouble()
                loadRequest(NSURLRequest(uRL = NSURL(string = url)))
            }
        },
        modifier = modifier,
        update = { webView ->
            webView.pageZoom = fontScale.toDouble()
            if (webView.URL?.absoluteString != url) {
                webView.loadRequest(NSURLRequest(uRL = NSURL(string = url)))
            }
        },
        onRelease = { webView -> webView.stopLoading() }
    )
}
