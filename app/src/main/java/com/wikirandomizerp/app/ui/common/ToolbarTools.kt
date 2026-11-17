package com.wikirandomizerp.app.ui.common

import com.wikirandomizerp.app.R

/** One tool that can be shown in the bottom utility bar. */
data class ToolbarTool(
    val id: String,
    val labelRes: Int,
    val iconRes: Int,
    /** Only offered while the top search bar is hidden (the bar already provides it). */
    val needsHiddenSearchBar: Boolean = false
)

object ToolbarTools {
    val ALL = listOf(
        ToolbarTool("menu", R.string.moreOptions, R.drawable.menu, needsHiddenSearchBar = true),
        ToolbarTool("search", R.string.searchWikipedia, R.drawable.search, needsHiddenSearchBar = true),
        ToolbarTool("random", R.string.randomArticle, R.drawable.shuffle, needsHiddenSearchBar = true),
        ToolbarTool("language", R.string.settingWikipediaLanguage, R.drawable.translate),
        ToolbarTool("appearance", R.string.readerAppearance, R.drawable.format_size),
        ToolbarTool("tts", R.string.ttsReadAloud, R.drawable.play_arrow),
        ToolbarTool("share", R.string.sharePage, R.drawable.share),
        ToolbarTool("save", R.string.downloadArticle, R.drawable.download),
        ToolbarTool("votes", R.string.toolbarToolVotes, R.drawable.thumb_up),
        ToolbarTool("top", R.string.scroll_to_top, R.drawable.upward)
    )

    private val knownIds = ALL.map { it.id }.toSet()
    private val hiddenBarOnly = setOf("menu", "search", "random")

    /** Ids in the saved order (the order is the order of the bar), unknown ids dropped. */
    fun parse(csv: String): List<String> =
        csv.split(',').map { it.trim() }.filter { it in knownIds }.distinct()

    /**
     * The tools actually shown, in order. With the top search bar hidden the menu button is
     * always kept (it leads the bar if the person never placed it), otherwise the navigation
     * drawer would have no visible entry point. With the search bar shown, the tools it already
     * provides (menu, search, shuffle) are left out.
     */
    fun effective(csv: String, showSearchBar: Boolean): List<String> {
        val chosen = parse(csv)
        return if (showSearchBar) chosen.filter { it !in hiddenBarOnly }
        else if ("menu" in chosen) chosen else listOf("menu") + chosen
    }

    fun toggle(csv: String, id: String, on: Boolean, showSearchBar: Boolean): String {
        val list = effective(csv, showSearchBar).toMutableList()
        if (on) { if (id !in list) list.add(id) } else list.remove(id)
        return list.joinToString(",")
    }

    /** Moves a shown tool one place earlier (delta -1) or later (+1) in the bar. */
    fun move(csv: String, id: String, delta: Int, showSearchBar: Boolean): String {
        val list = effective(csv, showSearchBar).toMutableList()
        val from = list.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in list.indices) return list.joinToString(",")
        list[from] = list[to].also { list[to] = list[from] }
        return list.joinToString(",")
    }
}
