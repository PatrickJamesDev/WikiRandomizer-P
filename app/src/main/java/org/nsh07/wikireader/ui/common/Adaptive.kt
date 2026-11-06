package org.nsh07.wikireader.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Small adaptive-layout helpers used by the reader and by tables.
 *
 * Android foldables report a wide window when unfolded (and can be half-opened). The app does not
 * try to force a two-pane layout everywhere, but it does stop article text and tables from
 * stretching edge to edge on those large surfaces, and it gives wide tables more columns before
 * they need sideways scrolling. Combined with the existing navigation rail, this keeps the
 * reading column comfortable when the device changes posture.
 */
enum class WidthBucket {
    COMPACT,
    MEDIUM,
    EXPANDED
}

fun WindowSizeClass.widthBucket(): WidthBucket = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WidthBucket.EXPANDED
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WidthBucket.MEDIUM
    else -> WidthBucket.COMPACT
}

/** Maximum readable article width. Compact phones keep the full width. */
fun WindowSizeClass.readingMaxWidth(): Dp = when (widthBucket()) {
    WidthBucket.COMPACT -> Dp.Unspecified
    WidthBucket.MEDIUM -> 760.dp
    WidthBucket.EXPANDED -> 920.dp
}

/** Widest comfortable table cell before the table should scroll horizontally instead. */
fun WindowSizeClass.tableCellMaxWidth(): Dp = when (widthBucket()) {
    WidthBucket.COMPACT -> 220.dp
    WidthBucket.MEDIUM -> 320.dp
    WidthBucket.EXPANDED -> 420.dp
}

/** Number of columns that fit before a table needs horizontal scrolling. */
fun WindowSizeClass.tableScrollAfterColumns(): Int = when (widthBucket()) {
    WidthBucket.COMPACT -> 3
    WidthBucket.MEDIUM -> 5
    WidthBucket.EXPANDED -> 7
}
