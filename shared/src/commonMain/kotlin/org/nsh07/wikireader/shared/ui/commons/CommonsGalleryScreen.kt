package org.nsh07.wikireader.shared.ui.commons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wikirandomizerp.app.data.CommonsImage
import org.nsh07.wikireader.shared.reader.CommonsSource

@Composable
fun CommonsGalleryScreen(
    source: CommonsSource,
    language: String,
    title: String,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var images by remember(title, language) { mutableStateOf<List<CommonsImage>?>(null) }
    LaunchedEffect(source, language, title) {
        images = source.images(language, title)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back") }
        when (val current = images) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            emptyList<CommonsImage>() -> Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No pictures found for this article.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(current, key = { it.title }) { image ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = image.fullUrl ?: image.descriptionUrl
                                if (target != null) onOpenUrl(target)
                            }
                    ) {
                        AsyncImage(
                            model = image.thumbnailUrl ?: image.fullUrl,
                            contentDescription = image.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                        Text(
                            image.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
