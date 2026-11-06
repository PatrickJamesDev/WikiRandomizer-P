package org.nsh07.wikireader.ui.statsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.ReadingStats

/** "3 h 5 min", "12 min" or "40 s" for a number of seconds. */
@Composable
private fun durationText(totalSeconds: Long): String {
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    return when {
        hours > 0 -> stringResource(R.string.statsHoursMinutes, hours, minutes)
        totalSeconds >= 60 -> stringResource(R.string.statsMinutes, minutes)
        else -> stringResource(R.string.statsSeconds, totalSeconds.toInt())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onGetStats: suspend () -> ReadingStats,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stats by remember { mutableStateOf<ReadingStats?>(null) }
    LaunchedEffect(Unit) {
        stats = runCatching { onGetStats() }.getOrDefault(ReadingStats())
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics)) },
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
        val current = stats
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StatGroup(
                        title = stringResource(R.string.statsGroupReading),
                        rows = listOf(
                            stringResource(R.string.statsTimeReading) to durationText(current.readingSeconds),
                            stringResource(R.string.statsToday) to durationText(current.readingTodaySeconds),
                            stringResource(R.string.statsLast7Days) to durationText(current.readingWeekSeconds),
                            stringResource(R.string.statsSessions) to current.readingSessions.toString(),
                            stringResource(R.string.statsAverageSession) to durationText(current.averageSessionSeconds),
                            stringResource(R.string.statsLongestSession) to durationText(current.longestSessionSeconds),
                            stringResource(R.string.statsBestDay) to durationText(current.bestDaySeconds)
                        )
                    )
                }
                item {
                    StatGroup(
                        title = stringResource(R.string.statsGroupArticles),
                        rows = listOf(
                            stringResource(R.string.statsArticlesOpened) to current.articlesOpened.toString(),
                            stringResource(R.string.statsToday) to current.articlesToday.toString(),
                            stringResource(R.string.statsLast7Days) to current.articlesWeek.toString(),
                            stringResource(R.string.statsHistory) to current.articlesInHistory.toString()
                        )
                    )
                }
                item {
                    StatGroup(
                        title = stringResource(R.string.statsGroupHabit),
                        rows = listOf(
                            stringResource(R.string.statsStreak) to
                                    stringResource(R.string.statsDays, current.currentStreakDays),
                            stringResource(R.string.statsActiveDays) to
                                    stringResource(R.string.statsDays, current.activeDays)
                        )
                    )
                }
                item {
                    StatGroup(
                        title = stringResource(R.string.statsGroupLibrary),
                        rows = listOf(
                            stringResource(R.string.statsSavedArticles) to current.savedArticles.toString(),
                            stringResource(R.string.statsHighlights) to current.savedHighlights.toString(),
                            stringResource(R.string.statsVocabulary) to current.vocabularyWords.toString()
                        )
                    )
                }
                item {
                    Text(
                        stringResource(R.string.statsFootnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatGroup(title: String, rows: List<Pair<String, String>>) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow(label, value)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
