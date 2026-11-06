package org.nsh07.wikireader.ui.dictionaryScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nsh07.wikireader.R
import org.nsh07.wikireader.ui.homeScreen.viewModel.DictionaryLookup

@Composable
fun DictionaryDialog(
    lookup: DictionaryLookup,
    onAddToVocabulary: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lookup.word) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    lookup.loading -> {
                        Text(stringResource(R.string.dictionaryLookingUp, lookup.word))
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    }
                    lookup.definition != null -> {
                        lookup.definition?.let { Text(it) }
                        Text(
                            stringResource(R.string.dictionarySource),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> Text(
                        stringResource(
                            R.string.dictionaryNoDefinition,
                            lookup.word
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAddToVocabulary,
                enabled = !lookup.loading
            ) {
                Text(stringResource(R.string.addToVocabulary))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
