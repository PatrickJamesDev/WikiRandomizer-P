package org.nsh07.wikireader.ui.image

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp

/** Zoom limits and tuning shared by both full-screen picture viewers. */
internal const val MIN_IMAGE_SCALE = 1f
internal const val MAX_IMAGE_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 3f

/** Zoom factor per pixel of vertical finger travel while "tap, then hold and drag" zooming. */
private const val DRAG_ZOOM_PER_PIXEL = 0.005f

/**
 * Scale and pan of a full-screen picture, plus the maths that keeps the point under the finger
 * fixed while zooming. [size] is the size of the area the picture is shown in; every anchor point
 * handed to these functions is in that area's coordinates (origin top-left, not scaled).
 */
@Stable
internal class ImageZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    /** Size of the picture itself, used to keep it from being dragged out of the screen. */
    var size by mutableStateOf(IntSize.Zero)

    /** Size of the whole viewing area. The picture scales around its centre; touches are in this area's coordinates. */
    var areaSize by mutableStateOf(IntSize.Zero)

    /** The running double-tap animation, so a new gesture can cancel it. */
    var animation: Job? = null

    /** Keeps the picture from being dragged out of the screen. */
    fun clampedOffset(value: Float, raw: Offset): Offset {
        val maxX = (size.width * (value - 1) / 2f).coerceAtLeast(0f)
        val maxY = (size.height * (value - 1) / 2f).coerceAtLeast(0f)
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    /** Offset that keeps the picture point under [anchor] where it is when going to [target] scale. */
    private fun offsetFor(anchor: Offset, startScale: Float, startOffset: Offset, target: Float): Offset {
        if (target <= 1f || startScale <= 0f) return Offset.Zero
        val centre = Offset(areaSize.width / 2f, areaSize.height / 2f)
        val p = anchor - centre
        return clampedOffset(target, p - (p - startOffset) * (target / startScale))
    }

    /** Multiplies the zoom by [factor] around [anchor] (used while dragging with one finger). */
    fun zoomBy(anchor: Offset, factor: Float) {
        val start = scale
        val target = (start * factor).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
        if (target == start) return
        scale = target
        offset = offsetFor(anchor, start, offset, target)
    }

    /** Double tap: zoom in towards [anchor], or back out when already zoomed. */
    fun toggleZoom(
        anchor: Offset,
        scope: CoroutineScope,
        spec: AnimationSpec<Float>,
        onScale: (Float) -> Unit
    ) {
        animation?.cancel()
        val startScale = scale
        val startOffset = offset
        val target = if (startScale <= 1.01f) DOUBLE_TAP_SCALE else 1f
        val targetOffset = offsetFor(anchor, startScale, startOffset, target)
        animation = scope.launch {
            animate(initialValue = 0f, targetValue = 1f, animationSpec = spec) { fraction, _ ->
                val value = startScale + (target - startScale) * fraction
                scale = value
                offset = Offset(
                    startOffset.x + (targetOffset.x - startOffset.x) * fraction,
                    startOffset.y + (targetOffset.y - startOffset.y) * fraction
                )
                onScale(value)
            }
        }
    }
}

/**
 * Tap, double tap and "double tap, then drag" on a full-screen picture.
 *
 *  - one tap: [onTap] (show/hide the bars);
 *  - two quick taps: zoom in/out around the second tap;
 *  - tap once, then touch down again and move the finger without lifting it: the picture zooms
 *    continuously around that point (drag down = zoom in, drag up = zoom out), like the map and
 *    photo apps. This is what makes one-handed zooming with a thumb possible.
 *
 * Put this on the area that contains the picture (outside the picture's own graphicsLayer) so the
 * positions it reports are not distorted by the zoom itself. Pinch and pan stay with the
 * `transformable` modifier on the picture.
 */
internal fun Modifier.imageZoomGestures(
    zoom: ImageZoomState,
    scope: CoroutineScope,
    doubleTapSpec: AnimationSpec<Float>,
    onTap: () -> Unit,
    onScaleChanged: (Float) -> Unit
): Modifier = pointerInput(zoom) {
    detectTapAndDragZoom(
        onTap = onTap,
        onDoubleTap = { anchor -> zoom.toggleZoom(anchor, scope, doubleTapSpec, onScaleChanged) },
        onDragZoomStart = { zoom.animation?.cancel() },
        onDragZoom = { anchor, factor ->
            zoom.zoomBy(anchor, factor)
            onScaleChanged(zoom.scale)
        }
    )
}

private suspend fun PointerInputScope.detectTapAndDragZoom(
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onDragZoomStart: () -> Unit,
    onDragZoom: (anchor: Offset, factor: Float) -> Unit
) {
    val slop = viewConfiguration.touchSlop
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        // null: the finger moved (pan), a second finger arrived (pinch) or the touch was
        // cancelled. Those belong to the transformable modifier, not to this detector.
        waitForUpOrCancellation() ?: return@awaitEachGesture

        val second = withTimeoutOrNull(doubleTapTimeout) {
            awaitFirstDown(requireUnconsumed = false)
        }
        if (second == null) {
            onTap()
            return@awaitEachGesture
        }

        val anchor = second.position
        var dragging = false
        while (true) {
            // Initial pass: claim the moves before the picture's transformable sees them.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } > 1) return@awaitEachGesture // pinch took over
            val change = event.changes.firstOrNull { it.id == second.id } ?: return@awaitEachGesture

            if (change.changedToUpIgnoreConsumed()) {
                if (!dragging) onDoubleTap(anchor)
                change.consume()
                return@awaitEachGesture
            }
            if (!dragging && (change.position - anchor).getDistance() > slop) {
                dragging = true
                onDragZoomStart()
            }
            if (dragging) {
                val dy = change.position.y - change.previousPosition.y
                if (dy != 0f) onDragZoom(anchor, exp(dy * DRAG_ZOOM_PER_PIXEL))
                change.consume()
            }
        }
    }
}
