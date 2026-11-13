package org.nsh07.wikireader.shared.ui.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full Wikipedia article rendered by the platform web engine.
 *
 * Wikipedia's mobile page already contains references, tables, collapsible sections and math, so
 * using the system web view keeps the shared reader at parity while the native wikitext parser is
 * migrated. Android uses `WebView`; iOS uses `WKWebView`. JVM/Wasm have no mobile web engine, so
 * they show a short placeholder instead.
 */
@Composable
expect fun PlatformArticleWebView(
    url: String,
    modifier: Modifier = Modifier
)

@Composable
internal fun ArticleWebViewUnavailable(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "The embedded article view is available in the Android and iOS apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
