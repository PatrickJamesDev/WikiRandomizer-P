package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.TOPIC_GROUPS
import com.wikirandomizerp.app.data.WikiTopic

data class TopicOption(
    val group: String,
    val subgroup: String,
    val topic: WikiTopic
)

internal val topicCatalog: List<TopicOption> = TOPIC_GROUPS.flatMap { group ->
    group.subgroups.flatMap { subgroup ->
        subgroup.children.map { topic ->
            TopicOption(
                group = group.label,
                subgroup = subgroup.label,
                topic = topic
            )
        }
    }
}

internal fun findTopicByCategory(category: String): WikiTopic? =
    topicCatalog.firstOrNull { it.topic.category == category }?.topic
