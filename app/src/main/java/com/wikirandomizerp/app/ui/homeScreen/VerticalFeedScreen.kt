package com.wikirandomizerp.app.ui.homeScreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.R.string
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeSubscreen
import com.wikirandomizerp.app.ui.image.FeedImage

private data class VerticalFeedCard(
    val title: String,
    val lang: String?,
    val description: String?,
    val extract: String?,
    val image: String?
)

@Composable
fun VerticalFeedScreen(
    feed: HomeSubscreen.Feed?,
    onOpenArticle: (String, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val cards = remember(feed) { buildCards(feed) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(modifier = modifier.fillMaxSize().background(colorScheme.surface)) {
        if (cards.isEmpty()) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(32.dp)
            ) {
                Text(stringResource(string.verticalFeedEmpty), style = typography.bodyLarge)
            }
        } else {
            val pagerState = rememberPagerState { Int.MAX_VALUE }
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val card = cards[page % cards.size]
                VerticalCard(
                    card = card,
                    onOpen = { onOpenArticle(card.title, card.lang) }
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(insets)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(string.verticalFeed),
                style = typography.titleMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun VerticalCard(card: VerticalFeedCard, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onOpen)
    ) {
        card.image?.let { source ->
            FeedImage(
                source = source,
                description = card.title,
                background = false,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            Text(
                text = card.title,
                style = typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            card.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            card.extract?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(string.verticalFeedTapToRead),
                style = typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun buildCards(feed: HomeSubscreen.Feed?): List<VerticalFeedCard> {
    if (feed == null) return emptyList()
    val cards = mutableListOf<VerticalFeedCard>()
    feed.tfa?.let { article ->
        val title = article.titles?.normalized.orEmpty()
        if (title.isNotBlank()) {
            cards += VerticalFeedCard(
                title = title,
                lang = article.lang ?: feed.currentLang,
                description = article.description,
                extract = article.extract,
                image = article.originalImage?.source ?: article.thumbnail?.source
            )
        }
    }
    feed.mostReadArticles.orEmpty().forEach { article ->
        val title = article.titles?.normalized.orEmpty()
        if (title.isNotBlank()) {
            cards += VerticalFeedCard(
                title = title,
                lang = article.lang ?: feed.currentLang,
                description = article.description,
                extract = article.extract,
                image = article.originalImage?.source ?: article.thumbnail?.source
            )
        }
    }
    return cards.distinctBy { "${it.lang}:${it.title}" }
}
