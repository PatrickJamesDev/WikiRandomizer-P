package com.wikirandomizerp.app.ui.commonsScreen

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.CommonsImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonsGalleryScreen(
    title: String,
    onGetImages: suspend (String) -> List<CommonsImage>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var images by remember(title) { mutableStateOf<List<CommonsImage>?>(null) }
    var failed by remember(title) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(title) {
        images = runCatching { onGetImages(title) }.onFailure { failed = true }.getOrDefault(emptyList())
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.commonsGallery),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { insets ->
        val current = images
        when {
            current == null -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(insets)
            ) { CircularProgressIndicator() }
            current.isEmpty() -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(insets)
            ) {
                Text(
                    stringResource(if (failed) R.string.commonsLoadFailed else R.string.commonsEmpty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(current, key = { it.title }) { image ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = image.fullUrl ?: image.descriptionUrl
                                if (target != null) uriHandler.openUri(target)
                            }
                    ) {
                        AsyncImage(
                            model = image.thumbnailUrl ?: image.fullUrl,
                            contentDescription = image.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
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
