package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R

/**
 * Shown when the reader taps Save while "save to folders" and/or "add a note" is enabled.
 * Both fields are optional, so the article can still be saved with one tap on Save.
 */
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun SaveArticleDialog(
    title: String,
    folders: List<String>,
    showFolder: Boolean,
    showNote: Boolean,
    onDismiss: () -> Unit,
    onSave: (folder: String?, note: String?) -> Unit
) {
    var folder by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.saveArticleTitle),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.saveArticleDesc, title),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (showFolder) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text(stringResource(R.string.folderLabel)) },
                        placeholder = { Text(stringResource(R.string.folderHint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (folders.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            folders.forEach { name ->
                                val selected = folder.trim().equals(name, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { folder = if (selected) "" else name },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }

                if (showNote) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.noteLabel)) },
                        placeholder = { Text(stringResource(R.string.noteHint)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDismiss
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { onSave(folder, note) }
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
