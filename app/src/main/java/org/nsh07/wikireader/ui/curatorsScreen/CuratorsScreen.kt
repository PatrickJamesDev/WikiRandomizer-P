package org.nsh07.wikireader.ui.curatorsScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.Curator
import org.nsh07.wikireader.data.CuratorPick
import org.nsh07.wikireader.data.CuratorRepository
import org.nsh07.wikireader.ui.curatorsScreen.viewModel.CuratorsUiState
import org.nsh07.wikireader.ui.curatorsScreen.viewModel.CuratorsViewModel
import org.nsh07.wikireader.ui.theme.CustomColors.listItemColors
import org.nsh07.wikireader.ui.theme.CustomColors.topBarColors

@Composable
fun CuratorsScreenRoot(
    openArticle: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CuratorsViewModel = viewModel(factory = CuratorsViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val followed by viewModel.followed.collectAsStateWithLifecycle()

    CuratorsScreen(
        state = state,
        followed = followed,
        onToggleFollow = viewModel::toggleFollow,
        onRefresh = viewModel::refresh,
        openArticle = openArticle,
        onBack = onBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CuratorsScreen(
    state: CuratorsUiState,
    followed: Set<String>,
    onToggleFollow: (String) -> Unit,
    onRefresh: () -> Unit,
    openArticle: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.curators)) },
                subtitle = { Text(stringResource(R.string.curatorsSubtitle)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = listItemColors.containerColor
                        ),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { uriHandler.openUri(CuratorRepository.FEED_PAGE) },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painterResource(R.drawable.github),
                            contentDescription = stringResource(R.string.curatorsOpenOnGitHub)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = topBarColors
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(topBarColors.containerColor)
    ) { insets ->
        when (state) {
            CuratorsUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(insets),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.curatorsLoading), style = typography.bodyMedium)
                }
            }

            is CuratorsUiState.Failed -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.outline_info),
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.curatorsError), style = typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.message,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onRefresh,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.curatorsRetry))
                    }
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { uriHandler.openUri(CuratorRepository.FEED_PAGE) }
                    ) {
                        Text(stringResource(R.string.curatorsOpenOnGitHub))
                    }
                }
            }

            is CuratorsUiState.Ready -> {
                val ordered = state.curators.sortedWith(
                    compareByDescending<Curator> { it.id in state.recommended }
                        .thenByDescending { it.id in followed }
                        .thenBy { it.name.lowercase() }
                )
                LazyColumn(
                    contentPadding = insets,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Surface(
                            color = colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.curatorsIntro),
                                    style = typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    shapes = ButtonDefaults.shapes(),
                                    onClick = { uriHandler.openUri(CuratorRepository.FEED_PAGE) }
                                ) {
                                    Icon(
                                        painterResource(R.drawable.github),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.curatorsAddYours))
                                }
                            }
                        }
                    }
                    if (state.fromFallback) {
                        item {
                            Surface(
                                color = colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    stringResource(R.string.curatorsOffline),
                                    style = typography.bodySmall,
                                    color = colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    if (ordered.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.curatorsEmpty),
                                        style = typography.titleMedium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.curatorsEmptyDesc),
                                        style = typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    items(ordered, key = { it.id.ifBlank { it.name } }) { curator ->
                        CuratorCard(
                            curator = curator,
                            following = curator.id in followed,
                            recommended = curator.id in state.recommended,
                            onToggleFollow = { onToggleFollow(curator.id) },
                            openArticle = openArticle,
                            onOpenGithub = {
                                if (curator.github.isNotBlank()) uriHandler.openUri(curator.github)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CuratorCard(
    curator: Curator,
    following: Boolean,
    recommended: Boolean,
    onToggleFollow: () -> Unit,
    openArticle: (String, String) -> Unit,
    onOpenGithub: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = listItemColors.containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CuratorAvatar(curator)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            curator.name.ifBlank { curator.id },
                            style = typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (recommended) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = colorScheme.primaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    stringResource(R.string.curatorRecommended),
                                    style = typography.labelSmall,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (curator.bio.isNotBlank()) {
                        Text(
                            curator.bio,
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onToggleFollow,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        stringResource(
                            if (following) R.string.curatorFollowing else R.string.curatorFollow
                        )
                    )
                }
            }

            if (curator.picks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.curatorPicks),
                    style = typography.labelLarge,
                    color = colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                curator.picks.forEach { pick ->
                    CuratorPickRow(pick = pick, openArticle = openArticle)
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (curator.github.isNotBlank()) {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onOpenGithub
                ) {
                    Icon(
                        painterResource(R.drawable.github),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.curatorGithub))
                }
            }
        }
    }
}

@Composable
private fun CuratorAvatar(curator: Curator) {
    val avatar = curator.avatar
    if (!avatar.isNullOrBlank()) {
        AsyncImage(
            model = avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
    } else {
        Surface(
            color = colorScheme.tertiaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    curator.name.firstOrNull()?.uppercase() ?: "?",
                    style = typography.titleLarge,
                    color = colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CuratorPickRow(
    pick: CuratorPick,
    openArticle: (String, String) -> Unit
) {
    Surface(
        onClick = { openArticle(pick.title, pick.lang) },
        color = colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.search),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pick.title,
                    style = typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (pick.note.isNotBlank()) {
                    Text(
                        pick.note,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
