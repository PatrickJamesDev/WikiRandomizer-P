package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.parser.CladeNode
import com.wikirandomizerp.app.parser.parseClade
import com.wikirandomizerp.app.parser.toWikitextAnnotatedStringSafe

private class CladeLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

private class CladeText(
    val layout: TextLayoutResult,
    val x: Float,
    val y: Float,
    val isLabel: Boolean
)

/** A coloured `barN` strip drawn beside a group of descendants. */
private class CladeBar(val x: Float, val y1: Float, val y2: Float, val color: Int)

private class CladeDrawing(
    val lines: List<CladeLine>,
    val texts: List<CladeText>,
    val bars: List<CladeBar>,
    val width: Float,
    val height: Float
)

private class CladePlaced(val cy: Float, val bottom: Float)

/**
 * A phylogenetic tree ("cladogram") drawn like on the desktop site: the root on the left,
 * branches to the right, taxon names at the tips and branch labels above the branch lines.
 * Wide trees scroll sideways.
 */
@Composable
fun Cladogram(
    text: String,
    fontSize: Int,
    modifier: Modifier = Modifier
) {
    val tree = remember(text) {
        try {
            parseClade(text)
        } catch (e: Throwable) {
            DebugLog.w("Clade", "could not parse a clade tree", e)
            null
        }
    }
    if (tree == null) {
        DebugLog.w("Clade", "could not parse a clade block (${text.length} chars): ${text.take(120)}")
        // Never leave a hole in the article: if a tree uses syntax we do not understand yet,
        // show the source instead of silently dropping the whole block.
        Text(
            text = text.trim(),
            fontSize = (fontSize - 2).coerceAtLeast(11).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }

    // Read the theme here: composable getters cannot be called inside remember { }.
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lineColor = colorScheme.outline
    val textColor = colorScheme.onSurface
    val labelColor = colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val availablePx = with(density) { maxWidth.toPx() }
        val drawing = remember(tree, availablePx, fontSize, colorScheme, typography) {
            try {
                buildCladeDrawing(
                    root = tree,
                    measurer = measurer,
                    leafStyle = TextStyle(fontSize = (fontSize - 1).coerceAtLeast(11).sp, color = textColor),
                    labelStyle = TextStyle(fontSize = (fontSize - 4).coerceAtLeast(10).sp, color = labelColor),
                    toText = { s ->
                        s.toWikitextAnnotatedStringSafe(
                            colorScheme = colorScheme,
                            typography = typography,
                            loadPage = {},
                            fontSize = fontSize,
                            newLine = false,
                            showRef = {}
                        )
                    },
                    availablePx = availablePx,
                    minStub = with(density) { 16.dp.toPx() },
                    pad = with(density) { 6.dp.toPx() },
                    gap = with(density) { 6.dp.toPx() },
                    rootInset = with(density) { 14.dp.toPx() },
                    minLeafWidth = with(density) { 90.dp.toPx() },
                    // Taxa names should be laid out as one readable line instead of being wrapped
                    // at whatever width happened to be left over on screen. Wide trees scroll.
                    maxLeafWidth = with(density) { 240.dp.toPx() }
                )
            } catch (e: Throwable) {
                DebugLog.w("Clade", "could not lay out a clade tree", e)
                null
            }
        }
        if (drawing != null) {
            val widthDp = with(density) { drawing.width.toDp() }
            val heightDp = with(density) { drawing.height.toDp() }
            val strokePx = with(density) { 1.5.dp.toPx() }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Canvas(modifier = Modifier.size(widthDp, heightDp)) {
                    drawing.lines.forEach {
                        drawLine(
                            color = lineColor,
                            start = Offset(it.x1, it.y1),
                            end = Offset(it.x2, it.y2),
                            strokeWidth = strokePx
                        )
                    }
                    drawing.bars.forEach {
                        drawLine(
                            color = Color(it.color),
                            start = Offset(it.x, it.y1),
                            end = Offset(it.x, it.y2),
                            strokeWidth = strokePx * 3f
                        )
                    }
                    drawing.texts.forEach {
                        drawText(
                            textLayoutResult = it.layout,
                            color = if (it.isLabel) labelColor else textColor,
                            topLeft = Offset(it.x, it.y)
                        )
                    }
                }
            }
        }
    }
}

private fun buildCladeDrawing(
    root: CladeNode.Branch,
    measurer: TextMeasurer,
    leafStyle: TextStyle,
    labelStyle: TextStyle,
    toText: (String) -> AnnotatedString,
    availablePx: Float,
    minStub: Float,
    pad: Float,
    gap: Float,
    rootInset: Float,
    minLeafWidth: Float,
    maxLeafWidth: Float
): CladeDrawing {
    val lines = ArrayList<CladeLine>()
    val texts = ArrayList<CladeText>()
    val bars = ArrayList<CladeBar>()
    var maxRight = 0f

    fun measureLabel(raw: String): TextLayoutResult? {
        if (raw.isBlank()) return null
        return measurer.measure(
            text = toText(raw),
            style = labelStyle,
            // Labels sit on a line of their own; giving them the same width as a leaf keeps long
            // taxon names readable instead of wrapping into a narrow column on deep trees.
            constraints = Constraints(maxWidth = maxLeafWidth.toInt().coerceAtLeast(80))
        )
    }

    fun place(node: CladeNode, x: Float, top: Float): CladePlaced {
        if (node is CladeNode.Leaf) {
            // Measure at the full leaf width even when the tree is already wider than the screen.
            // The old code shrank every deep leaf to the leftover viewport width, so taxa wrapped
            // into tall, cramped columns instead of extending the horizontally scrollable canvas.
            val maxW = maxLeafWidth.toInt().coerceAtLeast(minLeafWidth.toInt())
            val layout = measurer.measure(
                text = toText(node.text),
                style = leafStyle,
                constraints = Constraints(maxWidth = maxW)
            )
            val h = layout.size.height.toFloat()
            texts.add(CladeText(layout, x + pad, top, false))
            val right = x + pad + layout.size.width
            if (right > maxRight) maxRight = right
            return CladePlaced(top + h / 2f, top + h)
        }

        val branch = node as CladeNode.Branch
        var cursor = top
        var firstCy = -1f
        var lastCy = 0f
        for (child in branch.children) {
            val label = measureLabel(child.label)
            val sub = measureLabel(child.sublabel)
            val group = measureLabel(child.groupLabel)
            val labelH = (label?.size?.height ?: 0).toFloat()
            val labelW = (label?.size?.width ?: 0).toFloat()
            val subW = (sub?.size?.width ?: 0).toFloat()
            val groupW = (group?.size?.width ?: 0).toFloat()
            val stub = maxOf(minStub, maxOf(labelW, subW, groupW) + pad * 2f)
            val childX = x + stub
            val childTop = cursor + labelH
            val placed = place(child.node, childX, childTop)
            lines.add(CladeLine(x, placed.cy, childX, placed.cy))
            if (label != null) texts.add(CladeText(label, x + pad, placed.cy - labelH, true))
            if (sub != null) texts.add(CladeText(sub, x + pad, placed.cy + 2f, true))
            if (group != null) {
                // `grouplabelN` names a whole clade. The parser has always kept it, but the
                // renderer ignored it, so those labels were missing from every tree.
                val gx = x + stub * 0.55f
                val gy = (childTop + placed.bottom) / 2f - group.size.height / 2f
                texts.add(CladeText(group, gx, gy, true))
                if (gx + group.size.width > maxRight) maxRight = gx + group.size.width
            }
            val barColor = parseCladeColor(child.barColor)
            if (barColor != null) {
                // The colour strip runs the full height of the child's group, like on Wikipedia.
                bars.add(
                    CladeBar(
                        x = x + stub * 0.35f,
                        y1 = childTop + 2f,
                        y2 = placed.bottom.coerceAtLeast(childTop + 4f),
                        color = barColor
                    )
                )
            }
            if (firstCy < 0f) firstCy = placed.cy
            lastCy = placed.cy
            cursor = placed.bottom + gap
        }
        if (firstCy < 0f) firstCy = top
        lines.add(CladeLine(x, firstCy, x, lastCy))
        return CladePlaced((firstCy + lastCy) / 2f, (cursor - gap).coerceAtLeast(top))
    }

    val placedRoot = place(root, rootInset, 0f)
    lines.add(CladeLine(0f, placedRoot.cy, rootInset, placedRoot.cy))
    return CladeDrawing(lines, texts, bars, maxRight + pad, placedRoot.bottom + pad)
}

/** `red`, `#c00`, `rgb(...)` → ARGB, or null for an unknown value. */
private fun parseCladeColor(value: String): Int? {
    val v = value.trim().removeSurrounding("\"").removeSurrounding("'")
    if (v.isEmpty()) return null
    return runCatching { android.graphics.Color.parseColor(v) }.getOrNull()
}
