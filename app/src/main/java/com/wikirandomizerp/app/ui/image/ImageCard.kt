package com.wikirandomizerp.app.ui.image

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wikirandomizerp.app.data.WikiPhoto
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.cardShape

/**
 * Composable for displaying a Wikipedia image with its associated text
 *
 * Displays an [androidx.compose.foundation.Image] composable fetched from a URI with a
 * [androidx.compose.material3.CircularWavyProgressIndicator] while the image is loading, with the
 * image title and description at the bottom of a card.
 *
 * @param photo A (nullable) WikiPhoto object. The image url and aspect ratio are provided by this
 * object
 */

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SharedTransitionScope.ImageCard(
    photo: WikiPhoto?,
    title: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    showPhoto: Boolean,
    background: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Full-size address saved by "hold to download"; null disables the hold menu. */
    downloadUrl: String? = null
) {
    val context = LocalContext.current
    val menu = rememberImageMenuState()
    val haptic = LocalHapticFeedback.current

    val contentScale = ContentScale.Crop
    val displaySource = remember(photo?.source) {
        ImagePrefetcher.standardThumbnailUrl(photo?.source)
    }
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(displaySource)
            .diskCacheKey(ImageCacheKeys.body(displaySource))
            .build(),
        contentScale = contentScale,
    )
    val painterState by painter.state.collectAsStateWithLifecycle()

    if (photo != null && showPhoto) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
            shape = cardShape,
            modifier = modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 512.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (downloadUrl == null) null else {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu.open = true
                        }
                    }
                )
        ) {
            if (downloadUrl != null) ImageDownloadMenu(menu, downloadUrl, title)
            PageImage(
                photo = photo,
                photoDesc = title,
                painter = painter,
                painterState = painterState,
                contentScale = contentScale,
                background = background,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(photo.source),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .fillMaxWidth()
                    .clip(cardShape)
            )
        }
    }
}