package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.ui.common.PremiumBadge
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.PreferencesState
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.SettingsAction
import com.wikirandomizerp.app.ui.theme.ReadingTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderAppearanceSheet(
    preferencesState: PreferencesState,
    onAction: (SettingsAction) -> Unit,
    onOpenDonation: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPremium = preferencesState.supporterMode
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentTheme = ReadingTheme.fromId(preferencesState.readingTheme)
    val percent = (preferencesState.fontSize / 16f * 100f).roundToInt()

    fun donate() {
        onDismiss()
        onOpenDonation()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(R.string.readerAppearance),
                style = MaterialTheme.typography.headlineSmall
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settingReadingTheme),
                    style = MaterialTheme.typography.titleSmall
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(ReadingTheme.entries, key = { it.id }) { theme ->
                        val locked = theme.premium && !isPremium
                        ThemeSwatch(
                            theme = theme,
                            selected = theme == currentTheme,
                            locked = locked,
                            onClick = {
                                if (locked) donate()
                                else onAction(SettingsAction.SaveReadingTheme(theme.id))
                            }
                        )
                    }
                }
                if (!isPremium) {
                    PremiumHint()
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.readerTextSize),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.readerTextSizeValue, percent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    enabled = preferencesState.fontSize > 10,
                    onClick = {
                        onAction(
                            SettingsAction.SaveFontSize(
                                (preferencesState.fontSize - 1).coerceAtLeast(10)
                            )
                        )
                    }
                ) {
                    Icon(painterResource(R.drawable.remove), contentDescription = null)
                }
                IconButton(
                    enabled = preferencesState.fontSize < 22,
                    onClick = {
                        onAction(
                            SettingsAction.SaveFontSize(
                                (preferencesState.fontSize + 1).coerceAtMost(22)
                            )
                        )
                    }
                ) {
                    Icon(painterResource(R.drawable.add), contentDescription = null)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.readerTextAlign),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!isPremium) PremiumBadge()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !preferencesState.justifyText,
                        onClick = {
                            if (isPremium) onAction(SettingsAction.SaveJustifyText(false))
                            else donate()
                        },
                        label = { Text(stringResource(R.string.readerTextAlignOriginal)) },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.format_align_left),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = preferencesState.justifyText,
                        onClick = {
                            if (isPremium) onAction(SettingsAction.SaveJustifyText(true))
                            else donate()
                        },
                        label = { Text(stringResource(R.string.readerTextAlignJustify)) },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.format_align_justify),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            PremiumSliderRow(
                title = stringResource(R.string.settingLineSpacing),
                value = preferencesState.lineSpacing,
                valueRange = 80f..200f,
                steps = 11,
                valueLabelRes = R.string.settingLineSpacingValue,
                enabled = isPremium,
                onValueChange = {
                    onAction(SettingsAction.SaveLineSpacing(it.roundToInt()))
                },
                onLockedClick = ::donate
            )

            PremiumSliderRow(
                title = stringResource(R.string.settingReadingMargin),
                value = preferencesState.readingMargin.toFloat(),
                valueRange = 4f..48f,
                steps = 10,
                valueLabelRes = R.string.settingReadingMarginValue,
                enabled = isPremium,
                onValueChange = {
                    onAction(SettingsAction.SaveReadingMargin(it.roundToInt()))
                },
                onLockedClick = ::donate
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: ReadingTheme,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    val background = if (theme.background == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        theme.background
    }
    val content = if (background.luminance() < 0.5f) Color.White else Color.Black
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        when {
            selected -> Icon(
                painterResource(R.drawable.check),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
            locked -> Icon(
                painterResource(R.drawable.lock),
                contentDescription = stringResource(R.string.premiumFeature),
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            else -> Text(
                text = "Aa",
                color = content,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PremiumHint() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PremiumBadge()
        Text(
            text = stringResource(R.string.premiumUnlockHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PremiumSliderRow(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabelRes: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onLockedClick: () -> Unit
) {
    var localValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!enabled) PremiumBadge()
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(valueLabelRes, localValue.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = localValue,
            onValueChange = { if (enabled) localValue = it },
            onValueChangeFinished = { if (enabled) onValueChange(localValue) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled
        )
        if (!enabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLockedClick)
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    painterResource(R.drawable.lock),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.premiumFeature),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
