package com.wikirandomizerp.app.data

import java.util.Locale

// ISO country lookup needs java.util.Locale, which is not available in common code. Keeping it as
// an extension on the shared LanguageData object means the parser call sites stay unchanged while
// iOS can use langCodes/langNames/wikipediaNames from commonMain.
private val isoCountries: List<String> by lazy { Locale.getISOCountries().toList() }

private val countryNames: Map<String, String> by lazy {
    isoCountries.associateBy { Locale.Builder().setRegion(it).build().displayCountry }
}

private val alpha3Codes: Map<String, String> by lazy {
    isoCountries.associateBy { Locale.Builder().setRegion(it).build().isO3Country }
}

val LanguageData.countryNameToAlpha2CodeMap: Map<String, String>
    get() = countryNames

val LanguageData.alpha3CodeToAlpha2CodeMap: Map<String, String>
    get() = alpha3Codes
