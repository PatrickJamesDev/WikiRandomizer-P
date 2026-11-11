package com.wikirandomizerp.app.parser

// Compiled once at class-load time — never inside a hot parse loop.
// DOT_MATCHES_ALL matters: templates are frequently broken over a multiline comment
// (`|1=<!--\n-->{{clade ...}}`). Without it the comment survived and hid the nested tree.
private val htmlCommentRegex =
    "<!--.*?-->".toRegex(RegexOption.DOT_MATCHES_ALL)
private val nobilityTableRegex =
    "\\{\\{nobility table header.*?\\}\\}"
        .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/**
 * Remove/simplify parts of wikitext
 *
 * @param wikitext Source Wikitext to clean up
 */
fun cleanUpWikitext(wikitext: String): String {
    var text = wikitext.replace(htmlCommentRegex, "")
    // Most articles have no nobility table: skip the expensive regex scan entirely.
    if (text.contains("nobility table header", ignoreCase = true)) {
        text = text.replace(nobilityTableRegex, "{| class=\"wikitable\"\n")
    }
    return text
        .replace("== \n", "==\n")
}
