package org.nsh07.wikireader.ui.homeScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.DebugLog
import org.nsh07.wikireader.parser.parseWikitable
import org.nsh07.wikireader.ui.common.tableCellMaxWidth
import org.nsh07.wikireader.ui.common.tableScrollAfterColumns
import kotlin.math.max

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AsyncWikitable(
    text: String,
    fontSize: Int,
    onLinkClick: (String) -> Unit,
    showRef: (String) -> Unit
) {
    val colorScheme = colorScheme
    val typography = typography
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    // Tables get more room on tablets, unfolded foldables and desktop-size windows. Below the
    // medium breakpoint cells stay narrow enough to read without horizontal scrolling for the
    // common 2-3 column infobox tables.
    val maxCellWidth = windowSizeClass.tableCellMaxWidth()
    val scrollAfterColumns = windowSizeClass.tableScrollAfterColumns()

    var rows by remember {
        mutableStateOf(
            Pair(
                AnnotatedString(""),
                emptyList<List<AnnotatedString>>()
            )
        )
    }
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var parsedText by remember { mutableStateOf<String?>(null) }
    var parsedColorScheme by remember { mutableStateOf<Any?>(null) }
    var parsedFontSize by remember { mutableStateOf(-1) }
    val tableTitle =
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(
                    stringResource(R.string.table)
                )
            }
        }

    LaunchedEffect(text, isExpanded, colorScheme, fontSize) {
        // Tables are collapsed by default; parsing one can be expensive (and a table with a
        // hidden cladogram used to be parsed eagerly on every article). Parse on first expand,
        // and re-parse when the theme or font size changes so colours and line height do not
        // stay stale after switching to light/dark mode.
        if (!isExpanded) return@LaunchedEffect
        if (parsedText == text &&
            parsedColorScheme === colorScheme &&
            parsedFontSize == fontSize
        ) return@LaunchedEffect

        try {
            rows = parseWikitable(text, colorScheme, typography, onLinkClick, showRef, fontSize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DebugLog.e("Wikitable", "could not parse table (showing it empty)", e)
            rows = Pair(AnnotatedString(""), emptyList())
        }
        parsedText = text
        parsedColorScheme = colorScheme
        parsedFontSize = fontSize
    }

    OutlinedCard(
        onClick = { isExpanded = !isExpanded },
        border = BorderStroke(1.dp, colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.padding(8.dp)
            ) {
                if (!isExpanded)
                    Icon(painterResource(R.drawable.keyboard_arrow_down), contentDescription = stringResource(R.string.expand_section))
                else
                    Icon(painterResource(R.drawable.keyboard_arrow_up), contentDescription = stringResource(R.string.collapse_section))
            }
            Text(
                rows.first.ifEmpty { tableTitle },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .padding(end = 16.dp)
            )
        }
    }

    AnimatedVisibility(
        isExpanded,
        enter = expandVertically(expandFrom = Alignment.CenterVertically) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.CenterVertically) + fadeOut()
    ) {
        val tableRows = rows.second
        val columnCount = tableRows.maxOfOrNull { it.size } ?: 0

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            if (columnCount == 0) {
                Text(
                    text = stringResource(R.string.tableEmpty),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Few columns stretch to the available width. Wide tables keep their natural size
                // and scroll sideways instead of squeezing every cell into an unreadable sliver.
                val needsHorizontalScroll = columnCount > scrollAfterColumns
                val scrollState = rememberScrollState()
                val tableModifier = Modifier
                    .border(1.dp, color = colorScheme.outline, shape = shapes.large)
                    .padding(1.dp)

                Box(
                    modifier = tableModifier.then(
                        if (needsHorizontalScroll) Modifier
                            .horizontalScroll(scrollState)
                            .fillMaxWidth()
                        else Modifier.fillMaxWidth()
                    )
                ) {
                    Table(
                        rows = tableRows.size,
                        columns = columnCount,
                        stretchColumns = !needsHorizontalScroll,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (needsHorizontalScroll) Modifier else Modifier.fillMaxWidth()
                    ) { row, column ->
                        val cell = tableRows.getOrNull(row)?.getOrNull(column) ?: AnnotatedString("")
                        Text(
                            text = cell,
                            fontSize = fontSize.sp,
                            lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                            fontWeight = if (row == 0) FontWeight.SemiBold else null,
                            modifier = Modifier
                                .background(
                                    if (row == 0) colorScheme.surfaceContainerHigh
                                    else colorScheme.surface
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = colorScheme.outlineVariant
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .widthIn(min = 88.dp, max = maxCellWidth)
                        )
                    }
                }

                if (needsHorizontalScroll) {
                    Text(
                        text = stringResource(R.string.tableSwipeHint),
                        style = typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun Table(
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    stretchColumns: Boolean = true,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    cell: @Composable (row: Int, column: Int) -> Unit
) {
    val measurePolicy =
        remember(rows, columns, stretchColumns, verticalAlignment, horizontalAlignment) {
            TableMeasurePolicy(
                rows,
                columns,
                stretchColumns,
                verticalAlignment,
                horizontalAlignment
            )
        }
    Layout(content = {
        repeat(rows) { row ->
            repeat(columns) { column ->
                cell(row, column)
            }
        }
    }, measurePolicy = measurePolicy, modifier = modifier)
}

private class TableMeasurePolicy(
    private val rows: Int,
    private val columns: Int,
    private val stretchColumns: Boolean,
    private val verticalAlignment: Alignment.Vertical,
    private val horizontalAlignment: Alignment.Horizontal
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val measured = measurables.map {
            it.measure(
                constraints.copy(
                    minWidth = 0,
                    minHeight = 0
                )
            )
        }

        val columnWidths = List(columns) { column ->
            var maxCellWidth = 0
            repeat(rows) { row ->
                val i = (row * columns) + column
                val placeable = measured[i]
                maxCellWidth = max(placeable.width, maxCellWidth)
            }
            maxCellWidth
        }.toMutableList()

        var tableWidth = columnWidths.sum()
        val availableWidth = constraints.maxWidth
        if (stretchColumns && availableWidth != Constraints.Infinity && columns > 0 &&
            tableWidth < availableWidth
        ) {
            // Share the extra space equally; the remainder goes to the leftmost columns so the
            // table exactly fills its parent instead of leaving a ragged strip on wide screens.
            val extra = (availableWidth - tableWidth) / columns
            val remainder = (availableWidth - tableWidth) % columns
            for (column in 0 until columns) {
                columnWidths[column] += extra + if (column < remainder) 1 else 0
            }
            tableWidth = availableWidth
        }

        val rowHeights = List(rows) { row ->
            var maxCellHeight = 0
            repeat(columns) { column ->
                val placeable = measured[(row * columns) + column]
                maxCellHeight = max(placeable.height, maxCellHeight)
            }
            maxCellHeight
        }

        val tableHeight = rowHeights.sum()

        return layout(tableWidth, tableHeight) {
            var y = 0
            repeat(rows) { row ->
                var x = 0
                val rowHeight = rowHeights[row]
                repeat(columns) { column ->
                    val placeable = measured[row * columns + column]
                    val columnWidth = columnWidths[column]
                    val yOffset = verticalAlignment.align(placeable.height, rowHeight)
                    val xOffset =
                        horizontalAlignment.align(placeable.width, columnWidth, layoutDirection)
                    placeable.placeRelative(x + xOffset, y + yOffset)

                    x += columnWidth
                }
                y += rowHeight
            }
        }
    }
}
