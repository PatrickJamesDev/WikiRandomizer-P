package com.wikirandomizerp.app.ui.settingsScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.ui.common.PremiumBadge
import com.wikirandomizerp.app.ui.theme.ReadingTheme

/**
 * Picker for article reading themes. Sepia and the extra themes are premium; the default theme is
 * always available. Tapping a locked theme asks the user to donate instead of silently doing
 * nothing.
 */
@Composable
fun ReadingThemeDialog(
    selectedThemeId: String,
    isPremium: Boolean,
    onSelect: (ReadingTheme) -> Unit,
    onOpenDonation: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settingReadingTheme)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ReadingTheme.entries.forEach { theme ->
                    val locked = theme.premium && !isPremium
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                if (locked) {
                                    onDismiss()
                                    onOpenDonation()
                                } else {
                                    onSelect(theme)
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (theme.background == androidx.compose.ui.graphics.Color.Unspecified)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else theme.background
                                )
                        )
                        RadioButton(
                            selected = theme.id == selectedThemeId,
                            onClick = null
                        )
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(themeLabel(theme), style = MaterialTheme.typography.bodyLarge)
                                if (theme.premium) PremiumBadge()
                            }
                            Text(
                                stringResource(theme.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (locked) {
                            Icon(
                                painterResource(R.drawable.lock),
                                contentDescription = stringResource(R.string.premiumFeature),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (!isPremium) {
                    Text(
                        stringResource(R.string.premiumUnlockHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun themeLabel(theme: ReadingTheme): String = stringResource(theme.labelRes)
