package org.nsh07.wikireader.ui.settingsScreen

import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.kiwix.KiwixStatus

/**
 * Optional Kiwix/ZIM database. The file is chosen with the system picker and its read permission
 * is persisted, so a Wikipedia ZIM can live on an SD card or in Downloads and still be readable
 * after a restart. Nothing here is enabled until the user picks an archive.
 */
@Composable
fun KiwixSettingsCard(
    status: KiwixStatus,
    onSelect: (Uri, String?) -> Unit,
    onRemove: () -> Unit,
    onUseForRandom: (Boolean) -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        onSelect(uri, displayName)
    }

    fun chooseFile() {
        // ZIM files are usually reported as application/octet-stream; many document providers
        // report nothing useful at all, so let the user pick any file and validate by opening it.
        picker.launch(arrayOf("*/*"))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.kiwixTitle), style = typography.titleMedium)
            Text(
                stringResource(R.string.kiwixDesc),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            if (!status.selected) {
                Button(onClick = { chooseFile() }) {
                    Text(stringResource(R.string.kiwixChoose))
                }
            } else {
                val name = status.title
                    ?: status.displayName
                    ?: stringResource(R.string.kiwixTitle)
                Text(name, style = typography.titleSmall)
                status.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        description,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
                if (status.ready) {
                    Text(
                        stringResource(
                            R.string.kiwixDetails,
                            status.articleCount,
                            Formatter.formatShortFileSize(context, status.sizeBytes)
                        ),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                status.error?.let { message ->
                    Text(
                        text = stringResource(R.string.kiwixOpenFailed, message),
                        style = typography.bodySmall,
                        color = colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.kiwixUseForRandom),
                            style = typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.kiwixUseForRandomDesc),
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = status.useForRandom,
                        onCheckedChange = onUseForRandom,
                        enabled = status.ready
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenReader,
                        enabled = status.ready,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.kiwixOpenReader),
                            maxLines = 1
                        )
                    }
                    OutlinedButton(onClick = onRemove) {
                        Text(stringResource(R.string.kiwixRemove))
                    }
                }
                TextButton(
                    onClick = { chooseFile() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.kiwixChooseAnother))
                }
            }
        }
    }
}
