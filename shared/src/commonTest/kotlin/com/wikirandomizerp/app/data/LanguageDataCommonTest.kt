package com.wikirandomizerp.app.data

import kotlin.test.Test
import kotlin.test.assertTrue

class LanguageDataCommonTest {
    @Test
    fun sharedLanguageListsAreUsableOnEveryTarget() {
        assertTrue(LanguageData.langCodes.contains("en"))
        assertTrue(LanguageData.rtlLangCodes.contains("ar"))
        assertTrue(LanguageData.langNames.isNotEmpty())
        assertTrue(LanguageData.wikipediaNames.isNotEmpty())
        assertTrue(LanguageData.fifaCodeToAlpha3CodeMap.isNotEmpty())
    }
}