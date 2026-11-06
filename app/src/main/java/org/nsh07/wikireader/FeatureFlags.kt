package org.nsh07.wikireader

/**
 * Product switches that hide unfinished features without deleting their code, routes or data.
 * Curators are kept wired end-to-end so they can be re-enabled with a single change.
 */
object FeatureFlags {
    const val CURATORS_ENABLED = false
}
