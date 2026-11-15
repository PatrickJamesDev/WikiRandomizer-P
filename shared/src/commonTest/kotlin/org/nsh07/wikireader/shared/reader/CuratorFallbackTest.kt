package org.nsh07.wikireader.shared.reader

import kotlin.test.Test
import kotlin.test.assertTrue

class CuratorFallbackTest {
    @Test
    fun bundledCuratorsDecode() {
        val load = fallbackCuratorLoad()
        assertTrue(load.fromFallback)
        assertTrue(load.feed.curators.isNotEmpty())
        assertTrue(load.feed.curators.all { it.picks.isNotEmpty() })
    }
}
