package org.nsh07.wikireader.shared.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

private data class TutorialPage(val title: String, val body: String)

private val pages = listOf(
    TutorialPage(
        "Random by topic",
        "Choose plants, history, physics or another subject in Topics. Shuffle then picks an " +
            "article you have not seen yet, and falls back to general Wikipedia when needed."
    ),
    TutorialPage(
        "Read your way",
        "Open Discover for featured and trending reading, or search Wikipedia directly. " +
            "Adjust text size, spacing, margins, typeface and sepia mode in Settings."
    ),
    TutorialPage(
        "Keep what matters",
        "Save articles, pictures and highlights, look up words in Wiktionary, and back up the " +
            "whole reader as JSON from Settings. The library stays on this device."
    )
)

@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val page = pages[index]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(page.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(page.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${index + 1} of ${pages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (index == pages.lastIndex) {
                TextButton(onClick = onDismiss) { Text("Start reading") }
            } else {
                TextButton(onClick = { index++ }) { Text("Next") }
            }
        },
        dismissButton = {
            Row {
                if (index > 0) {
                    TextButton(onClick = { index-- }) { Text("Back") }
                }
                TextButton(onClick = onDismiss) { Text("Skip") }
            }
        }
    )
}
