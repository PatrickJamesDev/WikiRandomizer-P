@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.nsh07.wikireader.ui.randomTopicsScreen

import org.nsh07.wikireader.data.SEARCH_TOPIC_PREFIX
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.IndexPhase
import org.nsh07.wikireader.data.InterestMatch
import org.nsh07.wikireader.data.LibraryStats
import org.nsh07.wikireader.data.TopicBuildResult
import org.nsh07.wikireader.ui.homeScreen.viewModel.IndexBuildState
import java.text.NumberFormat

private fun num(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** Readable name of a topic key: built-in label, "search: phrase" topics, or the category name. */
private fun topicName(topic: String, labels: Map<String, String>): String =
    labels[topic] ?: if (topic.startsWith(SEARCH_TOPIC_PREFIX))
        "\uD83D\uDD0D " + topic.removePrefix(SEARCH_TOPIC_PREFIX)
    else topic.replace('_', ' ')

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ── Natural-language interests ────────────────────────────────────────────────

@Composable
fun InterestCard(
    text: String,
    onTextChange: (String) -> Unit,
    resolving: Boolean,
    status: String?,
    error: String?,
    matches: List<InterestMatch>?,
    picked: SnapshotStateMap<String, Boolean>,
    onFind: () -> Unit,
    onAdd: () -> Unit,
    onAddSearch: (InterestMatch) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.interestTitle), style = typography.titleMedium)
            Text(
                stringResource(R.string.interestHint),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.interestExample)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onFind, enabled = !resolving && text.isNotBlank()) {
                Text(stringResource(R.string.interestFind))
            }
            if (resolving) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text(status ?: "", style = typography.bodyMedium)
                }
            }
            if (error != null) {
                Text(error, style = typography.bodyMedium, color = colorScheme.error)
            }
            if (matches != null) {
                if (matches.all { it.categories.isEmpty() }) {
                    Text(stringResource(R.string.interestNothing), style = typography.bodyMedium)
                }
                matches.forEach { m ->
                    Text(
                        stringResource(R.string.interestPhrase, m.phrase),
                        style = typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (m.exclusions.isNotEmpty()) {
                        Text(
                            stringResource(R.string.interestExcluding, m.exclusions.joinToString(", ")),
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    if (m.sampleArticles.isNotEmpty()) {
                        Text(
                            stringResource(R.string.interestExamples, m.sampleArticles.take(4).joinToString(", ")),
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onAddSearch(m) }) {
                        Text(stringResource(R.string.interestAddSearch))
                    }
                    if (m.categories.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            m.categories.forEach { c ->
                                FilterChip(
                                    selected = picked[c.category] == true,
                                    onClick = { picked[c.category] = picked[c.category] != true },
                                    label = { Text(if (c.size > 0) "${c.label} (${num(c.size)})" else c.label) }
                                )
                            }
                        }
                    }
                }
                if (matches.any { it.categories.isNotEmpty() }) {
                    Text(
                        stringResource(R.string.interestPickHint),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onAdd, enabled = picked.any { it.value }) {
                        Text(stringResource(R.string.interestAdd, picked.count { it.value }))
                    }
                }
            }
        }
    }
}

/** Selected topics that aren't in the built-in list (they came from the description box). */
@Composable
fun CustomTopicsRow(topics: List<String>, onRemove: (String) -> Unit) {
    if (topics.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.customTopics), style = typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            topics.forEach { t ->
                FilterChip(
                    selected = true,
                    onClick = { onRemove(t) },
                    label = { Text(topicName(t, emptyMap())) },
                    trailingIcon = {
                        Icon(painterResource(R.drawable.clear), contentDescription = stringResource(R.string.back))
                    }
                )
            }
        }
    }
}

// ── Library overview ──────────────────────────────────────────────────────────

@Composable
fun LibraryCard(
    stats: LibraryStats?,
    selectedTopics: List<String>,
    labels: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onResetSeen: () -> Unit,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {}
) {
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        onClick = if (stats == null && loadFailed) onRetry else onToggle
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.libraryTitle), style = typography.titleMedium)
            if (stats == null && loadFailed) {
                Text(stringResource(R.string.libraryLoadFailed), style = typography.bodyMedium, color = colorScheme.error)
            } else if (stats == null) {
                Text(stringResource(R.string.libraryLoading), style = typography.bodyMedium)
            } else if (stats.topics.isEmpty()) {
                Text(stringResource(R.string.libraryEmpty), style = typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.libraryTotals, num(stats.totalArticles), num(stats.unseen), stats.topics.size),
                    style = typography.bodyMedium
                )
                Text(
                    stringResource(
                        R.string.libraryStorage,
                        android.text.format.Formatter.formatShortFileSize(LocalContext.current, stats.sizeBytes)
                    ),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onResetSeen) { Text(stringResource(R.string.libraryStartOver)) }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    stats.topics.forEach { t ->
                        val name = topicName(t.topic, labels)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = typography.titleSmall)
                                Text(
                                    stringResource(
                                        R.string.libraryTopicRead,
                                        num(t.readCount),
                                        num(t.remaining)
                                    ),
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            // Off keeps the downloaded library; check it again to use it instantly.
                            androidx.compose.material3.Switch(
                                checked = t.enabled,
                                onCheckedChange = { onToggleEnabled(t.topic, it) }
                            )
                            // Deliberately destructive and separate from the switch.
                            TextButton(onClick = { confirmRemove = t.topic }) {
                                Text(stringResource(R.string.libraryRemove))
                            }
                        }
                        Text(
                            stringResource(
                                R.string.libraryTopicLine, num(t.indexed), num(t.withImages),
                                num(t.good), num(t.featured)
                            ) + if (t.resolved != t.topic) "  (${stringResource(R.string.libraryUsing, t.resolved.replace('_', ' '))})" else "",
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (t.complete) stringResource(
                                R.string.libraryBuilt,
                                android.text.format.DateUtils.getRelativeTimeSpanString(
                                    t.builtAt, System.currentTimeMillis(),
                                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                                ).toString()
                            ) else stringResource(R.string.libraryIncomplete),
                            style = typography.bodySmall,
                            color = if (t.complete) colorScheme.onSurfaceVariant else colorScheme.error
                        )
                    }
                    val built = stats.topics.map { it.topic }.toSet()
                    selectedTopics.filter { it !in built }.forEach { t ->
                        Text(
                            topicName(t, labels) + " - " + stringResource(R.string.libraryNotBuilt),
                            style = typography.bodySmall,
                            color = colorScheme.error
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.libraryTapToExpand),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    confirmRemove?.let { topic ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.libraryRemoveConfirmTitle)) },
            text = {
                Text(
                    stringResource(
                        R.string.libraryRemoveConfirmDesc,
                        labels[topic] ?: topic.removePrefix(SEARCH_TOPIC_PREFIX)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(topic)
                    confirmRemove = null
                }) { Text(stringResource(R.string.libraryRemove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ── Build progress / result ───────────────────────────────────────────────────

@Composable
fun IndexProgressOverlay(
    state: IndexBuildState,
    labels: Map<String, String>,
    onCancel: () -> Unit,
    onAddMore: () -> Unit,
    onDone: () -> Unit,
    onStartBrowsing: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            // The overlay is drawn edge-to-edge; without this the bottom action row sits under
            // the navigation bar on devices with large insets.
            .navigationBarsPadding(),
        color = colorScheme.surface
    ) {
        if (state.running) RunningView(state, onCancel, onStartBrowsing, onAddMore)
        else ResultView(state, labels, onDone, onAddMore)
    }
}

@Composable
private fun RunningView(
    state: IndexBuildState,
    onCancel: () -> Unit,
    onStartBrowsing: () -> Unit,
    onAddMore: () -> Unit
) {
    // Clock for "elapsed" and "last response N s ago": ticks even when nothing else changes,
    // so the screen visibly proves it is alive.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val p = state.progress
    val sinceActivity = now - state.lastActivityMs
    // A topic becomes usable before its optional popularity download finishes. Offer browsing as
    // soon as at least one topic is complete, instead of waiting for every topic or for a raw
    // discovered-article counter that is not yet safe to serve.
    val usableNow = state.results.any { it.ok }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.indexBuildTitle), style = typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.indexBuildHint),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.indexBuildTopic, state.topicIndex + 1, state.topicTotal, state.currentTopic),
            style = typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))

        if (p == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.indexBuildStarting), style = typography.bodySmall)
        } else if (p.phase == IndexPhase.VIEWS) {
            val frac = if (p.toCheck > 0) (p.checked.toFloat() / p.toCheck).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.indexBuildViews, num(p.checked), num(p.toCheck)),
                style = typography.bodyMedium
            )
        } else if (p.phase == IndexPhase.INTROS) {
            val frac = if (p.toCheck > 0) (p.checked.toFloat() / p.toCheck).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.indexBuildIntros, num(p.checked), num(p.toCheck)),
                style = typography.bodyMedium
            )
        } else if (p.phase == IndexPhase.CHECKING) {
            val frac = if (p.toCheck > 0) (p.checked.toFloat() / p.toCheck).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.indexBuildChecking, num(p.checked), num(p.toCheck), (frac * 100).toInt()),
                style = typography.bodyMedium
            )
        } else {
            val frac = if (p.categoriesKnown > 0) (p.categoriesDone.toFloat() / p.categoriesKnown).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.indexBuildCrawling, p.depth + 1, p.maxDepth + 1,
                    num(p.categoriesDone), num(p.categoriesKnown)
                ),
                style = typography.bodyMedium
            )
            Text(
                stringResource(R.string.indexBuildFound, num(p.articlesFound)),
                style = typography.bodyMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                R.string.indexBuildStats,
                clock(now - state.startedAtMs), num(p?.requests ?: 0)
            ),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.indexBuildLastResponse, (sinceActivity / 1000).coerceAtLeast(0)),
            style = typography.bodySmall,
            color = if (sinceActivity > 30_000) colorScheme.error else colorScheme.onSurfaceVariant
        )
        if (p?.status != null) {
            Spacer(Modifier.height(12.dp))
            Text(p.status, style = typography.bodyMedium, color = colorScheme.error)
        }
        if (usableNow) {
            Spacer(Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.indexBuildBackgroundReady),
                        style = typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onStartBrowsing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.indexBuildStartBrowsing))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onAddMore) {
                Text(stringResource(R.string.indexBuildAddMore))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun ResultView(
    state: IndexBuildState,
    labels: Map<String, String>,
    onDone: () -> Unit,
    onAddMore: () -> Unit
) {
    val ok = state.results.count { it.ok }
    val total = state.topicTotal
    val articles = state.results.sumOf { it.indexed }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            when {
                state.cancelled -> stringResource(R.string.indexResultCancelled)
                ok == total -> stringResource(R.string.indexResultSuccess)
                ok == 0 -> stringResource(R.string.indexResultFailed)
                else -> stringResource(R.string.indexResultPartial)
            },
            style = typography.headlineSmall,
            color = if (ok == total && !state.cancelled) colorScheme.primary else colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.indexResultSummary, ok, total, num(articles),
                clock(state.finishedAtMs - state.startedAtMs)
            ),
            style = typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.results, key = { it.topic }) { r -> ResultRow(r, labels[r.topic] ?: r.label) }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onAddMore) {
                Text(stringResource(R.string.indexBuildAddMore))
            }
            Button(onClick = onDone) { Text(stringResource(R.string.indexBuildOk)) }
        }
    }
}

@Composable
private fun ResultRow(r: TopicBuildResult, name: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            (if (r.ok) "\u2713 " else "\u2717 ") + name,
            style = typography.titleSmall,
            color = if (r.ok) colorScheme.onSurface else colorScheme.error
        )
        if (r.ok) {
            Text(
                stringResource(
                    R.string.indexResultTopicOk, num(r.indexed), num(r.withImages),
                    num(r.good), num(r.featured), clock(r.durationMs)
                ),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            if (r.indexed < 40) {
                Text(
                    stringResource(R.string.indexResultSmall, r.indexed),
                    style = typography.bodySmall,
                    color = colorScheme.error
                )
            }
            if (r.filteredOut > 0) {
                Text(
                    stringResource(R.string.indexResultFiltered, num(r.filteredOut)),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            if (r.incomplete) {
                Text(
                    stringResource(R.string.indexResultIncomplete, r.categoriesFailed, r.batchesFailed),
                    style = typography.bodySmall,
                    color = colorScheme.error
                )
            }
        } else {
            Text(r.error ?: "", style = typography.bodySmall, color = colorScheme.error)
        }
    }
}
