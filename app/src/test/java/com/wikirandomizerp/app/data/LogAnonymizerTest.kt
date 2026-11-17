package com.wikirandomizerp.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogAnonymizerTest {
    @Test fun removesTitlesAndSearchWords() {
        val out = LogAnonymizer.anonymize(
            "LOAD START title=Secret Article random=false lang=en\nevicted 'Private Topic' (en)\n" +
                "GET https://en.wikipedia.org/w/api.php?action=query&titles=Hidden&format=json mail a@b.com"
        )
        assertFalse(out.contains("Secret"))
        assertFalse(out.contains("Private"))
        assertFalse(out.contains("Hidden"))
        assertFalse(out.contains("a@b.com"))
        assertTrue(out.contains("random=false"))
        assertTrue(out.contains("action=query"))
    }
}
