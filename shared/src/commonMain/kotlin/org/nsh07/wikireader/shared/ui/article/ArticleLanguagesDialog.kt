package org.nsh07.wikireader.shared.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.nsh07.wikireader.shared.reader.ArticleLanguageLink
import org.nsh07.wikireader.shared.reader.ArticleLanguagesSource

@Composable
fun ArticleLanguagesDialog(
    source: ArticleLanguagesSource,
    language: String,
    title: String,
    onOpen: (ArticleLanguageLink) -> Unit,
    onDismiss: () -> Unit
) {
    var links by remember(source, language, title) {
        mutableStateOf<List<ArticleLanguageLink>?>(null)
    }
    LaunchedEffect(source, language, title) {
        links = try {
            source.languages(language, title)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Article languages")
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            val available = links
            when {
                available == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                available.isEmpty() -> Text(
                    "No other language versions were found for this article.",
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(available, key = { it.language }) { link ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpen(link) }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    link.label,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${link.language} · ${link.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
