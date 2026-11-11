package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.DividerProperties
import ir.ehsannarmani.compose_charts.models.DotProperties
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.PopupProperties

@Composable
fun ArticleViewsGraph(viewCounts: List<Int>, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    // The chart library does min/max maths over the values: an empty or single-point series (or a
    // all-zero one) can throw or produce NaN while drawing. A sparkline needs 2+ points anyway.
    if (viewCounts.size < 2) return
    // The library's default scale is 0..max, which divides by zero when every value is 0.
    val hi = viewCounts.max().toDouble()
    LineChart(
        modifier = modifier,
        maxValue = if (hi <= 0.0) 1.0 else hi,
        data = remember(viewCounts) {
            listOf(
                Line(
                    label = "",
                    values = viewCounts.map { it.toDouble() },
                    color = SolidColor(colorScheme.primary),
                    strokeAnimationSpec = tween(1500, easing = FastOutSlowInEasing),
                    curvedEdges = true
                )
            )
        },
        labelHelperProperties = LabelHelperProperties(enabled = false),
        gridProperties = GridProperties(enabled = false),
        popupProperties = PopupProperties(enabled = false),
        labelProperties = LabelProperties(enabled = false),
        indicatorProperties = HorizontalIndicatorProperties(enabled = false),
        dotsProperties = DotProperties(enabled = false),
        dividerProperties = DividerProperties(enabled = false)
    )
}