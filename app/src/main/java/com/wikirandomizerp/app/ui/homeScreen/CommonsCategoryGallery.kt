package com.wikirandomizerp.app.ui.homeScreen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.wikirandomizerp.app.ui.image.ImageDownloadMenu
import com.wikirandomizerp.app.ui.image.rememberImageMenuState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.CommonsCategoryGallery
import com.wikirandomizerp.app.data.CommonsGalleryImage
import com.wikirandomizerp.app.ui.image.ImageCacheKeys

/**
 * How the article page asks for the Commons gallery of the article it shows. Provided once by the
 * activity (backed by the view model) so it does not have to be threaded through every screen.
 */
val LocalCommonsGalleryLoader = staticCompositionLocalOf<suspend (String, String) -> CommonsCategoryGallery?> {
    { _, _ -> null }
}

/**
 * Loads the Commons category gallery of the article [title] in the background. Returns null while
 * loading and when the article has no Commons category with pictures, so the page simply shows no
 * gallery in those cases.
 */
@Composable
fun rememberCommonsGallery(title: String, lang: String, enabled: Boolean): CommonsCategoryGallery? {
    val loader = LocalCommonsGalleryLoader.current
    var gallery by remember(title, lang) { mutableStateOf<CommonsCategoryGallery?>(null) }
    LaunchedEffect(title, lang, enabled) {
        if (enabled && gallery == null) gallery = runCatching { loader(title, lang) }.getOrNull()
    }
    return if (enabled) gallery else null
}

/**
 * A row of pictures from the Commons category named like the article ("Navel orange" shows the
 * "Navel oranges" category), shown at the end of the article, before the references.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommonsCategoryGalleryStrip(
    gallery: CommonsCategoryGallery,
    onImageClick: (viewUrl: String, description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.commons_gallery_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = gallery.category,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(gallery.images, key = { it.thumbnailUrl }) { image ->
                GalleryThumb(image, onImageClick)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(gallery.categoryUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) {
                Text(stringResource(R.string.commons_gallery_open))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryThumb(
    image: CommonsGalleryImage,
    onImageClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val menu = rememberImageMenuState()
    val haptic = LocalHapticFeedback.current
    // Keep every picture at the same height; its width follows the picture's proportions.
    val height = 160.dp
    val ratio = if (image.height > 0) (image.width.toFloat() / image.height).coerceIn(0.6f, 2f) else 1.33f
    Box(
        modifier = Modifier
            .height(height)
            .width(height * ratio)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onImageClick(image.viewUrl, image.title) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu.open = true
                }
            )
    ) {
        ImageDownloadMenu(menu, image.viewUrl, image.title)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(image.thumbnailUrl)
                .diskCacheKey(ImageCacheKeys.body(image.thumbnailUrl))
                .crossfade(true)
                .build(),
            contentDescription = image.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(height)
                .width(height * ratio)
        )
    }
}
