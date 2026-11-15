package org.nsh07.wikireader.shared.ui.vocabulary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nsh07.wikireader.shared.library.VocabularyEntry
import org.nsh07.wikireader.shared.reader.DictionarySource

@Composable
fun VocabularyScreen(
    words: List<VocabularyEntry>,
    language: String,
    dictionarySource: DictionarySource,
    onAdd: (word: String, definition: String?) -> Unit,
    onOpenArticle: (lang: String, title: String) -> Unit,
    onDelete: (VocabularyEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdd by remember { mutableStateOf(false) }
    var word by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Look up a word") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = {
                            word = it
                            definition = null
                            error = null
                        },
                        placeholder = { Text("Word") },
                        singleLine = true
                    )
                    when {
                        loading -> CircularProgressIndicator()
                        definition != null -> Text(definition!!)
                        error != null -> Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = word.isNotBlank() && !loading,
                    onClick = {
                        if (definition != null) {
                            onAdd(word.trim(), definition)
                            showAdd = false
                        } else {
                            scope.launch {
                                loading = true
                                error = null
                                definition = dictionarySource.define(word.trim(), language)
                                loading = false
                                if (definition == null) error = "No definition found"
                            }
                        }
                    }
                ) {
                    Text(if (definition != null) "Add to vocabulary" else "Look up")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Close") }
            }
        )
    }

    if (words.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No vocabulary words yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Look up a word with Wiktionary and keep the definition here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { word = ""; definition = null; error = null; showAdd = true }) {
                    Text("Add a word")
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { word = ""; definition = null; error = null; showAdd = true }) {
                Text("Add a word")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear all") }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(words, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val title = entry.articleTitle
                            if (!title.isNullOrBlank()) onOpenArticle(entry.language, title)
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            entry.word,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        entry.definition?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDelete(entry) }) { Text("Delete") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
