package org.nsh07.wikireader.ui.aboutScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.nsh07.wikireader.R
import org.nsh07.wikireader.WikiRandomizerPApplication
import org.nsh07.wikireader.data.UpdateInfo

/**
 * Release notes plus the download/install hand-off. Kept separate from [AboutScreen] so the same
 * dialog can later be reused by the background update prompt without duplicating install logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onOpenReleasePage: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? WikiRandomizerPApplication
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(stringResource(R.string.updateAvailable, info.version))
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (info.notes.isNotBlank()) {
                    Text(
                        stringResource(R.string.updateNotes),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        info.notes.take(6000),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (downloading) {
                    Text(
                        stringResource(R.string.updateDownloading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (error != null) {
                    Text(
                        stringResource(R.string.updateDownloadFailed, error.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (info.apkUrl == null) {
                    Text(
                        stringResource(R.string.updateNoApk),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (info.apkUrl != null) {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        val application = app
                        if (application == null) {
                            error = context.getString(R.string.updateFailed)
                        } else {
                            scope.launch {
                                downloading = true
                                error = null
                                progress = 0f
                                try {
                                    val file = application.container.updateChecker.download(info) { value ->
                                        progress = value
                                    }
                                    // A failed backup must never block an update.
                                    runCatching {
                                        application.container.backupManager.createBackup(
                                            prefix = "pre-update-backup"
                                        )
                                    }
                                    downloading = false
                                    application.container.updateChecker.install(file)
                                    onDismiss()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    downloading = false
                                    error = e.message ?: e.javaClass.simpleName
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.updateDownload))
                }
            } else {
                TextButton(onClick = onOpenReleasePage) {
                    Text(stringResource(R.string.updateOpenRelease))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}
