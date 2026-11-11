package com.wikirandomizerp.app.ui.settingsScreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.DebugLog

/** Full-screen viewer for the debug log (last ~200 KB), with copy / share / clear. */
@Composable
fun DebugLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        lines = withContext(Dispatchers.IO) { DebugLog.readTail().lines() }
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.surface) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    stringResource(R.string.debugLogTitle),
                    style = typography.titleLarge,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    DebugLog.logFile()?.absolutePath ?: "",
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { copyToClipboard(context, lines.joinToString("\n")) }) {
                        Text(stringResource(R.string.debugLogCopy))
                    }
                    TextButton(onClick = { scope.launch { shareLogFile(context) } }) {
                        Text(stringResource(R.string.debugLogShare))
                    }
                    TextButton(onClick = { reload++ }) { Text(stringResource(R.string.debugLogRefresh)) }
                    TextButton(onClick = {
                        DebugLog.clear()
                        reload++
                    }) { Text(stringResource(R.string.debugLogClear)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.debugLogClose)) }
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = when {
                                line.contains(" E/") || line.contains("CRASH") -> colorScheme.error
                                line.contains(" W/") -> colorScheme.tertiary
                                line.contains("PERF") -> colorScheme.primary
                                else -> colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    // Clipboard transactions are size-limited; the tail is what matters anyway.
    val clip = ClipData.newPlainText("WikiRandomizer-P debug log", text.takeLast(400_000))
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

private suspend fun shareLogFile(context: Context) {
    val file = withContext(Dispatchers.IO) { DebugLog.exportToFile() } ?: return
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, "Share debug log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
