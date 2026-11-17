package com.wikirandomizerp.app.ui.homeScreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.R.string
import com.wikirandomizerp.app.ui.image.ImageCacheKeys
import com.wikirandomizerp.app.ui.image.ImagePrefetcher
import kotlinx.coroutines.CancellationException

/** One article of the vertical feed: cover picture plus the start of its introduction. */
data class VerticalFeedCard(
    val title: String,
    val lang: String?,
    val description: String?,
    val extract: String?,
    val image: String?
)

/** Cuts [text] after its last complete sentence when it was truncated in the middle of one. */
fun trimToSentence(text: String): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.length < 200) return clean
    val end = clean.indexOfLast { it == '.' || it == '!' || it == '?' }
    return if (end >= 160) clean.substring(0, end + 1) else clean
}

private const val FEED_BATCH = 6
// The very first request is small so the first card appears quickly; the next batch (full size)
// starts right after, long before the reader swipes that far.
private const val FEED_FIRST_BATCH = 2
private const val FEED_PRELOAD_AHEAD = 3

@Composable
fun VerticalFeedScreen(
    loadMore: suspend (Int) -> List<VerticalFeedCard>,
    onOpenArticle: (String, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val cards = remember { mutableStateListOf<VerticalFeedCard>() }
    var loading by remember { mutableStateOf(false) }
    var failedEmpty by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState { cards.size }

    // Infinite feed: keep a few cards ahead of the reader.
    val needMore = cards.isEmpty() || pagerState.currentPage >= cards.size - FEED_PRELOAD_AHEAD
    LaunchedEffect(needMore, cards.size) {
        if (!needMore || loading) return@LaunchedEffect
        loading = true
        try {
            val fresh = loadMore(if (cards.isEmpty()) FEED_FIRST_BATCH else FEED_BATCH)
            val known = cards.map { "${it.lang}:${it.title}" }.toSet()
            cards.addAll(fresh.filter { "${it.lang}:${it.title}" !in known })
            failedEmpty = cards.isEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failedEmpty = cards.isEmpty()
        } finally {
            loading = false
        }
    }

    // Load the pictures of the next cards before the reader swipes to them.
    LaunchedEffect(pagerState.currentPage, cards.size) {
        val from = pagerState.currentPage
        val urls = cards.drop(from).take(FEED_PRELOAD_AHEAD + 1).mapNotNull { it.image }
        if (urls.isNotEmpty()) ImagePrefetcher.warmFullScreen(context, urls)
    }

    Box(modifier = modifier.fillMaxSize().background(colorScheme.surface)) {
        if (cards.isEmpty()) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(32.dp)
            ) {
                if (loading || !failedEmpty) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(string.verticalFeedEmpty), style = typography.bodyLarge)
                }
            }
        } else {
            VerticalPager(
                state = pagerState,
                key = { index -> "${cards[index].lang}:${cards[index].title}" },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val card = cards[page]
                VerticalCard(card = card, onOpen = { onOpenArticle(card.title, card.lang) })
            }
        }
    }
}

@Composable
private fun VerticalCard(card: VerticalFeedCard, onOpen: () -> Unit) {
    val context = LocalContext.current
    // The picture is shown whole ("fit") in the upper part of the card instead of being cropped
    // over the whole screen, and the text sits below it on a plain surface.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
    ) {
        val image = card.image
        if (image != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.46f)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            ) {
                val displaySource = remember(image) { ImagePrefetcher.standardThumbnailUrl(image) ?: image }
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(displaySource)
                        .diskCacheKey(ImageCacheKeys.body(displaySource))
                        .crossfade(true)
                        .build(),
                    contentScale = ContentScale.Fit
                )
                val state by painter.state.collectAsStateWithLifecycle()
                if (state is AsyncImagePainter.State.Success) {
                    Image(
                        painter = painter,
                        contentDescription = card.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                    )
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (image != null) 0.54f else 1f)
                .then(if (image == null) Modifier.statusBarsPadding() else Modifier)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp)
        ) {
            Text(
                text = card.title,
                style = typography.headlineMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            card.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = typography.titleSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            card.extract?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(string.verticalFeedTapToRead),
                style = typography.labelLarge,
                color = colorScheme.primary
            )
        }
    }
}
