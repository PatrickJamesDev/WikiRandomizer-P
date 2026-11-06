package org.nsh07.wikireader.ui.randomTopicsScreen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
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
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.ArticleCache

/**
 * Explicit "download N articles for offline reading" control. The selection itself is made by the
 * view model from the enabled topic libraries, so the number is spread as evenly as possible
 * across topics instead of coming from one large category.
 */
@Composable
fun OfflineDownloadCard(
    stats: ArticleCache.OfflineStats?,
    count: Int,
    estimateBytes: Long,
    downloading: Boolean,
    progress: Pair<Int, Int>?,
    enabled: Boolean = true,
    estimateCount: Int? = null,
    onCountChange: (Int) -> Unit,
    onDownload: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.offlineDownloadTitle), style = typography.titleMedium)
            Text(
                stringResource(R.string.offlineDownloadDesc),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { onCountChange((count - 10).coerceAtLeast(10)) },
                    enabled = !downloading && count > 10
                ) { Text("−") }
                Text(
                    stringResource(R.string.offlineDownloadCount, count),
                    style = typography.titleMedium
                )
                TextButton(
                    onClick = { onCountChange((count + 10).coerceAtMost(1000)) },
                    enabled = !downloading && count < 1000
                ) { Text("+") }
            }

            Text(
                stringResource(
                    R.string.offlineDownloadEstimate,
                    Formatter.formatShortFileSize(context, estimateBytes)
                ),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            when {
                estimateCount == null -> Unit
                estimateCount == 0 -> Text(
                    stringResource(R.string.offlineDownloadNothingNew),
                    style = typography.bodySmall,
                    color = colorScheme.error
                )
                estimateCount < count -> Text(
                    stringResource(R.string.offlineDownloadAvailable, estimateCount),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (stats == null || stats.articles == 0)
                    stringResource(R.string.offlineDownloadEmpty)
                else stringResource(
                    R.string.offlineDownloadStored,
                    stats.articles,
                    Formatter.formatShortFileSize(context, stats.bytes)
                ),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            if (!enabled) {
                Text(
                    stringResource(R.string.offlineDownloadNeedsLibrary),
                    style = typography.bodySmall,
                    color = colorScheme.error
                )
            }

            if (downloading) {
                Spacer(Modifier.height(4.dp))
                val done = progress?.first ?: 0
                val total = progress?.second ?: 0
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    stringResource(R.string.offlineDownloadProgress, done, total),
                    style = typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDownload,
                    enabled = enabled && !downloading && count > 0 &&
                            (estimateCount == null || estimateCount > 0)
                ) {
                    Text(stringResource(R.string.offlineDownloadStart))
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !downloading && (stats?.articles ?: 0) > 0
                ) {
                    Text(stringResource(R.string.offlineDownloadDeleteAll))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.offlineDownloadDeleteAll)) },
            text = { Text(stringResource(R.string.offlineDownloadDeleteConfirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAll()
                }) { Text(stringResource(R.string.libraryRemove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
