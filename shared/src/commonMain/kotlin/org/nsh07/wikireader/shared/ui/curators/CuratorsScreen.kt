package org.nsh07.wikireader.shared.ui.curators

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wikirandomizerp.app.data.Curator
import com.wikirandomizerp.app.data.CuratorPick
import kotlinx.coroutines.CancellationException
import org.nsh07.wikireader.shared.reader.CuratorLinks
import org.nsh07.wikireader.shared.reader.CuratorSource
import org.nsh07.wikireader.shared.ui.languageName

private sealed interface CuratorUiState {
    data object Loading : CuratorUiState
    data class Ready(val curators: List<Curator>, val fromFallback: Boolean) : CuratorUiState
    data class Failed(val message: String) : CuratorUiState
}

@Composable
fun CuratorsScreen(
    source: CuratorSource,
    followed: Set<String>,
    interests: Set<String>,
    onToggleFollow: (String) -> Unit,
    onOpenArticle: (language: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var state by remember(source) { mutableStateOf<CuratorUiState>(CuratorUiState.Loading) }

    LaunchedEffect(source, refreshKey) {
        state = CuratorUiState.Loading
        state = try {
            val load = source.load()
            CuratorUiState.Ready(load.feed.curators, load.fromFallback)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            CuratorUiState.Failed(error.message ?: "Could not load the curator list")
        }
    }

    when (val current = state) {
        CuratorUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading curators…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        is CuratorUiState.Failed -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not load curators", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = { refreshKey++ }) {
                    Text("Try again")
                }
                TextButton(onClick = { uriHandler.openUri(CuratorLinks.FEED_PAGE) }) {
                    Text("Open the curator list on GitHub")
                }
            }
        }

        is CuratorUiState.Ready -> {
            val recommended = remember(current.curators, interests) {
                current.curators.filter { curator ->
                    curator.picks.any { it.title.trim().lowercase() in interests }
                }.map { it.id }.toSet()
            }
            val ordered = remember(current.curators, recommended, followed) {
                current.curators.sortedWith(
                    compareByDescending<Curator> { it.id in recommended }
                        .thenByDescending { it.id in followed }
                        .thenBy { it.name.lowercase() }
                )
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Community picks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Follow readers whose taste matches yours and open the " +
                                    "articles they recommend. The list lives on GitHub, so it " +
                                    "grows without an app update.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { uriHandler.openUri(CuratorLinks.FEED_PAGE) }) {
                                Text("Add your picks on GitHub")
                            }
                        }
                    }
                }

                if (current.fromFallback) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "The live list could not be reached. A bundled copy is shown.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (ordered.isEmpty()) {
                    item {
                        Text(
                            text = "No curators have been added yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(ordered, key = { it.id.ifBlank { it.name } }) { curator ->
                        CuratorCard(
                            curator = curator,
                            following = curator.id in followed,
                            recommended = curator.id in recommended,
                            onToggleFollow = { onToggleFollow(curator.id) },
                            onOpenArticle = onOpenArticle,
                            onOpenGithub = {
                                if (curator.github.isNotBlank()) uriHandler.openUri(curator.github)
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CuratorCard(
    curator: Curator,
    following: Boolean,
    recommended: Boolean,
    onToggleFollow: () -> Unit,
    onOpenArticle: (language: String, title: String) -> Unit,
    onOpenGithub: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = curator.avatar
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = curator.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = curator.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = curator.name.ifBlank { "Unnamed curator" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpenGithub)
                    )
                    if (recommended) {
                        Text(
                            text = "Matches your saved reading",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TextButton(onClick = onToggleFollow) {
                    Text(if (following) "Following" else "Follow")
                }
            }

            if (curator.bio.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = curator.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (curator.picks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                curator.picks.forEach { pick ->
                    CuratorPickRow(pick = pick, onOpen = onOpenArticle)
                }
            }
        }
    }
}

@Composable
private fun CuratorPickRow(
    pick: CuratorPick,
    onOpen: (language: String, title: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pick.title.isNotBlank()) { onOpen(pick.lang, pick.title) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pick.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (pick.note.isNotBlank()) {
                Text(
                    text = pick.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = languageName(pick.lang),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
