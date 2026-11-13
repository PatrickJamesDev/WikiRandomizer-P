package org.nsh07.wikireader.shared.settings

/**
 * Small key/value boundary for reader preferences.
 *
 * iOS uses NSUserDefaults; Android will wrap the existing preference repository. Keeping the
 * contract this narrow lets the shared UI persist choices without depending on a platform API.
 */
interface SettingsStore {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
}

object SettingsKeys {
    const val LANGUAGE = "wikiLanguage"
    const val THEME = "wikiTheme"
    const val TOPIC = "wikiSelectedTopic"
    const val SAVED_ARTICLES = "wikiSavedArticles"
    const val ARTICLE_HISTORY = "wikiArticleHistory"
}
