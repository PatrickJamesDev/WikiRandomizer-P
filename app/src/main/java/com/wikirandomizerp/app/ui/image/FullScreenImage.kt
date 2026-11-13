package com.wikirandomizerp.app.ui.image

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.data.WikiPhoto

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun FullScreenImage(
    photo: WikiPhoto?,
    photoDesc: String?,
    sharedScope: SharedTransitionScope,
    title: String,
    background: Boolean,
    modifier: Modifier = Modifier,
    link: String? = null,
    articleTitle: String? = null,
    articleLang: String? = null,
    onBack: () -> Unit,
) {
    var currentLightStatusBars = true
    val view = LocalView.current
    val context = LocalContext.current
    val motionScheme = motionScheme
    val window = remember { view.context.requireActivity().window }
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }

    val contentScale = ContentScale.Crop
    val displaySource = remember(photo?.source) {
        ImagePrefetcher.fullScreenUrl(photo?.source)
    }
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(displaySource)
            .diskCacheKey(ImageCacheKeys.body(displaySource))
            .crossfade(false)
            .build(),
        contentScale = contentScale,
    )

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

    var showTopBar by remember { mutableStateOf(true) }

    LaunchedEffect(showTopBar) {
        if (showTopBar) {
            insetsController.apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        } else {
            insetsController.apply {
                hide(WindowInsetsCompat.Type.statusBars())
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    val download = rememberImageDownload(
        url = photo?.source,
        description = photoDesc ?: title,
        articleTitle = articleTitle,
        articleLang = articleLang,
        thumbnailUrl = photo?.source
    )
    val saveToGallery = rememberSaveImage(
        url = photo?.source,
        description = photoDesc ?: title,
        articleTitle = articleTitle,
        articleLang = articleLang,
        thumbnailUrl = photo?.source
    )
    var showImageActions by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val longPress = remember(download, saveToGallery, haptic) {
        {
            if (download != null || saveToGallery != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showImageActions = true
            }
        }
    }
    with(sharedScope) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    showTopBar,
                    enter = slideInVertically(motionScheme.defaultSpatialSpec()) { -it },
                    exit = slideOutVertically(motionScheme.defaultSpatialSpec()) { -it }
                ) {
                    FullScreenImageTopBar(
                        description = title,
                        link = link,
                        onDownload = download,
                        onSaveToGallery = saveToGallery,
                        onBack = onBack
                    )
                }
            },
            containerColor = Color.Black,
            modifier = modifier
        ) { _ ->
            val zoom = remember { ImageZoomState() }
            val borderRadius by animateDpAsState(
                if (showTopBar) 16.dp else 0.dp,
                animationSpec = motionScheme.defaultSpatialSpec()
            )

            // Pinch + pan. Taps, double taps and "tap then drag" zooming are handled by
            // imageZoomGestures on the surrounding box (see ZoomGestures.kt).
            val state = rememberTransformableState { scaleChange, offsetChange, _ ->
                zoom.animation?.cancel()
                val newScale = (zoom.scale * scaleChange).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
                zoom.scale = newScale
                zoom.offset = zoom.clampedOffset(newScale, zoom.offset + offsetChange.times(newScale))
                showTopBar = newScale <= 1.1f
            }
            // Resolve the theme animation spec here: defaultSpatialSpec() is @Composable and
            // cannot be called from inside the pointerInput gesture lambdas.
            val doubleTapSpec = motionScheme.defaultSpatialSpec<Float>()

            val coroutineScope = rememberCoroutineScope()
            val painterState by painter.state.collectAsStateWithLifecycle()

            if (photo != null && photoDesc != null)
                Box(contentAlignment = Alignment.Center) {
                    AnimatedVisibility(
                        painterState is AsyncImagePainter.State.Success && showTopBar && Build.VERSION.SDK_INT >= 31,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = contentScale,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(32.dp)
                                .alpha(0.3f)
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { zoom.areaSize = it }
                            .imageZoomGestures(
                                zoom = zoom,
                                scope = coroutineScope,
                                doubleTapSpec = doubleTapSpec,
                                onTap = { showTopBar = !showTopBar },
                                onScaleChanged = { showTopBar = it <= 1.1f },
                                onLongPress = longPress
                            )
                    ) {
                        PageImage(
                            photo = photo,
                            photoDesc = photoDesc,
                            painter = painter,
                            painterState = painterState,
                            contentScale = contentScale,
                            background = background,
                            loadingColor = Color.Transparent,
                            modifier = Modifier
                                .onSizeChanged { zoom.size = it }
                                .graphicsLayer {
                                    scaleX = zoom.scale
                                    scaleY = zoom.scale
                                    translationX = zoom.offset.x
                                    translationY = zoom.offset.y
                                }
                                .transformable(state = state)
                                .fillMaxSize()
                                .padding(borderRadius.coerceAtLeast(0.dp))
                                .clip(RoundedCornerShape(borderRadius.coerceAtLeast(0.dp)))
                        )
                    }
                }
        }
        ImageActionDialog(
            visible = showImageActions,
            onDismiss = { showImageActions = false },
            onDownload = download,
            onSaveToGallery = saveToGallery
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun FullScreenArticleImage(
    uri: String,
    description: String,
    sharedScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
    link: String? = null,
    background: Boolean,
    articleTitle: String? = null,
    articleLang: String? = null,
    onBack: () -> Unit,
) {
    var currentLightStatusBars = true
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val window = remember { view.context.requireActivity().window }
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }

    val contentScale = ContentScale.Fit

    val displaySource = remember(uri) {
        ImagePrefetcher.fullScreenUrl(uri)
    }
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(displaySource)
            .diskCacheKey(ImageCacheKeys.body(displaySource))
            .crossfade(false)
            .build(),
        contentScale = contentScale,
    )

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

    var showTopBar by remember { mutableStateOf(true) }

    LaunchedEffect(showTopBar) {
        if (showTopBar) {
            insetsController.apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        } else {
            insetsController.apply {
                hide(WindowInsetsCompat.Type.statusBars())
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    val download = rememberImageDownload(
        url = uri,
        description = description,
        articleTitle = articleTitle,
        articleLang = articleLang,
        thumbnailUrl = uri
    )
    val saveToGallery = rememberSaveImage(
        url = uri,
        description = description,
        articleTitle = articleTitle,
        articleLang = articleLang,
        thumbnailUrl = uri
    )
    var showImageActions by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val longPress = remember(download, saveToGallery, haptic) {
        {
            if (download != null || saveToGallery != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showImageActions = true
            }
        }
    }
    with(sharedScope) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    showTopBar,
                    enter = slideInVertically(motionScheme.defaultSpatialSpec()) { -it },
                    exit = slideOutVertically(motionScheme.defaultSpatialSpec()) { -it }
                ) {
                    FullScreenImageTopBar(
                        description = description,
                        link = link,
                        onDownload = download,
                        onSaveToGallery = saveToGallery,
                        onBack = onBack
                    )
                }
            },
            containerColor = Color.Black,
            modifier = modifier
        ) { _ ->
            val zoom = remember { ImageZoomState() }
            val borderRadius by animateDpAsState(
                if (showTopBar) 16.dp else 0.dp,
                animationSpec = motionScheme.defaultSpatialSpec()
            )

            val state = rememberTransformableState { scaleChange, offsetChange, _ ->
                zoom.animation?.cancel()
                val newScale = (zoom.scale * scaleChange).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
                zoom.scale = newScale
                zoom.offset = zoom.clampedOffset(newScale, zoom.offset + offsetChange.times(newScale))
                showTopBar = newScale <= 1.1f
            }
            // Same reason as above: the spec must be read in composable scope.
            val doubleTapSpec = motionScheme.defaultSpatialSpec<Float>()

            val painterState by painter.state.collectAsStateWithLifecycle()

            Box(contentAlignment = Alignment.Center) {
                AnimatedVisibility(
                    painterState is AsyncImagePainter.State.Success && showTopBar && Build.VERSION.SDK_INT >= 31,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(32.dp)
                            .alpha(0.3f)
                    )
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
                            onTap = { showTopBar = !showTopBar },
                            onScaleChanged = { showTopBar = it <= 1.1f },
                            onLongPress = longPress
                        )
                ) {
                    PageImage(
                        uri = uri,
                        description = description,
                        painter = painter,
                        painterState = painterState,
                        contentScale = contentScale,
                        background = background,
                        loadingColor = Color.Transparent,
                        modifier = Modifier
                            .onSizeChanged { zoom.size = it }
                            .graphicsLayer {
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = zoom.offset.x
                                translationY = zoom.offset.y
                            }
                            .transformable(state = state)
                            .padding(borderRadius.coerceAtLeast(0.dp))
                            .clip(RoundedCornerShape(borderRadius.coerceAtLeast(0.dp)))
                    )
                }
            }
        }
        ImageActionDialog(
            visible = showImageActions,
            onDismiss = { showImageActions = false },
            onDownload = download,
            onSaveToGallery = saveToGallery
        )
    }
}

/** The hosting Activity, even when the view's context is wrapped (themed / locale-wrapped contexts). */
internal fun Context.requireActivity(): Activity {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    throw IllegalStateException("No Activity found in context chain")
}
