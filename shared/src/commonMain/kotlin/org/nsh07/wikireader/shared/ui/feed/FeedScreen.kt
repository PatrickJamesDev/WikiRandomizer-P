package org.nsh07.wikireader.shared.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wikirandomizerp.app.data.FeedApiImage
import com.wikirandomizerp.app.data.FeedApiNews
import com.wikirandomizerp.app.data.FeedApiOTD
import com.wikirandomizerp.app.data.FeedApiResponse
import com.wikirandomizerp.app.data.FeedApiTFA
import com.wikirandomizerp.app.data.FeedImageSource
import com.wikirandomizerp.app.data.FeedTitles
import com.wikirandomizerp.app.data.MostReadArticle
import kotlinx.coroutines.CancellationException
import org.nsh07.wikireader.shared.reader.FeedSource

private sealed interface FeedUiState {
    data object Loading : FeedUiState
    data object Error : FeedUiState
    data class Ready(val feed: FeedApiResponse) : FeedUiState
}

@Composable
fun FeedScreen(
    source: FeedSource,
    language: String,
    languageName: String,
    onOpenArticle: (lang: String, title: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var reload by remember { mutableIntStateOf(0) }
    var state by remember(language) { mutableStateOf<FeedUiState>(FeedUiState.Loading) }

    LaunchedEffect(source, language, reload) {
        state = FeedUiState.Loading
        state = try {
            FeedUiState.Ready(source.load(language))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            FeedUiState.Error
        }
    }

    when (val current = state) {
        FeedUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        FeedUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not load today's feed.")
                Text(
                    "Check your connection and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
        }

        is FeedUiState.Ready -> FeedList(
            feed = current.feed,
            language = language,
            languageName = languageName,
            onRefresh = { reload++ },
            onOpenArticle = onOpenArticle,
            onOpenUrl = onOpenUrl,
            modifier = modifier
        )
    }
}

@Composable
private fun FeedList(
    feed: FeedApiResponse,
    language: String,
    languageName: String,
    onRefresh: () -> Unit,
    onOpenArticle: (lang: String, title: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val news = feed.news.orEmpty()
    val onThisDay = feed.onThisDay.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Discover", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        languageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }

        feed.tfa?.let { tfa ->
            item { FeaturedArticle(tfa, language, onOpenArticle) }
        }

        val mostRead = feed.mostRead?.articles.orEmpty().sortedBy { it.rank ?: Int.MAX_VALUE }
        if (mostRead.isNotEmpty()) {
            item { SectionTitle("Most read") }
            items(mostRead, key = { it.titles?.canonical ?: it.titles?.normalized ?: it.hashCode().toString() }) {
                MostReadRow(it, language, onOpenArticle)
            }
        }

        feed.image?.let { image ->
            item { SectionTitle("Picture of the day") }
            item { PictureOfTheDay(image, onOpenUrl) }
        }

        if (news.isNotEmpty()) {
            item { SectionTitle("In the news") }
            items(news, key = { it.story ?: it.hashCode().toString() }) {
                NewsRow(it, language, onOpenArticle)
            }
        }

        if (onThisDay.isNotEmpty()) {
            item { SectionTitle("On this day") }
            items(onThisDay, key = { it.text ?: it.hashCode().toString() }) {
                OnThisDayRow(it, language, onOpenArticle)
            }
        }
    }
}

@Composable
private fun FeaturedArticle(
    tfa: FeedApiTFA,
    language: String,
    onOpenArticle: (String, String) -> Unit
) {
    val title = feedTitle(tfa.titles).ifBlank { return }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenArticle(language, title) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column {
            imageUrl(tfa.originalImage, tfa.thumbnail)?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
            Column(Modifier.padding(16.dp)) {
                Text(
                    "FEATURED ARTICLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall)
                tfa.description?.let {
                    Text(
                        stripHtml(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                tfa.extract?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stripHtml(it),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Read article", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MostReadRow(
    article: MostReadArticle,
    language: String,
    onOpenArticle: (String, String) -> Unit
) {
    val title = feedTitle(article.titles).ifBlank { return }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenArticle(language, title) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            article.rank?.toString() ?: "•",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        imageUrl(article.thumbnail, article.originalImage)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            article.description?.let {
                Text(
                    stripHtml(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PictureOfTheDay(image: FeedApiImage, onOpenUrl: (String) -> Unit) {
    val pictureUrl = imageUrl(image.thumbnail, image.image)
    val filePage = image.filePage
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (filePage != null) Modifier.clickable { onOpenUrl(filePage) }
                else Modifier
            )
    ) {
        Column {
            pictureUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = image.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
            Column(Modifier.padding(16.dp)) {
                image.title?.removePrefix("File:")?.let {
                    Text(stripHtml(it), style = MaterialTheme.typography.titleMedium)
                }
                image.description?.text?.let {
                    Text(
                        stripHtml(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                (image.artist?.text ?: image.credit?.text)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stripHtml(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsRow(
    news: FeedApiNews,
    language: String,
    onOpenArticle: (String, String) -> Unit
) {
    Column {
        Text(stripHtml(news.story.orEmpty()), style = MaterialTheme.typography.bodyMedium)
        news.links.orEmpty().forEach { link ->
            val title = feedTitle(link.titles)
            if (title.isNotBlank()) {
                TextButton(onClick = { onOpenArticle(language, title) }) { Text(title) }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun OnThisDayRow(
    event: FeedApiOTD,
    language: String,
    onOpenArticle: (String, String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            event.year?.toString() ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f)) {
            Text(stripHtml(event.text.orEmpty()), style = MaterialTheme.typography.bodyMedium)
            event.pages.orEmpty().take(3).forEach { page ->
                val title = feedTitle(page.titles)
                if (title.isNotBlank()) {
                    TextButton(onClick = { onOpenArticle(language, title) }) { Text(title) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

private fun feedTitle(titles: FeedTitles?): String =
    titles?.canonical ?: titles?.normalized.orEmpty()

private fun imageUrl(first: FeedImageSource?, second: FeedImageSource?): String? =
    first?.source ?: second?.source

private fun stripHtml(value: String): String =
    value
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
