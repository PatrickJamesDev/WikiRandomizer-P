package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.wikirandomizerp.app.ui.image.ImageDownloadMenu
import com.wikirandomizerp.app.ui.image.rememberImageMenuState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.wikirandomizerp.app.parser.toWikitextAnnotatedStringSafe
import com.wikirandomizerp.app.ui.image.FeedImage
import com.wikirandomizerp.app.ui.image.ImagePrefetcher
import com.wikirandomizerp.app.ui.theme.ColorConstants.colorMatrixInvert

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SharedTransitionScope.ImageWithCaption(
    text: String,
    fontSize: Int,
    lang: String,
    darkTheme: Boolean,
    background: Boolean,
    checkFirstImage: Boolean,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String) -> Unit = {},
    onClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    pageImageUri: String? = null,
    showCaption: Boolean = true,
    shape: CornerBasedShape = shapes.large
) {
    val fileName = remember(text) {
        text.substringAfter(':').substringBefore('|').substringBefore("]]").trim()
    }
    val uriLow = remember(text) { ImagePrefetcher.fileUrl(lang, fileName) }
    val uriHigh = remember(text) {
        // 1920 px is a standard Wikimedia thumbnail step and is pre-cached by ImagePrefetcher,
        // so tapping a picture opens it instantly. The old code requested the un-cached original,
        // which always meant a fresh multi-megabyte download first.
        ImagePrefetcher.fileUrl(lang, fileName, width = 1920)
    }

    val pageImageName = remember(pageImageUri) { imageNameFromUrl(pageImageUri) }
    val normalizedFileName = remember(fileName) { normalizeImageName(fileName) }

    // `checkFirstImage` means "this section is under the lead image". The API leads with one
    // picture; when the article body shows the same file again we would draw it twice. The old
    // condition was inverted: with a lead image present it hid every intro picture whose file was
    // *different* (taking its caption with it) and rendered the duplicate instead. Skip only the
    // duplicate, and keep its caption below the lead image.
    val isLeadDuplicate = checkFirstImage && pageImageName != null &&
            pageImageName.equals(normalizedFileName, ignoreCase = true)

    val description = remember(text) {
        // The caption is everything after the file name, not just the first parameter:
        // captions routinely contain links and templates with their own `|`.
        text.substringAfter('|', "")
            .substringBeforeLast("]]")
            .substringBeforeLast("|invert")
            .trim()
    }
    val invert = remember(text) { text.contains("|invert") }
    val captionColorScheme = colorScheme
    val captionTypography = typography

    if (!isLeadDuplicate) {
        // Tap opens the full-screen gallery; holding the picture offers to download it right away.
        val menu = rememberImageMenuState()
        val haptic = LocalHapticFeedback.current
        Box(modifier = modifier) {
            FeedImage(
                source = uriLow,
                description = description,
                lightPlaceholder = true,
                colorFilter = if (invert && darkTheme && !background) // Invert colors in dark theme
                    ColorFilter.colorMatrix(ColorMatrix(colorMatrixInvert))
                else null,
                background = background,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(uriHigh),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    )
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .clip(shape)
                    .widthIn(max = 512.dp)
                    .combinedClickable(
                        onClick = { onClick(uriHigh, description) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu.open = true
                        }
                    )
            )
            ImageDownloadMenu(menu, uriHigh, description.ifBlank { null })
        }
    }
    if (showCaption && description.isNotBlank()) {
        val caption = remember(description, fontSize, captionColorScheme, captionTypography) {
            description.toWikitextAnnotatedStringSafe(
                colorScheme = captionColorScheme,
                fontSize = fontSize - 2,
                loadPage = onLinkClick,
                typography = captionTypography,
                showRef = {}
            )
        }
        val captionDisplay = remember(caption, captionColorScheme.primary) {
            caption.toTappableArticleLinks(captionColorScheme.primary)
        }
        val captionLayout = remember(captionDisplay) {
            androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
        }
        Text(
            text = captionDisplay,
            fontSize = (fontSize - 2).sp,
            lineHeight = (24 * ((fontSize - 2) / 16.0)).toInt().sp,
            textAlign = TextAlign.Center,
            color = colorScheme.onSurfaceVariant,
            onTextLayout = { captionLayout.value = it },
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(description),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current
                )
                .padding(horizontal = 16.dp)
                // The caption of a picture that is already shown as the lead image sits directly
                // under it; a normal picture caption sits under its own picture.
                .padding(top = if (isLeadDuplicate) 4.dp else 8.dp, bottom = 12.dp)
                .articleLinkGestures(
                    text = captionDisplay,
                    layoutProvider = { captionLayout.value },
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick
                )
        )
    }
}

/** File name of the last path segment of an image URL ("960px-Pea.jpg" -> "Pea.jpg"). */
internal fun imageNameFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return normalizeImageName(url.substringBefore('?').substringAfterLast('/'))
}

/**
 * Canonical file name for comparing the API lead image with pictures emitted from HTML.
 * Wikimedia thumbnail URLs can carry prefixes such as `960px-`, `lossy-page1-` or a format
 * suffix such as `.svg.png`; the body uses the original `File:` title. Without stripping these
 * the same picture was not recognised and the lead image appeared twice.
 */
internal fun normalizeImageName(name: String?): String? {
    if (name.isNullOrBlank()) return null
    var value = name.substringBefore('?').substringAfterLast('/').trim()
    value = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    value = value.replace(Regex("^(?:lossy-)?page\\d+-", RegexOption.IGNORE_CASE), "")
    value = value.replace(Regex("^\\d+px-"), "")
    value = value.replace(Regex("^(?:file|image):", RegexOption.IGNORE_CASE), "")
    value = value.replace(Regex("(?i)\\.(svg)\\.(?:png|jpe?g|webp)$"), ".$1")
    value = value.replace(' ', '_')
    return value.ifBlank { null }
}
