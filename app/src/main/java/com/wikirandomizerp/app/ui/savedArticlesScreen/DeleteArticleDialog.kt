package com.wikirandomizerp.app.ui.savedArticlesScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.ArticleInfo
import com.wikirandomizerp.app.data.WRStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteArticleDialog(
    pageId: Int?,
    lang: String?,
    title: String?,
    showSnackbar: (String) -> Unit,
    setShowDeleteDialog: (Boolean) -> Unit,
    deleteArticle: (Int, String) -> WRStatus,
    deleteAll: () -> WRStatus
) {
    val articleName: String? = title
    val context = LocalContext.current
    BasicAlertDialog(
        onDismissRequest = { setShowDeleteDialog(false) }
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (articleName != null) stringResource(R.string.deleteSavedArticle)
                    else stringResource(R.string.deleteAllArticles),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.padding(16.dp))
                Text(
                    text =
                    if (articleName != null)
                        stringResource(R.string.deleteSavedArticleDesc, articleName)
                    else
                        stringResource(R.string.deleteAllArticlesDesc),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { setShowDeleteDialog(false) }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        setShowDeleteDialog(false)
                        val status = if (title != null) deleteArticle(pageId ?: 0, lang ?: "en")
                        else deleteAll()
                        if (status == WRStatus.SUCCESS)
                            showSnackbar(context.getString(R.string.articleDeleted))
                        else
                            showSnackbar(
                                context.getString(
                                    R.string.unableToDeleteArticle,
                                    status.name
                                )
                            )
                    }
                    ) {
                        Text(text = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

/**
 * Long-press editor for a saved article: lets the reader move it to another folder, write or
 * change a note, or delete it. The folder/note fields are only shown when the matching setting
 * is enabled, so the default (both off) keeps the familiar one-tap delete flow.
 */
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun SavedArticleDetailsDialog(
    article: ArticleInfo,
    folders: List<String>,
    showFolder: Boolean,
    showNote: Boolean,
    onDismiss: () -> Unit,
    onSave: (folder: String?, note: String?) -> Unit,
    onDelete: () -> Unit
) {
    var folder by remember(article.pageId, article.lang) {
        mutableStateOf(article.folder.orEmpty())
    }
    var note by remember(article.pageId, article.lang) {
        mutableStateOf(article.note.orEmpty())
    }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.editSavedArticle),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.editSavedArticleDesc, article.title),
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDelete
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row {
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
}