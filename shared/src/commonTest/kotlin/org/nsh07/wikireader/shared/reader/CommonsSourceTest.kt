package org.nsh07.wikireader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommonsSourceTest {
    @Test
    fun parsesImagesAndSkipsNonImageFiles() {
        val json = """
            {
              "query": {
                "pages": [
                  {
                    "title": "File:Orange.jpg",
                    "imageinfo": [{
                      "thumburl": "https://example.org/thumb.jpg",
                      "url": "https://example.org/orange.jpg",
                      "descriptionurl": "https://commons.example.org/orange"
                    }]
                  },
                  {
                    "title": "File:Notes.pdf",
                    "imageinfo": [{
                      "thumburl": "https://example.org/notes.pdf",
                      "url": "https://example.org/notes.pdf"
                    }]
                  }
                ]
              }
            }
        """.trimIndent()

        val images = parseCommonsImages(json)
        assertEquals(1, images.size)
        assertEquals("Orange.jpg", images.first().title)
        assertTrue(images.first().thumbnailUrl!!.endsWith("thumb.jpg"))
    }
}
