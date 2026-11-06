@file:OptIn(ExperimentalJsExport::class)

package org.nsh07.wikireader.shared

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tiny stable facade for the browser PWA.
 *
 * Keep this surface to strings/numbers: it is consumed from plain JavaScript, and the PWA ships
 * a small JavaScript fallback if a browser cannot instantiate WasmGC modules (iOS < 18).
 */
@JsExport
@JsName("articleUrl")
fun wikiArticleUrl(lang: String, title: String): String = WikiUrls.articleUrl(lang, title)

@JsExport
@JsName("mobileArticleUrl")
fun wikiMobileArticleUrl(lang: String, title: String): String = WikiUrls.mobileArticleUrl(lang, title)

@JsExport
@JsName("summaryUrl")
fun wikiSummaryUrl(lang: String, title: String): String = WikiUrls.summaryUrl(lang, title)

@JsExport
@JsName("randomSummaryUrl")
fun wikiRandomSummaryUrl(lang: String): String = WikiUrls.randomSummaryUrl(lang)

@JsExport
@JsName("searchUrl")
fun wikiSearchUrl(lang: String, query: String): String = WikiUrls.searchUrl(lang, query)

@JsExport
@JsName("filePathUrl")
fun wikiFilePathUrl(lang: String, fileName: String, width: Int): String =
    WikiUrls.filePathUrl(lang, fileName, width)

@JsExport
@JsName("normalizeTitle")
fun wikiNormalizeTitle(title: String): String = WikiUrls.normalizeTitle(title)

@JsExport
@JsName("encodeTitle")
fun wikiEncodeTitle(title: String): String = WikiUrls.encodeTitle(title)

@JsExport
@JsName("plainText")
fun wikiPlainText(html: String): String = WikiText.plainText(html)

@JsExport
@JsName("snippet")
fun wikiSnippet(html: String, maxChars: Int): String = WikiText.snippet(html, maxChars)

@JsExport
@JsName("readingMinutes")
fun wikiReadingMinutes(text: String): Int = WikiText.readingMinutes(text)
