package org.nsh07.wikireader.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedImage(
    source: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    width: Int? = null,
    height: Int? = null,
    loadingIndicator: Boolean,
    background: Boolean,
    colorFilter: ColorFilter? = null,
    contentScale: ContentScale = ContentScale.Crop,
    /** Static grey block while loading instead of an animated indicator (cheaper for long articles). */
    lightPlaceholder: Boolean = false
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            // Old stored addresses can carry a thumbnail width Wikimedia no longer serves.
            .data(ImagePrefetcher.standardThumbnailUrl(source))
            .crossfade(true)
            .build(),
        contentScale = contentScale,
    )

    val painterState by painter.state.collectAsStateWithLifecycle()

    // Height known before the picture loads: from explicit sizes, or from the size lookup / an earlier
    // load (ImageAspectCache). If nothing is known, reserve a landscape box instead of letting the
    // placeholder collapse to zero height and then jump when the bitmap arrives.
    val knownRatio: Float? =
        if (width != null && height != null) safeAspectRatio(width, height)
        else ImageAspectCache.ratioFor(source)
    val displayRatio = knownRatio ?: FALLBACK_ASPECT_RATIO

    when (painterState) {
        is AsyncImagePainter.State.Success -> {
            if (knownRatio == null) {
                val intrinsic = painter.intrinsicSize
                SideEffect { ImageAspectCache.record(source, intrinsic.width, intrinsic.height) }
            }
            Image(
                painter = painter,
                contentDescription = description,
                contentScale = contentScale,
                colorFilter = colorFilter,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(displayRatio)
                    .background(if (imageNeedsWhiteBackground(source, background)) Color.White else Color.Transparent)
            )
        }

        is AsyncImagePainter.State.Loading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(displayRatio)
                    .then(if (lightPlaceholder) Modifier.background(placeholderColor) else Modifier)
            ) {
                if (lightPlaceholder) Unit
                else if (loadingIndicator) LoadingIndicator()
                else CircularWavyProgressIndicator()
            }
        }

        else -> {
            Spacer(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(displayRatio)
            )
        }
    }
}

private const val FALLBACK_ASPECT_RATIO = 16f / 9f