package com.wikirandomizerp.app.ui.homeScreen

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.WikiRevision

/**
 * Developer-preview article info: the talk page's wikitext and the recent revision history.
 * Both are fetched only when the sheet is opened and the corresponding tab is selected.
 */
@Composable
fun ArticleInfoDialog(
    title: String,
    lang: String,
    initialTab: Int,
    talkLoader: suspend (String, String) -> String?,
    revisionsLoader: suspend (String, String) -> List<WikiRevision>,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var talk by remember { mutableStateOf<String?>(null) }
    var revisions by remember { mutableStateOf<List<WikiRevision>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(title, lang, tab) {
        loading = true
        if (tab == 0) {
            talk = talk ?: talkLoader(title, lang)
        } else if (revisions.isEmpty()) {
            revisions = revisionsLoader(title, lang)
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = stringResource(R.string.articleInfoTitle))
                Text(
                    text = title,
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
                        label = { Text(stringResource(R.string.articleInfoTabTalk)) }
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text(stringResource(R.string.articleInfoTabHistory)) }
                    )
                }
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    tab == 0 -> Text(
                        text = talk ?: stringResource(R.string.articleInfoTalkEmpty),
                        style = MaterialTheme.typography.bodySmall
                    )
                    revisions.isEmpty() -> Text(
                        text = stringResource(R.string.articleInfoHistoryEmpty),
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> revisions.forEach { revision ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.articleInfoRevisionBy,
                                    revision.user.ifBlank { "—" },
                                    revision.timestamp
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (revision.comment.isNotBlank()) {
                                Text(
                                    text = revision.comment,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.articleInfoRevisionSize,
                                    revision.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.inat_close)) }
        }
    )
}
