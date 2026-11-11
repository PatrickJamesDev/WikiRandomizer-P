package com.wikirandomizerp.app.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun FeedImage(
    source: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    width: Int? = null,
    height: Int? = null,
    background: Boolean,
    colorFilter: ColorFilter? = null,
    contentScale: ContentScale = ContentScale.Crop,
    /** Disable the crossfade: many pictures animating at once costs frames while scrolling. */
    lightPlaceholder: Boolean = false,
    /**
     * True for list covers (History / Saved / tabs). Their pictures are stored under the pinned
     * thumbnail cache key, so the article retention policy never evicts them.
     */
    pinnedThumbnail: Boolean = false
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    // Old rows may still point at a non-standard thumbnail width that Wikimedia rate-limits.
    val displaySource = remember(source, pinnedThumbnail) {
        if (pinnedThumbnail) ImagePrefetcher.smallThumbnailUrl(source)
        else ImagePrefetcher.standardThumbnailUrl(source)
    }
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(displaySource)
            .diskCacheKey(
                if (pinnedThumbnail) ImageCacheKeys.thumbnail(displaySource)
                else ImageCacheKeys.body(displaySource)
            )
            // Crossfades add a second animated layer per picture; list covers and article bodies
            // appear often enough that the fade is not worth the extra frames.
            .crossfade(!pinnedThumbnail && !lightPlaceholder)
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
            // A flat tinted block is both cheaper than an animated indicator (which redraws every
            // frame) and a better height placeholder while a cached picture is decoded.
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(displayRatio)
                    .background(placeholderColor)
            )
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