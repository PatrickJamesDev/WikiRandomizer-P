package com.wikirandomizerp.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Counts how often the calling composable is (re)composed; shown in the debug log's VITALS line as
 * "recomposed: Name 12". A composable that recomposes dozens of times per second while scrolling
 * is a prime suspect for lag. Inline on purpose: a skippable function would not run again when the
 * caller recomposes. Costs nothing while the debug log is off.
 */
@Composable
inline fun TrackRecompositions(name: String) {
    if (DebugLog.enabled) SideEffect { PerfMonitor.recomposed(name) }
}

/**
 * Same as [TrackRecompositions], but records a small context string alongside the counter (for
 * example which article or how many sections the composable had). VITALS then shows, for the
 * busiest composables, what was on screen when they recomposed. The detail lambda only runs while
 * debug logging is enabled.
 */
@Composable
inline fun TrackRecompositions(name: String, noinline detail: () -> String) {
    if (DebugLog.enabled) SideEffect { PerfMonitor.recomposed(name, detail()) }
}

/**
 * Measures how long measuring + laying out this node (including its children) takes and reports
 * anything slow as SLOWMEASURE in the debug log. No-op while the debug log is off.
 */
fun Modifier.perfMeasure(name: String): Modifier =
    if (!DebugLog.enabled) this
    else this.layout { measurable, constraints ->
        val t0 = System.nanoTime()
        val placeable = measurable.measure(constraints)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        if (ms >= 6.0) PerfMonitor.slowMeasure(name, ms, placeable.width, placeable.height)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
