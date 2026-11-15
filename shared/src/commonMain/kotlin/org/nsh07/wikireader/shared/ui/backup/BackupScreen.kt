package org.nsh07.wikireader.shared.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.nsh07.wikireader.shared.settings.BackupRepository

@Composable
fun BackupScreen(
    repository: BackupRepository,
    onRestored: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back to settings") }
        Text("Backup and restore", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The shared reader keeps its library in local settings. Copy this JSON somewhere safe, " +
                "or paste a previous backup below to restore it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                text = repository.export()
                clipboard.setText(AnnotatedString(text))
                message = "Backup copied to the clipboard."
            }
        ) { Text("Export and copy backup") }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Backup JSON") },
            minLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    text = clipboard.getText()?.text.orEmpty()
                    message = "Pasted from the clipboard."
                }
            ) { Text("Paste") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val restored = repository.import(text)
                    message = if (restored == null) {
                        "That text is not a WikiReader shared backup."
                    } else {
                        "Restored $restored settings. Reopen screens to see the data."
                    }
                    if (restored != null) onRestored()
                }
            ) { Text("Restore") }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
