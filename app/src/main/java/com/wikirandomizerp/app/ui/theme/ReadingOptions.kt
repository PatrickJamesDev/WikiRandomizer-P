package com.wikirandomizerp.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Optional page-style reading customisation (developer preview). Defaults reproduce the regular
 * article layout exactly, so the feature stays invisible while it is off.
 */
data class ReadingOptions(
    val lineSpacingPercent: Int = 100,
    val horizontalMarginDp: Int = 16,
    val justifyText: Boolean = false,
    val sepia: Boolean = false
)

val LocalReadingOptions = compositionLocalOf { ReadingOptions() }

/** Warm paper and soft ink for the optional sepia reading theme. */
val SepiaBackground = Color(0xFFF4ECD8)
val SepiaInk = Color(0xFF5B4636)
