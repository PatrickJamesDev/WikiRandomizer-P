package org.nsh07.wikireader.shared.ui.article

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private data class ArticleWebViewState(val url: String, val styleScript: String)

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
                tag = ArticleWebViewState(url, currentScript.value)
                loadUrl(url)
            }
        },
        update = { webView ->
            val script = currentScript.value
            val state = webView.tag as? ArticleWebViewState
            val urlChanged = state?.url != url
            webView.settings.textZoom = (fontScale * 100).toInt().coerceIn(50, 250)
            // Updating Compose state does not need another JavaScript round trip. Apply the style
            // only when a reading option changed; a new page receives it in onPageFinished.
            if (!urlChanged && state?.styleScript != script) {
                webView.evaluateJavascript(script, null)
            }
            webView.tag = ArticleWebViewState(url, script)
            if (urlChanged) webView.loadUrl(url)
        },
        onRelease = { webView -> webView.destroy() }
    )
}
