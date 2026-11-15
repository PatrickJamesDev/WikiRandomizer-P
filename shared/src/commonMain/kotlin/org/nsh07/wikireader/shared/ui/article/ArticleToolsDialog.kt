package org.nsh07.wikireader.shared.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.nsh07.wikireader.shared.WikiUrls

@Composable
fun ArticleToolsDialog(
    language: String,
    title: String,
    onArticleInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val open: (String) -> Unit = { url ->
        uriHandler.openUri(url)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ToolButton("Article information") {
                    onArticleInfo()
                }
                ToolButton("Talk page") {
                    open(WikiUrls.talkPageUrl(language, title))
                }
                ToolButton("Revision history") {
                    open(WikiUrls.historyUrl(language, title))
                }
                ToolButton("Page information") {
                    open(WikiUrls.pageInfoUrl(language, title))
                }
                ToolButton("What links here") {
                    open(WikiUrls.whatLinksHereUrl(language, title))
                }
                ToolButton("Copy article link") {
                    clipboard.setText(AnnotatedString(WikiUrls.articleUrl(language, title)))
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
