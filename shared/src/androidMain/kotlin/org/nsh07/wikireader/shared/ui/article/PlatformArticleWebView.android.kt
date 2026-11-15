package org.nsh07.wikireader.shared.ui.article

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
    fontFamily: String,
    modifier: Modifier
) {
    val currentScript = rememberUpdatedState(
        readerStyleScript(lineSpacingPercent, horizontalMarginDp, justifyText, sepia, fontFamily)
    )
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
                        view?.evaluateJavascript(currentScript.value, null)
                    }
                }
                tag = url
                loadUrl(url)
            }
        },
        update = { webView ->
            webView.settings.textZoom = (fontScale * 100).toInt().coerceIn(50, 250)
            webView.evaluateJavascript(currentScript.value, null)
            if (webView.tag != url) {
                webView.tag = url
                webView.loadUrl(url)
            }
        },
        onRelease = { webView -> webView.destroy() }
    )
}
