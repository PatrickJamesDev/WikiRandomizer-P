package org.nsh07.wikireader.shared.ui.images

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.nsh07.wikireader.shared.library.SavedImageEntry

@Composable
fun SavedImagesScreen(
    images: List<SavedImageEntry>,
    onOpenArticle: (lang: String, title: String) -> Unit,
    onDelete: (SavedImageEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No saved pictures yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use Save image in Discover to keep a picture and its article.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TextButton(onClick = onClear, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Clear all pictures")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(images, key = { it.id }) { image ->
                SavedImageCard(
                    image = image,
                    onOpen = {
                        if (image.articleTitle.isNotBlank() && image.articleLanguage.isNotBlank()) {
                            onOpenArticle(image.articleLanguage, image.articleTitle)
                        }
                    },
                    onDelete = { onDelete(image) }
                )
            }
        }
    }
}

@Composable
private fun SavedImageCard(
    image: SavedImageEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onOpen)
            ) {
                AsyncImage(
                    model = image.thumbnailUrl ?: image.imageUrl,
                    contentDescription = image.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text("Delete")
                }
            }
            val title = image.articleTitle.ifBlank { image.description.orEmpty() }
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}
