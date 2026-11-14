package org.nsh07.wikireader.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    themeMode: String,
    languageName: String,
    seenCount: Int,
    browsingHistory: Boolean,
    onThemeModeChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    onBrowsingHistoryChange: (Boolean) -> Unit,
    onClearSeen: () -> Unit,
    onAboutClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionHeader("Appearance") }

        item {
            ThemeChoice(
                label = "Follow system",
                description = "Use the device light or dark setting",
                selected = themeMode == THEME_SYSTEM,
                onClick = { onThemeModeChange(THEME_SYSTEM) }
            )
        }
        item {
            ThemeChoice(
                label = "Light",
                description = "Always use the light reading theme",
                selected = themeMode == THEME_LIGHT,
                onClick = { onThemeModeChange(THEME_LIGHT) }
            )
        }
        item {
            ThemeChoice(
                label = "Dark",
                description = "Always use the dark reading theme",
                selected = themeMode == THEME_DARK,
                onClick = { onThemeModeChange(THEME_DARK) }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SectionHeader("Language & behaviour")
        }

        item {
            SettingsRow(
                title = "Wikipedia language",
                subtitle = languageName,
                onClick = onLanguageClick
            )
        }

        item {
            SettingsToggleRow(
                title = "Browsing history",
                subtitle = if (browsingHistory) {
                    "Opened articles are remembered on this device"
                } else {
                    "Opened articles are not added to History"
                },
                checked = browsingHistory,
                onCheckedChange = onBrowsingHistoryChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SectionHeader("Reading")
        }

        item {
            SettingsRow(
                title = "Forget seen articles",
                subtitle = if (seenCount == 0) {
                    "No articles remembered yet"
                } else {
                    "$seenCount articles will be offered again"
                },
                onClick = onClearSeen
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SectionHeader("About")
        }

        item {
            SettingsRow(
                title = "About WikiRandomizer-P",
                subtitle = "Version, links and the shared Compose port",
                onClick = onAboutClick
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun AboutScreen(
    onOpenRepository: () -> Unit,
    onOpenWikipedia: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "WikiRandomizer-P",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Read random Wikipedia articles from the topics you care about.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Version 1.0 beta",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text(
                text = "Android and iOS share the Kotlin core, the base color identity and the " +
                    "reader preferences. iOS already runs this shared Compose app shell, " +
                    "including Saved and History; the remaining Android screens are being " +
                    "moved into the same module.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenRepository
            ) {
                Text("Open the GitHub repository")
            }
        }

        item {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenWikipedia
            ) {
                Text("Visit Wikipedia")
            }
        }

        item {
            Text(
                text = "WikiRandomizer-P is free and ad-free. Wikipedia content is available " +
                    "under the Creative Commons Attribution-ShareAlike licence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeChoice(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"
