package com.wikirandomizerp.app.ui.homeScreen

import android.net.Uri
import androidx.compose.ui.text.AnnotatedString
import com.wikirandomizerp.app.parser.HTML_INFOBOX_MARKER

/**
 * Helpers for the "View on iNaturalist" action. iNaturalist is a citizen-science site for
 * observations of living organisms, so the action only appears on articles that are about a
 * taxon (plants, animals, fungi). Its search accepts the article title directly, e.g.
 * "Capsicum annuum" or "Amanita muscaria".
 */
private val taxoboxTemplates = listOf(
    "{{taxobox", "{{automatic taxobox", "{{speciesbox", "{{subspeciesbox",
    "{{infraspeciesbox", "{{hybridbox", "{{ichnobox", "{{oobox", "{{paraphyletic group"
)

private val organismDescriptions = listOf(
    "species of", "subspecies of", "genus of", "variety of", "form of"
)

fun inaturalistSearchUrl(title: String): String =
    "https://www.inaturalist.org/taxa/search?q=" + Uri.encode(title.trim())

/**
 * True when [extract] contains a taxobox (the REST HTML path and any remaining wikitext one) or
 * [description] reads like a taxonomic short description ("Species of flowering plant").
 */
fun isOrganismArticle(
    extract: List<List<AnnotatedString>>,
    description: String?
): Boolean {
    for (section in extract) {
        for (part in section) {
            val text = part.text.trimStart()
            if (text.startsWith(HTML_INFOBOX_MARKER, ignoreCase = true) &&
                text.contains("\u001Ftaxobox", ignoreCase = true)
            ) return true
            val lower = text.take(64).lowercase()
            if (taxoboxTemplates.any { lower.startsWith(it) }) return true
        }
    }
    val desc = description?.trim()?.lowercase() ?: return false
    return organismDescriptions.any { desc.startsWith(it) || desc.contains(" $it") }
}
