package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.LinkPreview

/**
 * Developer-preview long-press link preview. Shows the page summary without leaving the article,
 * with explicit choices to read it here or open it in a background tab.
 */
@Composable
fun LinkPreviewDialog(
    preview: LinkPreview?,
    loading: Boolean,
    onRead: () -> Unit,
    onOpenTab: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = preview?.title ?: stringResource(R.string.linkPreviewLoading),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preview?.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
                Text(
                    text = when {
                        loading -> stringResource(R.string.linkPreviewLoading)
                        preview != null -> preview.extract
                        else -> stringResource(R.string.linkPreviewFailed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRead, enabled = preview != null) {
                Text(stringResource(R.string.linkPreviewRead))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenTab) {
                    Text(stringResource(R.string.linkPreviewOpenTab))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.inat_close))
                }
            }
        }
    )
}
