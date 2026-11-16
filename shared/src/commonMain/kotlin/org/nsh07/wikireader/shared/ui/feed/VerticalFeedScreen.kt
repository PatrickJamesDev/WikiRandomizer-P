package org.nsh07.wikireader.shared.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wikirandomizerp.app.data.FeedApiResponse
import com.wikirandomizerp.app.data.FeedTitles

private data class VerticalCard(
    val title: String,
    val language: String,
    val description: String?,
    val extract: String?,
    val image: String?
)

@Composable
fun VerticalFeedScreen(
    feed: FeedApiResponse,
    language: String,
    onOpenArticle: (lang: String, title: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = remember(feed, language) { buildCards(feed, language) }
    if (cards.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No swipe cards in today's feed.")
        }
        return
    }

    val pagerState = rememberPagerState { cards.size }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val card = cards[page]
            VerticalCardContent(card) { onOpenArticle(card.language, card.title) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = Color.White)
            }
            Text(
                "Swipe feed",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun VerticalCardContent(card: VerticalCard, onOpen: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().clickable(onClick = onOpen)) {
        card.image?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
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
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                card.title,
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            card.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stripHtml(it),
                    color = Color.White.copy(alpha = 0.88f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            card.extract?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stripHtml(it),
                    color = Color.White.copy(alpha = 0.78f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap to read",
                color = Color.White.copy(alpha = 0.7f),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun buildCards(feed: FeedApiResponse, language: String): List<VerticalCard> {
    val cards = mutableListOf<VerticalCard>()
    feed.tfa?.let { article ->
        val title = feedTitle(article.titles)
        if (title.isNotBlank()) {
            cards += VerticalCard(
                title = title,
                language = article.lang ?: language,
                description = article.description,
                extract = article.extract,
                image = article.originalImage?.source ?: article.thumbnail?.source
            )
        }
    }
    feed.mostRead?.articles.orEmpty().forEach { article ->
        val title = feedTitle(article.titles)
        if (title.isNotBlank()) {
            cards += VerticalCard(
                title = title,
                language = article.lang ?: language,
                description = article.description,
                extract = article.extract,
                image = article.originalImage?.source ?: article.thumbnail?.source
            )
        }
    }
    return cards.distinctBy { "${it.language.lowercase()}|${it.title.lowercase()}" }
}

private fun feedTitle(titles: FeedTitles?): String =
    titles?.canonical ?: titles?.normalized.orEmpty()

private fun stripHtml(value: String): String =
    value
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
