package org.nsh07.wikireader.ui.settingsScreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.DebugLog
import java.io.File

/**
 * Start of the supporter-only bug reporter.
 *
 * There is no server side yet: the report (description + optional log tail + device header) is
 * assembled locally and the user decides whether to copy it or send it through the system share
 * sheet. This keeps the feature honest about privacy and leaves room for a real endpoint later.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BugReportDialog(
    onDismiss: () -> Unit,
    onCopied: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var includeLog by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    fun buildReport(): String = buildString {
        appendLine("=== WikiRandomizer-P bug report ===")
        appendLine(DebugLog.lastCrumb().takeIf { it.isNotBlank() }?.let { "last action: $it" } ?: "")
        appendLine()
        appendLine("--- what happened ---")
        appendLine(description.trim())
        if (includeLog) {
            appendLine()
            appendLine("--- debug log ---")
            append(DebugLog.readTail(400_000))
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.reportBugTitle),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reportBugExplain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        if (it.isNotBlank()) error = false
                    },
                    label = { Text(stringResource(R.string.reportBugDescription)) },
                    isError = error,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.reportBugAttachLog),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = includeLog, onCheckedChange = { includeLog = it })
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDismiss
                    ) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            if (description.isBlank()) {
                                error = true
                            } else {
                                scope.launch {
                                    val report = withContext(Dispatchers.IO) { buildReport() }
                                    copyBugReport(context, report)
                                    onCopied()
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.reportBugCopy)) }
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            if (description.isBlank()) {
                                error = true
                            } else {
                                scope.launch {
                                    val report = withContext(Dispatchers.IO) { buildReport() }
                                    shareBugReport(context, report)
                                    onDismiss()
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.reportBugShare)) }
                }
            }
        }
    }
}

private fun copyBugReport(context: Context, report: String) {
    val clip = ClipData.newPlainText("WikiRandomizer-P bug report", report.takeLast(400_000))
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

private suspend fun shareBugReport(context: Context, report: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_logs").also { it.mkdirs() }
        val stamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        File(dir, "wikirandomizerp-bug-$stamp.txt").also { it.writeText(report) }
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "WikiRandomizer-P bug report")
        putExtra(Intent.EXTRA_TEXT, report.take(20_000))
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.reportBugTitle))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
