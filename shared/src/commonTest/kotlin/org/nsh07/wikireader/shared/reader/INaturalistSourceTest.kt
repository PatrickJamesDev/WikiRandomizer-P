package org.nsh07.wikireader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class INaturalistSourceTest {
    @Test
    fun parsesFirstTaxonId() {
        val body = """{"results":[{"id":47126,"name":"Plantae"},{"id":1}]}"""
        assertEquals(47126L, parseINaturalistTaxonId(body))
        assertNull(parseINaturalistTaxonId("""{"results":[]}"""))
    }

    @Test
    fun urlsEncodeTitles() {
        assertTrue(INaturalistUrls.autocompleteUrl("Café & tea").contains("Caf%C3%A9%20%26%20tea"))
        assertEquals("https://www.inaturalist.org/taxa/47126", INaturalistUrls.taxonUrl(47126))
    }
}
