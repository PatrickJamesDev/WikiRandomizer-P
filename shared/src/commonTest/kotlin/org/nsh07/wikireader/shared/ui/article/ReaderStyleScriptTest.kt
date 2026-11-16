package org.nsh07.wikireader.shared.ui.article

import kotlin.test.Test
import kotlin.test.assertTrue

class ReaderStyleScriptTest {
    @Test
    fun appliesSpacingMarginsAndFamily() {
        val script = readerStyleScript(
            lineSpacingPercent = 150,
            horizontalMarginDp = 20,
            justifyText = true,
            sepia = false,
            fontFamily = "serif"
        )
        assertTrue(script.contains("line-height:1.5"))
        assertTrue(script.contains("padding-left:20px"))
        assertTrue(script.contains("text-align:justify"))
        assertTrue(script.contains("font-family:Georgia"))
    }

    @Test
    fun usesSafeFallbacksForOutOfRangeValues() {
        val script = readerStyleScript(500, 100, false, true, "custom")
        assertTrue(script.contains("line-height:2.0"))
        assertTrue(script.contains("padding-left:64px"))
        assertTrue(script.contains("background:#F4ECD8"))
        assertTrue(script.contains("sans-serif"))
    }
}
