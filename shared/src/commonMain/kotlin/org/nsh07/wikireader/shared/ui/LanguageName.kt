package org.nsh07.wikireader.shared.ui

import com.wikirandomizerp.app.data.LanguageData

private val languageNames: Map<String, String> =
    LanguageData.langCodes.zip(LanguageData.langNames).toMap()

internal fun languageName(code: String): String = languageNames[code] ?: code
