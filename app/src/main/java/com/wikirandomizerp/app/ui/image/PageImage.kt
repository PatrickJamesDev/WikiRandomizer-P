package com.wikirandomizerp.app.ui.image

import com.wikirandomizerp.app.ui.theme.isDark
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.delay
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.WikiPhoto

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PageImage(
    photo: WikiPhoto,
    photoDesc: String,
    painter: AsyncImagePainter,
    painterState: AsyncImagePainter.State,
    background: Boolean,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    /** Backing colour while the bitmap decodes; full-screen viewers pass black to avoid a flash. */
    loadingColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    when (painterState) {
        is AsyncImagePainter.State.Success -> {
            Image(
                painter = painter,
                contentDescription = photoDesc,
                contentScale = contentScale,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(photo.source),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    )
                    .then(modifier)
                    .aspectRatio(safeAspectRatio(photo.width, photo.height))
                    .background(if (imageNeedsWhiteBackground(photo.source, background)) Color.White else Color.Transparent)
            )
        }

        is AsyncImagePainter.State.Loading -> {
            // Static placeholder: an animated indicator per body picture caused visible frame
            // drops while scrolling a long article.
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(safeAspectRatio(photo.width, photo.height))
                    .background(loadingColor)
            )
        }

        is AsyncImagePainter.State.Error -> {
            ImageLoadFailure(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(safeAspectRatio(photo.width, photo.height))
            )
        }

        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(safeAspectRatio(photo.width, photo.height))
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PageImage(
    uri: String,
    description: String,
    background: Boolean,
    painter: AsyncImagePainter,
    painterState: AsyncImagePainter.State,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    loadingColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    when (painterState) {
        is AsyncImagePainter.State.Success -> {
            Image(
                painter = painter,
                contentDescription = description,
                contentScale = contentScale,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(uri),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    )
                    .then(modifier)
                    .background(if (imageNeedsWhiteBackground(uri, background)) Color.White else Color.Transparent)
            )
        }

        is AsyncImagePainter.State.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(loadingColor)
            )
        }

        is AsyncImagePainter.State.Error -> {
            ImageLoadFailure(modifier = modifier.fillMaxWidth())
        }

        else -> {
            Box(modifier = modifier.fillMaxWidth())
        }
    }
}

/**
 * Coil can briefly report an error while a cached bitmap is being restored, then succeed on the
 * next frame. Showing the red warning immediately made cold starts look broken, so keep the
 * neutral placeholder for a moment and only surface a real failure after that.
 */
@Composable
private fun ImageLoadFailure(modifier: Modifier = Modifier) {
    var showError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(700)
        showError = true
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        if (showError) {
            Icon(
                painterResource(R.drawable.error),
                contentDescription = stringResource(R.string.errorLoadingImage),
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(64.dp),
                tint = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Width / height that is always a valid aspect ratio. A 0 or missing dimension used to make
 * Modifier.aspectRatio throw (NaN / Infinity / 0), which crashed the whole app mid-draw.
 */
fun safeAspectRatio(width: Int, height: Int): Float {
    val r = width.toFloat() / height.toFloat()
    return if (r.isFinite() && r > 0f) r else 1.5f
}

/**
 * Transparent images (SVG chemical structures and diagrams, transparent PNGs) are drawn in black
 * and become unreadable on a dark background. In a dark theme they get a white backing; the
 * "image background" setting still forces it for every image.
 */
@Composable
fun imageNeedsWhiteBackground(source: String?, background: Boolean): Boolean {
    if (background) return true
    if (source == null || !MaterialTheme.colorScheme.isDark()) return false
    val ext = source.substringBefore('?').substringAfterLast('.', "").lowercase()
    return ext == "svg" || ext == "png" || ext == "gif" || ext == "webp"
}
