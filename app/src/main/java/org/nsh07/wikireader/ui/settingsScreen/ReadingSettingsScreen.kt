package org.nsh07.wikireader.ui.settingsScreen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.LibraryStats
import org.nsh07.wikireader.data.kiwix.KiwixStatus
import org.nsh07.wikireader.ui.theme.CustomColors.listItemColors
import org.nsh07.wikireader.ui.theme.CustomColors.topBarColors

/**
 * A small settings submenu so the main Settings list stays focused. Topic libraries are a
 * substantial feature (they build an on-device index and can hold hundreds of megabytes of
 * downloads), so they get their own page instead of a single entry at the top of Settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadingSettingsScreenRoot(
    onNavigateToRandomTopics: () -> Unit,
    onGetLibraryStats: suspend () -> LibraryStats = {
        LibraryStats(totalArticles = 0, unseen = 0, topics = emptyList())
    },
    kiwixStatus: KiwixStatus = KiwixStatus(),
    onSelectKiwix: (Uri, String?) -> Unit = { _, _ -> },
    onRemoveKiwix: () -> Unit = {},
    onSetKiwixForRandom: (Boolean) -> Unit = {},
    onOpenKiwixReader: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var libraryStats by remember { mutableStateOf<LibraryStats?>(null) }
    LaunchedEffect(Unit) {
        libraryStats = try {
            onGetLibraryStats()
        } catch (e: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                title = R.string.settingsReadingTopics
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { insets ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = insets,
            modifier = Modifier
                .background(topBarColors.containerColor)
                .padding(horizontal = 16.dp)
        ) {
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.view_list),
                            tint = colorScheme.primary,
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.randomTopicsTitle)) },
                    supportingContent = {
                        val stats = libraryStats
                        Text(
                            if (stats == null || stats.topics.isEmpty())
                                stringResource(R.string.settingsReadingTopicsDesc)
                            else
                                stringResource(
                                    R.string.settingsLibrarySummary,
                                    stats.totalArticles,
                                    stats.topics.size
                                )
                        )
                    },
                    colors = listItemColors,
                    items = 1,
                    index = 0
                ) { onNavigateToRandomTopics() }
            }
            item {
                Text(
                    text = stringResource(R.string.settingsReadingTopicsIntro),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            item {
                KiwixSettingsCard(
                    status = kiwixStatus,
                    onSelect = onSelectKiwix,
                    onRemove = onRemoveKiwix,
                    onUseForRandom = onSetKiwixForRandom,
                    onOpenReader = onOpenKiwixReader
                )
            }
        }
    }
}
