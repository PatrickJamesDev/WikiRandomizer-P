package org.nsh07.wikireader.shared.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.nsh07.wikireader.shared.reader.ArticleInfoSource
import org.nsh07.wikireader.shared.reader.ArticleRevision

@Composable
fun ArticleInfoDialog(
    source: ArticleInfoSource,
    language: String,
    title: String,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var talk by remember { mutableStateOf<String?>(null) }
    var revisions by remember { mutableStateOf<List<ArticleRevision>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(source, language, title, tab) {
        loading = true
        if (tab == 0) {
            if (talk == null) {
                talk = try {
                    source.talkWikitext(language, title)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    ""
                }
            }
        } else if (revisions == null) {
            revisions = try {
                source.revisions(language, title)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                emptyList()
            }
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Article information")
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("Talk") }
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("History") }
                    )
                }
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    tab == 0 -> Text(
                        talk ?: "The talk page is empty or could not be loaded.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    revisions.isNullOrEmpty() -> Text(
                        "No revisions could be loaded.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> revisions.orEmpty().forEach { revision ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "Edited by ${revision.user.ifBlank { "—" }} at ${revision.timestamp}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (revision.comment.isNotBlank()) {
                                Text(
                                    revision.comment,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "${revision.size} bytes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
