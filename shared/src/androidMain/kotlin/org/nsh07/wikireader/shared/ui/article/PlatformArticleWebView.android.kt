package org.nsh07.wikireader.shared.ui.article

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformArticleWebView(
    url: String,
    fontScale: Float,
    lineSpacingPercent: Int,
    horizontalMarginDp: Int,
    justifyText: Boolean,
    sepia: Boolean,
    modifier: Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.textZoom = (fontScale * 100).toInt().coerceIn(50, 250)
                setBackgroundColor(Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            readerStyleScript(
                                lineSpacingPercent,
                                horizontalMarginDp,
                                justifyText,
                                sepia
                            ),
                            null
                        )
                    }
                }
                tag = url
                loadUrl(url)
            }
        },
        update = { webView ->
            webView.settings.textZoom = (fontScale * 100).toInt().coerceIn(50, 250)
            webView.evaluateJavascript(
                readerStyleScript(lineSpacingPercent, horizontalMarginDp, justifyText, sepia),
                null
            )
            if (webView.tag != url) {
                webView.tag = url
                webView.loadUrl(url)
            }
        },
        onRelease = { webView -> webView.destroy() }
    )
}
