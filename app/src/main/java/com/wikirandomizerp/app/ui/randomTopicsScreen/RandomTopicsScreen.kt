package com.wikirandomizerp.app.ui.randomTopicsScreen

import com.wikirandomizerp.app.data.friendlyError
import com.wikirandomizerp.app.data.SEARCH_TOPIC_PREFIX
import com.wikirandomizerp.app.data.TopicHint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wikirandomizerp.app.data.ALL_TOPICS
import com.wikirandomizerp.app.data.ArticleCache
import com.wikirandomizerp.app.data.InterestMatch
import com.wikirandomizerp.app.data.LibraryStats
import com.wikirandomizerp.app.ui.homeScreen.viewModel.IndexBuildState
import com.wikirandomizerp.app.data.TOPIC_GROUPS
import com.wikirandomizerp.app.data.WikiSubgroup
import com.wikirandomizerp.app.data.WikiTopic
import com.wikirandomizerp.app.data.WikiTopicGroup

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun RandomTopicsScreen(
    onGetTopics: suspend () -> Set<String>,
    /** Saves the topics and starts indexing any that are new. Returns true if indexing started. */
    onSaveTopics: suspend (topics: Set<String>, forceRebuild: Boolean) -> Boolean,
    indexState: IndexBuildState,
    onCancelBuild: () -> Unit,
    onResolveInterests: suspend (String, (String) -> Unit) -> List<InterestMatch>,
    onGetInterestText: suspend () -> String,
    onSaveInterestText: suspend (String) -> Unit,
    onGetLibraryStats: suspend () -> LibraryStats,
    /** Turns a topic on/off without deleting its downloaded library. */
    onSetTopicEnabled: suspend (String, Boolean) -> Unit,
    /** Explicitly deletes a downloaded library (confirmation is handled in the UI). */
    onRemoveTopic: suspend (String) -> Unit,
    /** Estimates an offline download without touching the network. */
    onEstimateOffline: suspend (Int) -> ArticleCache.OfflineSelection = {
        ArticleCache.OfflineSelection(0, 0L)
    },
    /** Downloads [count] articles, reporting (done, total); returns how many were stored. */
    onDownloadOffline: suspend (Int, suspend (Int, Int) -> Unit) -> Int = { _, _ -> 0 },
    onGetOfflineStats: suspend () -> ArticleCache.OfflineStats = {
        ArticleCache.OfflineStats(0, 0L)
    },
    onDeleteOffline: suspend () -> Unit = {},
    onSaveTopicHints: suspend (Map<String, TopicHint>) -> Unit,
    onResetSeen: suspend () -> Unit,
    onStartBrowsing: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Progress overlay: shown while the topic index is being built, then (only if something went
    // wrong) as a result page; otherwise we leave the screen automatically when it finishes.
    var overlayVisible by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    // A build started elsewhere (auto-build after adding a topic, or a resumed build from a
    // previous launch) must not cover the topic list. The compact banner below offers progress
    // instead, so the user can keep adding topics while the library builds.

    // ── Natural-language interests + library overview ────────────────────────
    var interestText by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var resolveStatus by remember { mutableStateOf<String?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<InterestMatch>?>(null) }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    var libraryStats by remember { mutableStateOf<LibraryStats?>(null) }
    var libraryExpanded by remember { mutableStateOf(false) }
    val topicLabels = remember { ALL_TOPICS.associate { it.category to it.label } }
    LaunchedEffect(Unit) { interestText = onGetInterestText() }
    var libraryReload by remember { mutableStateOf(0) }
    // The library overview used to be loaded only while NO build was running, and a failed read was
    // turned into `null`, which the card shows as "Loading…" forever. The database is in WAL mode,
    // so reading while a build writes is safe; and a failure is now reported (with a retry) instead
    // of looking like an endless load.
    var libraryLoadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(indexState.running, libraryReload) {
        try {
            libraryStats = onGetLibraryStats()
            libraryLoadFailed = false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.wikirandomizerp.app.data.DebugLog.e("Library", "could not read the library overview", e)
            if (libraryStats == null) libraryLoadFailed = true
        }
    }

    // ── Explicit offline downloads ───────────────────────────────────────────
    var offlineCount by rememberSaveable { mutableIntStateOf(20) }
    var offlineStats by remember { mutableStateOf<ArticleCache.OfflineStats?>(null) }
    var offlineSelection by remember { mutableStateOf<ArticleCache.OfflineSelection?>(null) }
    var offlineDownloading by remember { mutableStateOf(false) }
    var offlineProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(libraryReload, indexState.running) {
        if (!indexState.running) {
            offlineStats = try { onGetOfflineStats() } catch (e: Exception) { null }
        }
    }
    LaunchedEffect(offlineCount, libraryStats, indexState.running, offlineStats, libraryReload) {
        if (!indexState.running) {
            offlineSelection = try {
                onEstimateOffline(offlineCount)
            } catch (e: Exception) {
                null
            }
        }
    }
    val view = LocalView.current
    DisposableEffect(indexState.running) {
        view.keepScreenOn = indexState.running // building takes minutes; don't let the screen sleep
        onDispose { view.keepScreenOn = false }
    }

    // ── Performance: mutableStateMapOf gives per-key granular recomposition ──
    // Only the chips whose state changed recompose, not the entire screen.
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    // For topics that have a library row, the database's enabled flag is the source of truth.
    // This keeps the checkboxes right after migration or process death.
    LaunchedEffect(libraryStats) {
        libraryStats?.topics?.forEach { stat ->
            if (stat.enabled) selected[stat.topic] = true else selected.remove(stat.topic)
        }
    }

    val expandedGroups   = remember { mutableStateMapOf<String, Boolean>() }
    val expandedSubgroups = remember { mutableStateMapOf<String, Boolean>() }

    // Reveal the group and subgroup that own a newly added topic. Without this the new chip
    // was selected but still hidden inside collapsed rows, so the list looked unchanged.
    fun expandGroupsFor(categories: Collection<String>) {
        if (categories.isEmpty()) return
        TOPIC_GROUPS.forEach { group ->
            val groupHasTopic = categories.any { it == group.category } ||
                group.subgroups.any { sub ->
                    categories.any { it == sub.category } ||
                        sub.children.any { child -> child.category in categories }
                }
            if (groupHasTopic) {
                expandedGroups[group.category] = true
                group.subgroups.forEach { sub ->
                    if (categories.any { it == sub.category } ||
                        sub.children.any { child -> child.category in categories }
                    ) {
                        expandedSubgroups[sub.category] = true
                    }
                }
            }
        }
    }

    // ── Load saved topics once ────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val saved = onGetTopics()
        saved.forEach { selected[it] = true }
        // Auto-expand groups/subgroups that have saved selections
        TOPIC_GROUPS.forEach { group ->
            val groupHasSelection = selected[group.category] == true ||
                group.subgroups.any { sub ->
                    selected[sub.category] == true ||
                    sub.children.any { selected[it.category] == true }
                }
            if (groupHasSelection) {
                expandedGroups[group.category] = true
                group.subgroups.forEach { sub ->
                    val subHasSelection = selected[sub.category] == true ||
                        sub.children.any { selected[it.category] == true }
                    if (subHasSelection) expandedSubgroups[sub.category] = true
                }
            }
        }
    }

    val totalSelected by remember { derivedStateOf { selected.count { it.value } } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.randomTopicsTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // The primary action lives in the always-visible top bar. A bottom button was
                    // squeezed below the window on some devices with large navigation insets.
                    TextButton(
                        onClick = {
                            scope.launch {
                                val started = onSaveTopics(selected.filter { it.value }.keys.toSet(), false)
                                if (started) overlayVisible = true else onBack()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.saveTopics))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Count row + global actions ────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (totalSelected == 0)
                        stringResource(R.string.randomTopicsNoneSelected)
                    else
                        stringResource(R.string.randomTopicsCountSelected, totalSelected),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = { confirmClearAll = true }) {
                        Text(stringResource(R.string.clearAll))
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val started = onSaveTopics(selected.filter { it.value }.keys.toSet(), true)
                            if (started) overlayVisible = true else onBack()
                        }
                    }) { Text(stringResource(R.string.indexRebuild)) }
                }
            }

            // A build that is already running stays visible but does not block the list. The
            // user can add more topics and they are queued behind the active build.
            if (indexState.running) {
                Surface(
                    color = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.indexBuildBackgroundBanner),
                                style = typography.titleSmall
                            )
                            Text(
                                if (indexState.currentTopic.isBlank())
                                    stringResource(R.string.indexBuildStarting)
                                else
                                    stringResource(
                                        R.string.indexBuildBannerProgress,
                                        indexState.topicIndex + 1,
                                        indexState.topicTotal,
                                        indexState.currentTopic
                                    ),
                                style = typography.bodySmall
                            )
                        }
                        TextButton(onClick = { overlayVisible = true }) {
                            Text(stringResource(R.string.indexBuildViewProgress))
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Main list ─────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                // Leave room for the floating Save button so the last topic chip can always be
                // scrolled fully into view.
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item(key = "interest_card") {
                        InterestCard(
                            text = interestText,
                            onTextChange = { interestText = it },
                            resolving = resolving,
                            status = resolveStatus,
                            error = resolveError,
                            matches = matches,
                            picked = picked,
                            onFind = {
                                scope.launch {
                                    resolving = true
                                    resolveError = null
                                    matches = null
                                    try {
                                        onSaveInterestText(interestText)
                                        val result = onResolveInterests(interestText) { resolveStatus = it }
                                        matches = result
                                        picked.clear()
                                        // Pre-tick the best few, but never a huge generic category.
                                        result.forEach { m ->
                                            m.categories.take(3).forEach { c ->
                                                if (c.relevant && c.size in 1..MAX_PRESELECT_SIZE) picked[c.category] = true
                                            }
                                        }
                                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        resolveError = friendlyError(e)
                                    } finally {
                                        resolving = false
                                        resolveStatus = null
                                    }
                                }
                            },
                            onAddSearch = { m ->
                                // A topic with no category: its library is built from full-text search.
                                val key = SEARCH_TOPIC_PREFIX + m.phrase.replace(',', ' ').replace('\n', ' ').trim()
                                selected[key] = true
                                scope.launch {
                                    onSaveTopicHints(mapOf(key to TopicHint(m.phrase, m.exclusions)))
                                    // Adding a topic starts its download immediately; if another
                                    // build is active it is queued instead of cancelling it.
                                    onSaveTopics(selected.filter { it.value }.keys.toSet(), false)
                                }
                            },
                            onAdd = {
                                val chosen = picked.filter { it.value }.keys.toSet()
                                // Remember the user's words per category: the library build uses
                                // them to drop off-topic articles (e.g. cigarettes under nightshades).
                                val hints = HashMap<String, TopicHint>()
                                matches?.forEach { m ->
                                    m.categories.forEach { c ->
                                        if (c.category in chosen) hints[c.category] = TopicHint(m.phrase, m.exclusions)
                                    }
                                }
                                chosen.forEach { selected[it] = true }
                                expandGroupsFor(chosen)
                                scope.launch {
                                    onSaveTopicHints(hints)
                                    // Auto-build the newly added topics. The full selected set is
                                    // saved so the new topics are also enabled for random picks.
                                    onSaveTopics(selected.filter { it.value }.keys.toSet(), false)
                                }
                                picked.clear()
                                matches = null
                            }
                        )
                    }
                    item(key = "custom_topics") {
                        CustomTopicsRow(
                            topics = selected.filter { it.value && it.key !in topicLabels }.keys.sorted(),
                            onRemove = { selected.remove(it) }
                        )
                    }
                    item(key = "library_card") {
                        LibraryCard(
                            stats = libraryStats,
                            loadFailed = libraryLoadFailed,
                            onRetry = {
                                libraryLoadFailed = false
                                libraryReload++
                            },
                            selectedTopics = selected.filter { it.value }.keys.toList(),
                            labels = topicLabels,
                            expanded = libraryExpanded,
                            onToggle = { libraryExpanded = !libraryExpanded },
                            onResetSeen = {
                                scope.launch {
                                    onResetSeen()
                                    libraryReload++
                                }
                            },
                            onToggleEnabled = { topic, enabled ->
                                // Update the switch state first; the database write and library
                                // reload must never block the visual feedback. LibraryCard reads
                                // `enabled` from stats, so update both representations.
                                if (enabled) selected[topic] = true else selected.remove(topic)
                                libraryStats = libraryStats?.let { stats ->
                                    stats.copy(
                                        topics = stats.topics.map { stat ->
                                            if (stat.topic == topic) stat.copy(enabled = enabled)
                                            else stat
                                        }
                                    )
                                }
                                scope.launch {
                                    onSetTopicEnabled(topic, enabled)
                                    libraryReload++
                                }
                            },
                            onRemove = { topic ->
                                scope.launch {
                                    onRemoveTopic(topic)
                                    selected.remove(topic)
                                    libraryReload++
                                }
                            }
                        )
                    }
                    item(key = "offline_download") {
                        OfflineDownloadCard(
                            stats = offlineStats,
                            count = offlineCount,
                            estimateBytes = offlineSelection?.estimatedBytes ?: 0L,
                            downloading = offlineDownloading,
                            progress = offlineProgress,
                            enabled = (libraryStats?.totalArticles ?: 0) > 0,
                            estimateCount = offlineSelection?.articles,
                            onCountChange = { offlineCount = it },
                            onDownload = {
                                if (!offlineDownloading) {
                                    scope.launch {
                                        offlineDownloading = true
                                        offlineProgress = 0 to 0
                                        try {
                                            onDownloadOffline(offlineCount) { done, total ->
                                                // Progress arrives from the IO download loop; state
                                                // updates must still happen on the main thread.
                                                withContext(Dispatchers.Main.immediate) {
                                                    offlineProgress = done to total
                                                }
                                            }
                                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(friendlyError(e))
                                        } finally {
                                            offlineDownloading = false
                                            offlineStats = try { onGetOfflineStats() } catch (e: Exception) { offlineStats }
                                            offlineSelection = try {
                                                onEstimateOffline(offlineCount)
                                            } catch (e: Exception) {
                                                offlineSelection
                                            }
                                        }
                                    }
                                }
                            },
                            onDeleteAll = {
                                scope.launch {
                                    try {
                                        onDeleteOffline()
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(friendlyError(e))
                                    }
                                    offlineStats = try { onGetOfflineStats() } catch (e: Exception) { offlineStats }
                                }
                            }
                        )
                    }
                // ── Grouped browse mode ───────────────────────────────────────
                TOPIC_GROUPS.forEach { group ->
                        val groupExpanded = expandedGroups[group.category] == true

                        // Group header row
                        item(key = "g_${group.category}") {
                            GroupHeaderRow(
                                group = group,
                                expanded = groupExpanded,
                                isSelected = { selected[group.category] == true },
                                subSelectedCount = {
                                    group.subgroups.sumOf { sub ->
                                        (if (selected[sub.category] == true) 1 else 0) +
                                        sub.children.count { selected[it.category] == true }
                                    }
                                },
                                onToggleExpand = {
                                    expandedGroups[group.category] = !groupExpanded
                                },
                                onToggleSelect = {
                                    selected[group.category] = selected[group.category] != true
                                }
                            )
                        }

                        // Subgroup rows (visible when group is expanded)
                        if (groupExpanded) {
                            group.subgroups.forEach { subgroup ->
                                val subExpanded = expandedSubgroups[subgroup.category] == true

                                item(key = "s_${subgroup.category}") {
                                    SubgroupRow(
                                        subgroup = subgroup,
                                        expanded = subExpanded,
                                        isSelected = { selected[subgroup.category] == true },
                                        childSelectedCount = {
                                            subgroup.children.count { selected[it.category] == true }
                                        },
                                        onToggleExpand = {
                                            expandedSubgroups[subgroup.category] = !subExpanded
                                        },
                                        onToggleSelect = {
                                            selected[subgroup.category] = selected[subgroup.category] != true
                                        }
                                    )
                                }

                                // Child chips (visible when subgroup is expanded)
                                if (subExpanded && subgroup.children.isNotEmpty()) {
                                    item(key = "c_${subgroup.category}") {
                                        FlowRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 48.dp, end = 16.dp, bottom = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            subgroup.children.forEach { topic ->
                                                TopicChip(
                                                    topic = topic,
                                                    isSelected = { selected[topic.category] == true },
                                                    onToggle = {
                                                        selected[topic.category] = selected[topic.category] != true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "div_${group.category}") { HorizontalDivider() }
                    }
            }
        }

        if (overlayVisible) {
            // Back returns to the topic list, not out of the screen: the build keeps running and
            // the user can add more topics while it finishes. Only Cancel stops the build.
            BackHandler { overlayVisible = false }
            IndexProgressOverlay(
                state = indexState,
                labels = topicLabels,
                onCancel = onCancelBuild,
                onAddMore = { overlayVisible = false },
                onDone = {
                    overlayVisible = false
                    onBack()
                },
                onStartBrowsing = {
                    overlayVisible = false
                    onStartBrowsing()
                }
            )
        }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.clearAllConfirmTitle)) },
            text = { Text(stringResource(R.string.clearAllConfirmDesc)) },
            confirmButton = {
                TextButton(onClick = {
                    selected.clear()
                    confirmClearAll = false
                }) { Text(stringResource(R.string.clearAll)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ── Group header (top level) ──────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupHeaderRow(
    group: WikiTopicGroup,
    expanded: Boolean,
    isSelected: () -> Boolean,
    subSelectedCount: () -> Int,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val sel = isSelected()
    val subCount = subSelectedCount()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = sel,
            onClick = onToggleSelect,
            label = { Text(group.label, fontWeight = FontWeight.SemiBold) },
            leadingIcon = if (sel) {{ ChipCheckIcon() }} else null
        )
        if (subCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "+$subCount",
                style = typography.labelSmall,
                color = colorScheme.primary
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.expand_all),
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            tint = colorScheme.onSurfaceVariant
        )
    }
}

// ── Subgroup row (second level) ───────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubgroupRow(
    subgroup: WikiSubgroup,
    expanded: Boolean,
    isSelected: () -> Boolean,
    childSelectedCount: () -> Int,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val sel = isSelected()
    val childCount = childSelectedCount()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = sel,
            onClick = onToggleSelect,
            label = { Text(subgroup.label) },
            leadingIcon = if (sel) {{ ChipCheckIcon() }} else null
        )
        if (childCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "+$childCount",
                style = typography.labelSmall,
                color = colorScheme.primary
            )
        }
        if (subgroup.children.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.expand_all),
                contentDescription = null,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                tint = colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Leaf chip (third level) ───────────────────────────────────────────────────
@Composable
private fun TopicChip(
    topic: WikiTopic,
    isSelected: () -> Boolean,
    onToggle: () -> Unit
) {
    val sel = isSelected()
    FilterChip(
        selected = sel,
        onClick = onToggle,
        label = { Text(topic.label) },
        leadingIcon = if (sel) {{ ChipCheckIcon() }} else null
    )
}

@Composable
private fun ChipCheckIcon() {
    Icon(
        painter = painterResource(R.drawable.check),
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize)
    )
}

/** Categories larger than this are shown but not pre-selected (too generic). */
private const val MAX_PRESELECT_SIZE = 20_000
