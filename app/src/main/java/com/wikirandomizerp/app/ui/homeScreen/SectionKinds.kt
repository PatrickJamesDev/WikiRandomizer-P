package com.wikirandomizerp.app.ui.homeScreen

import java.util.Locale

/**
 * Section headings that hold citations or footnotes. They are long, rarely read straight through,
 * and used to push the rest of the article away, so they start collapsed whatever the global
 * "expand sections" setting says. The user can still open them with a tap.
 *
 * Wikipedia names this section differently per language, so the common titles are listed.
 */
private val referenceSectionTitles = setOf(
    "references", "reference", "notes", "footnotes", "citations", "notes and references",
    "references and notes", "notes and citations", "references and footnotes",
    "footnotes and references", "sources and references",
    // German
    "einzelnachweise", "anmerkungen", "belege", "fußnoten", "fussnoten", "quellen und anmerkungen",
    // French
    "références", "notes et références", "notes et références", "références et notes", "notes",
    // Spanish / Portuguese
    "referencias", "notas", "notas y referencias", "referências", "notas e referências",
    "notas e referencias",
    // Italian
    "note", "riferimenti", "note e riferimenti", "bibliografia e note",
    // Dutch
    "referenties", "voetnoten", "noten", "bronnen",
    // Nordic
    "källor", "noter", "referenser", "kilder", "lähteet", "viitteet",
    // Eastern Europe
    "przypisy", "odnośniki", "примечания", "сноски", "источники", "посилання", "примітки",
    "jegyzetek", "források", "poznámky", "odkazy", "referințe", "note și referințe",
    // Turkish / others
    "kaynakça", "dipnotlar", "notlar", "daftar pustaka", "catatan",
    // CJK
    "脚注", "注釈", "出典", "参照", "注释", "参考资料", "参考文献", "각주", "참고 문헌",
    // Arabic / Hebrew
    "مراجع", "المراجع", "هوامش", "ملاحظات", "הערות שוליים", "הפניות"
)

private val tagRegex = Regex("<[^>]*>")

/** True when [title] (possibly containing HTML tags or a trailing colon) is a references/footnotes heading. */
internal fun isReferencesTitle(title: String): Boolean {
    val cleaned = title
        .replace(tagRegex, "")
        .replace('\u00A0', ' ')
        .trim()
        .trimEnd(':', '.')
        .trim()
        .lowercase(Locale.ROOT)
    return cleaned.isNotEmpty() && cleaned in referenceSectionTitles
}
