package com.wikirandomizerp.app.data

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wikirandomizerp.app.parser.ReferenceData

class RobustnessFixesTest {

    @Test
    fun toColor_malformedValueFallsBackInsteadOfCrashing() {
        assertEquals(Color.White, "".toColor())
        assertEquals(Color.White, "not a color".toColor())
        assertEquals(Color.White, "Color(1.0, x, 3".toColor())
    }

    @Test
    fun toColor_stillParsesValidValues() {
        val c = "Color(0.25, 0.5, 0.75, 1.0, sRGB IEC61966-2.1)".toColor()
        assertEquals(0.25f, c.red, 0.01f)
        assertEquals(0.5f, c.green, 0.01f)
        assertEquals(0.75f, c.blue, 0.01f)
    }

    @Test
    fun parseSections_headingAsLastElementDoesNotThrow() {
        // An ignorable heading with nothing after it used to throw IndexOutOfBounds.
        val out = parseSections("Intro text\n==External links")
        assertEquals(listOf("Intro text"), out)
    }

    @Test
    fun parseSections_normalArticleUnchanged() {
        val out = parseSections("Intro\n== History ==\nSome history\n== Geography ==\nSome geography")
        assertEquals(5, out.size)
        assertEquals("Intro", out[0])
        assertEquals(" History ", out[1])
    }

    @Test
    fun referenceNumbers_areSequentialAndNamedRefsAreReused() {
        ReferenceData.refCount = 1
        ReferenceData.refList.clear()
        ReferenceData.refListCount.clear()
        ReferenceData.refListIndex.clear()

        assertEquals(1, ReferenceData.nextInlineRef("first"))
        assertEquals(2, ReferenceData.numberForNamedRef("shared"))
        assertEquals(3, ReferenceData.nextInlineRef("second"))
        // same named reference again: same number, no new entry
        assertEquals(2, ReferenceData.numberForNamedRef("shared"))
        assertEquals(4, ReferenceData.refCount)
        // every number is present in the reference list (named refs used to be missing)
        assertEquals(listOf(1, 2, 3), ReferenceData.refListIndex.keys.sorted())
        assertEquals("shared", ReferenceData.refListIndex[2])
    }

    @Test
    fun referenceNumbers_areUniqueUnderConcurrentUse() {
        ReferenceData.refCount = 1
        ReferenceData.refListCount.clear()
        ReferenceData.refListIndex.clear()

        val threads = (1..8).map { t ->
            Thread { repeat(250) { ReferenceData.nextInlineRef("t$t-$it") } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(2001, ReferenceData.refCount)
        assertEquals(2000, ReferenceData.refListIndex.size)
        assertTrue(ReferenceData.refListIndex.keys.sorted() == (1..2000).toList())
    }

    @Test(timeout = 5_000)
    fun interestParser_longWhitespaceRunDoesNotHang() {
        // 20,000 blanks took ~11 s (quadratic regex backtracking); "i am" + blanks never finished.
        InterestParser.parse(" ".repeat(20_000))
        InterestParser.parse("i am" + " ".repeat(20_000) + "interested")
        InterestParser.parse("show me" + " ".repeat(20_000))
    }

    @Test
    fun relevance_unrelatedArticleGetsZeroCoverage() {
        val docs = listOf(
            RelDoc(
                "Moche", "Moche culture", "Archaeological culture in Peru",
                "The Moche were an Andean civilization on the north coast of Peru."
            ),
            RelDoc(
                "Inca", "Inca Empire", "Empire in South America",
                "The Inca Empire was the largest empire in pre-Columbian America."
            ),
            RelDoc(
                "Egypt", "Ancient Egypt", "Civilization in Northeast Africa",
                "Ancient Egypt was a civilization of ancient Northeast Africa."
            ),
            RelDoc(
                "Caesar", "Julius Caesar", "Roman general",
                "Gaius Julius Caesar was a Roman general and statesman."
            )
        )

        val result = RelevanceModel.score(
            docs, listOf("South American civilizations"), emptyList()
        )

        // No shared content word: exactly the unrelated article the random button must not show.
        assertEquals(0, result.rel["Caesar"])
        assertEquals(0, result.cov["Caesar"])
        // Coverage is IDF-weighted: matching the distinctive "South American" words beats matching
        // only the generic word "civilization".
        assertTrue(result.cov.getValue("Inca") > result.cov.getValue("Egypt"))
    }

    @Test
    fun interestParser_normalInputIsUnchanged() {
        val a = InterestParser.parse(
            "I am interested in succulent plants of North America, as well as muscle cars, " +
                    "but not cigarettes or nicotine pouches"
        )
        assertEquals(listOf("succulent plants of North America", "muscle cars"), a.phrases)
        assertEquals(listOf("cigarettes", "nicotine pouches"), a.exclusions)
        // runs of blanks collapse but a newline still separates interests
        assertEquals(listOf("cacti", "succulents"), InterestParser.parse("cacti   and\n\n\nsucculents").phrases)
    }
}
