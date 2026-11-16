package org.nsh07.wikireader.shared.ui.topics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.data.TOPIC_GROUPS
import com.wikirandomizerp.app.data.WikiTopic
import com.wikirandomizerp.app.data.WikiTopicGroup
import org.nsh07.wikireader.shared.reader.TopicOption
import org.nsh07.wikireader.shared.reader.topicCatalog

@Composable
fun TopicsScreen(
    selected: WikiTopic?,
    onSelect: (WikiTopic) -> Unit,
    onClear: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    val normalized = query.trim()
    val matches = remember(normalized) {
        if (normalized.length < 2) emptyList() else topicCatalog.filter { it.matches(normalized) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        SelectedTopicCard(selected = selected, onClear = onClear)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search topics") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (normalized.length >= 2) {
            if (matches.isEmpty()) {
                TopicMessage(
                    title = "No topics found",
                    description = "Try a broader word, such as \"physics\" or \"film\".",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "${matches.size} topics",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    items(matches, key = { "${it.topic.category}|${it.topic.label}" }) { option ->
                        TopicResultRow(
                            option = option,
                            selected = selected == option.topic,
                            onSelect = { onSelect(option.topic) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "${topicCatalog.size} topics across ${TOPIC_GROUPS.size} groups",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(TOPIC_GROUPS, key = { it.label }) { group ->
                    TopicGroupCard(
                        group = group,
                        expanded = group.label in expandedGroups,
                        onToggle = {
                            expandedGroups = if (group.label in expandedGroups) {
                                expandedGroups - group.label
                            } else {
                                expandedGroups + group.label
                            }
                        },
                        selected = selected,
                        onSelect = onSelect
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SelectedTopicCard(selected: WikiTopic?, onClear: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selected == null) "All topics" else "Reading topic",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = selected?.label ?: "Random across Wikipedia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onClear) {
                Text(if (selected == null) "Surprise me" else "All topics")
            }
        }
    }
}

@Composable
private fun TopicGroupCard(
    group: WikiTopicGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    selected: WikiTopic?,
    onSelect: (WikiTopic) -> Unit
) {
    val topicCount = group.subgroups.sumOf { it.children.size }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$topicCount topics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    group.subgroups.forEachIndexed { index, subgroup ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        }
                        Text(
                            text = subgroup.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            subgroup.children.forEach { topic ->
                                if (selected == topic) {
                                    FilledTonalButton(onClick = { onSelect(topic) }) {
                                        Text(topic.label)
                                    }
                                } else {
                                    TextButton(onClick = { onSelect(topic) }) {
                                        Text(topic.label)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicResultRow(
    option: TopicOption,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = option.topic.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${option.group} · ${option.subgroup}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selected) {
                Text(
                    text = "Selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun TopicOption.matches(query: String): Boolean =
    topic.label.contains(query, ignoreCase = true) ||
        topic.category.contains(query, ignoreCase = true) ||
        group.contains(query, ignoreCase = true) ||
        subgroup.contains(query, ignoreCase = true)

@Composable
private fun TopicMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
