package com.wikirandomizerp.app.ui.backupScreen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.BackupInfo
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the user asked to restore, shown in the confirmation dialog first. */
private sealed class RestoreRequest {
    data class FromFile(val uri: Uri) : RestoreRequest()
    data class FromAuto(val backup: BackupInfo) : RestoreRequest()
}

/**
 * Export, restore and automatic backups of everything stored on the device: saved articles,
 * highlights, history, vocabulary, statistics, open tabs and settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onListAutoBackups: suspend () -> List<BackupInfo>,
    onExport: (Uri) -> Unit,
    onShare: () -> Unit,
    onRestoreFile: (Uri) -> Unit,
    onRestoreAuto: (File) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var autoBackups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        autoBackups = runCatching { onListAutoBackups() }.getOrDefault(emptyList())
    }
    var pending by remember { mutableStateOf<RestoreRequest?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) onExport(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pending = RestoreRequest.FromFile(uri) }

    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backupScreenTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { insets ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.download_done), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.backupExport)) },
                    supportingContent = { Text(stringResource(R.string.backupExportDesc)) },
                    colors = colors,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .clickable {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportLauncher.launch("wikireader-backup-$stamp.zip")
                        }
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.share), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.backupShare)) },
                    colors = colors,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onShare() }
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.undo), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.backupRestoreFile)) },
                    supportingContent = { Text(stringResource(R.string.backupRestoreFileDesc)) },
                    colors = colors,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                        .clickable { importLauncher.launch(arrayOf("*/*")) }
                )
            }

            item {
                Text(
                    stringResource(R.string.backupAutomatic),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 4.dp)
                )
                Text(
                    stringResource(R.string.backupPreUpdateDesc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }
            if (autoBackups.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.backupNoneYet),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(autoBackups, key = { it.file.name }) { backup ->
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.history), contentDescription = null)
                        },
                        headlineContent = {
                            Text(
                                stringResource(
                                    when (backup.reason) {
                                        "before-update" -> R.string.backupReasonUpdate
                                        "before-restore" -> R.string.backupReasonRestore
                                        else -> R.string.backupReasonManual
                                    }
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                dateFormat.format(Date(backup.createdAt)) + " · " +
                                        android.text.format.Formatter.formatShortFileSize(
                                            androidx.compose.ui.platform.LocalContext.current,
                                            backup.sizeBytes
                                        )
                            )
                        },
                        colors = colors,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pending = RestoreRequest.FromAuto(backup) }
                    )
                }
            }
        }
    }

    pending?.let { request ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.backupRestoreConfirmTitle)) },
            text = { Text(stringResource(R.string.backupRestoreConfirmDesc)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    when (request) {
                        is RestoreRequest.FromFile -> onRestoreFile(request.uri)
                        is RestoreRequest.FromAuto -> onRestoreAuto(request.backup.file)
                    }
                }) { Text(stringResource(R.string.backupRestoreConfirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
