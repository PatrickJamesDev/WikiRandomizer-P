package com.wikirandomizerp.app.ui.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.parser.toWikitextAnnotatedStringSafe

/**
 * Full-screen viewer for ALL pictures of an article as one gallery: swipe left/right from the
 * first picture to the last without leaving the viewer. Shows the caption of the current picture
 * at the bottom, lets the picture be pinch/double-tap zoomed, and fills the screen with small
 * pictures (scaled up, aspect ratio untouched).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.FullScreenGallery(
    items: List<ArticleGalleryItem>,
    startIndex: Int,
    background: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val view = LocalView.current
    val context = LocalContext.current
    val window = remember { view.context.requireActivity().window }
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }
    var currentLightStatusBars = true

    DisposableEffect(null) {
        if (!view.isInEditMode) {
            currentLightStatusBars = insetsController.isAppearanceLightStatusBars
            insetsController.isAppearanceLightStatusBars = false
        }
        onDispose {
            insetsController.apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
            insetsController.isAppearanceLightStatusBars = currentLightStatusBars
        }
    }

    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var barsRequested by remember { mutableStateOf(true) }
    // The zoom of the picture on screen. While it is zoomed in, a horizontal drag pans the picture;
    // otherwise it turns the page.
    var currentScale by remember { mutableFloatStateOf(1f) }
    val showBars = barsRequested

    LaunchedEffect(showBars) {
        if (showBars) {
            insetsController.apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        } else {
            insetsController.apply {
                hide(WindowInsetsCompat.Type.statusBars())
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // A new page always starts un-zoomed.
    LaunchedEffect(pagerState.settledPage) { currentScale = 1f }

    // Load the neighbours of the picture on screen so the next swipe lands on a finished picture.
    LaunchedEffect(pagerState.settledPage, items) {
        val page = pagerState.settledPage
        val near = ((page - 1)..(page + 2)).filter { it in items.indices && it != page }
            .map { items[it].uri }
        ImagePrefetcher.warmFullScreen(context, near)
    }

    val item = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
    val download = rememberImageDownload(
        url = item.uri,
        description = item.caption.ifBlank { null },
        articleTitle = item.articleTitle,
        articleLang = item.articleLang,
        pageUrl = item.pageUrl,
        thumbnailUrl = item.thumbnailUrl
    )
    val haptic = LocalHapticFeedback.current
    val longPress = remember(download, haptic) {
        {
            if (download != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                download()
            }
        }
    }
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val captionText = remember(item.caption) {
        if (item.caption.isBlank()) ""
        else item.caption.toWikitextAnnotatedStringSafe(
            colorScheme = colorScheme,
            typography = typography,
            loadPage = {},
            fontSize = 1,
            showRef = {}
        ).toString().trim()
    }
    val counter = stringResource(R.string.imageCounter, pagerState.currentPage + 1, items.size)
    val doubleTapSpec = motionScheme.defaultSpatialSpec<Float>()

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = currentScale <= 1.01f,
            pageSpacing = 12.dp,
            key = { page -> "${page}#${items[page].uri}" },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            GalleryImagePage(
                item = items[page],
                isCurrent = page == pagerState.currentPage,
                background = background,
                doubleTapSpec = doubleTapSpec,
                onTap = { barsRequested = !barsRequested },
                onLongPress = longPress,
                onScaleChanged = { scale ->
                    if (page == pagerState.currentPage) {
                        if (scale > 1.1f && currentScale <= 1.1f) barsRequested = false
                        currentScale = scale
                    }
                }
            )
        }

        AnimatedVisibility(
            showBars,
            enter = slideInVertically(motionScheme.defaultSpatialSpec()) { -it },
            exit = slideOutVertically(motionScheme.defaultSpatialSpec()) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            FullScreenImageTopBar(
                description = counter,
                link = item.pageUrl,
                onDownload = download,
                onBack = onBack
            )
        }

        AnimatedVisibility(
            showBars && captionText.isNotBlank(),
            enter = fadeIn() + slideInVertically(motionScheme.defaultSpatialSpec()) { it },
            exit = fadeOut() + slideOutVertically(motionScheme.defaultSpatialSpec()) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
                    )
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 16.dp)
            ) {
                Text(
                    text = captionText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GalleryImagePage(
    item: ArticleGalleryItem,
    isCurrent: Boolean,
    background: Boolean,
    doubleTapSpec: androidx.compose.animation.core.AnimationSpec<Float>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onScaleChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zoom = remember { ImageZoomState() }

    // Swiping away from a zoomed picture leaves it un-zoomed for next time.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            zoom.animation?.cancel()
            zoom.scale = 1f
            zoom.offset = Offset.Zero
        }
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(item.uri)
            .diskCacheKey(ImageCacheKeys.body(item.uri))
            // No crossfade when the viewer opens: the bitmap either comes straight from the
            // preloaded disk cache or shows the progress indicator on black, never a white frame.
            .crossfade(false)
            .build(),
        contentScale = ContentScale.Fit
    )
    val painterState by painter.state.collectAsStateWithLifecycle()

    val transformState = rememberTransformableState { scaleChange, offsetChange, _ ->
        zoom.animation?.cancel()
        val newScale = (zoom.scale * scaleChange).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
        zoom.scale = newScale
        zoom.offset = zoom.clampedOffset(newScale, zoom.offset + offsetChange.times(newScale))
        onScaleChanged(newScale)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { zoom.areaSize = it }
            .imageZoomGestures(
                zoom = zoom,
                scope = scope,
                doubleTapSpec = doubleTapSpec,
                onTap = onTap,
                onScaleChanged = onScaleChanged,
                onLongPress = onLongPress
            )
    ) {
        when (painterState) {
            is AsyncImagePainter.State.Success -> {
                // Size the picture to the LARGEST box of its own proportions that fits the screen.
                // A small picture is therefore scaled up to use the screen (aspect ratio untouched)
                // instead of sitting at its pixel size in the middle of a black area.
                val intrinsic = painter.intrinsicSize
                val ratio = safeAspectRatio(intrinsic.width.toInt(), intrinsic.height.toInt())
                Image(
                    painter = painter,
                    contentDescription = item.caption.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .aspectRatio(ratio)
                        .onSizeChanged { zoom.size = it }
                        .graphicsLayer {
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offset.x
                            translationY = zoom.offset.y
                        }
                        // canPan: at 1x a one-finger drag belongs to the pager (page turn).
                        .transformable(state = transformState, canPan = { zoom.scale > 1.01f })
                        .background(
                            if (imageNeedsWhiteBackground(item.uri, background)) Color.White
                            else Color.Transparent
                        )
                )
            }

            is AsyncImagePainter.State.Error -> {
                Icon(
                    painterResource(R.drawable.error),
                    contentDescription = stringResource(R.string.errorLoadingImage),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
            }

            else -> CircularWavyProgressIndicator()
        }
    }
}
