package org.nsh07.wikireader.ui.shimmer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun AnimatedShimmer(content: @Composable (Modifier) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val shimmerColors = remember(
        colorScheme.surfaceContainer,
        colorScheme.surfaceContainerHighest
    ) {
        listOf(
            colorScheme.surfaceContainer,
            colorScheme.surfaceContainerHighest,
            colorScheme.surfaceContainer
        )
    }

    val transition = rememberInfiniteTransition()
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 5000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )

    // Build the gradient while drawing, not while composing. The old version created a new
    // Brush on every animation frame, which recomposed every shimmer placeholder continuously.
    val shimmerModifier = remember(shimmerColors) {
        Modifier.drawBehind {
            val x = translateAnimation.value
            val sweep = size.maxDimension
            drawRect(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(x - sweep, x - sweep),
                    end = Offset(x, x)
                )
            )
        }
    }
    content(shimmerModifier)
}
