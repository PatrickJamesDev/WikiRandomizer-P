package org.nsh07.wikireader.shared.ui.article

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformArticleWebView(
    url: String,
    modifier: Modifier
) {
    ArticleWebViewUnavailable(modifier)
}
