package org.nsh07.wikireader.shared.ui

import com.wikirandomizerp.app.data.LanguageData

internal fun languageName(code: String): String {
    val index = LanguageData.langCodes.indexOf(code)
    return if (index >= 0) LanguageData.langNames[index] else code
}
