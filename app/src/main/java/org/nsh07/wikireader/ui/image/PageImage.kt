package org.nsh07.wikireader.ui.image

import org.nsh07.wikireader.ui.theme.isDark
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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.AsyncImagePainter
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.WikiPhoto

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PageImage(
    photo: WikiPhoto,
    photoDesc: String,
    painter: AsyncImagePainter,
    painterState: AsyncImagePainter.State,
    background: Boolean,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        safeAspectRatio(photo.width, photo.height)
                    )
            ) {
                CircularWavyProgressIndicator()
            }
        }

        else -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        safeAspectRatio(photo.width, photo.height)
                    )
            ) {
                Icon(
                    painterResource(R.drawable.error),
                    contentDescription = stringResource(R.string.errorLoadingImage),
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(64.dp),
                    tint = colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PageImage(
    uri: String,
    description: String,
    background: Boolean,
    painter: AsyncImagePainter,
    painterState: AsyncImagePainter.State,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
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
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                CircularWavyProgressIndicator()
            }
        }

        else -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Icon(
                    painterResource(R.drawable.error),
                    contentDescription = stringResource(R.string.errorLoadingImage),
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(64.dp),
                    tint = colorScheme.error
                )
            }
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
