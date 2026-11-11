package com.wikirandomizerp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wikirandomizerp.app.data.RelDoc
import com.wikirandomizerp.app.data.RelevanceModel
import com.wikirandomizerp.app.data.parseSections
import com.wikirandomizerp.app.parser.cleanUpWikitext
import com.wikirandomizerp.app.parser.splitHtmlSections
import kotlin.system.measureNanoTime

/**
 * Fast, deterministic performance checks for the two CPU-heavy paths that decide whether an
 * article feels instant: wikitext cleanup + section splitting, and HTML section splitting.
 *
 * These are deliberately generous bounds. They are not micro-benchmarks for a specific phone; they
 * catch a catastrophic regression (an accidental O(n^2) loop, a regex that stops terminating) while
 * still passing on a slow CI machine. The measured numbers are printed so a release build can be
 * compared over time.
 */
class PerformanceBenchmarkTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing test resource $name" }
            .readText()

    @Test
    fun wikitextCleanupAndSplittingBenchmark() {
        val raw = resource("athyriaceae-wikitext.txt")
        require(raw.length > 1_000)

        // Warm up the JIT and the regex caches before measuring.
        repeat(3) { parseSections(cleanUpWikitext(raw)) }

        val iterations = 25
        var sections = 0
        val ns = measureNanoTime {
            repeat(iterations) {
                sections = parseSections(cleanUpWikitext(raw)).size
            }
        }
        val ms = ns / 1_000_000.0 / iterations
        println(
            "BENCH wikitext clean+split: %.2f ms/iteration, %d sections, %d chars"
                .format(ms, sections, raw.length)
        )
        assertTrue("the fixture should produce sections", sections > 0)
        assertTrue(
            "wikitext clean+split regressed to %.2f ms/iteration".format(ms),
            ms < 250.0
        )
    }

    @Test
    fun htmlSectionSplittingBenchmark() {
        val html = resource("chasmogamy.html")
        repeat(3) { splitHtmlSections(html) }

        val iterations = 25
        var sections = 0
        val ns = measureNanoTime {
            repeat(iterations) { sections = splitHtmlSections(html).size }
        }
        val ms = ns / 1_000_000.0 / iterations
        println(
            "BENCH html split: %.2f ms/iteration, %d sections, %d chars"
                .format(ms, sections, html.length)
        )
        assertTrue("the fixture should produce sections", sections > 1)
        assertTrue("HTML splitting regressed to %.2f ms/iteration".format(ms), ms < 250.0)
    }

    @Test
    fun relevanceScoringBenchmark() {
        // A 600-article field is larger than a typical topic library page, so this also exercises
        // the sparse-vector path rather than a tiny best case.
        val docs = (1..600).map { i ->
            RelDoc(
                key = "k$i",
                title = "Article $i",
                descr = "A description about topic ${i % 24}, science and history",
                intro = "Intro text about topic ${i % 17}, research and the natural world $i"
            )
        }
        val query = listOf("science", "topic", "research", "history")
        repeat(3) { RelevanceModel.score(docs, query, emptyList()) }

        val iterations = 10
        var scored = 0
        val ns = measureNanoTime {
            repeat(iterations) {
                scored = RelevanceModel.score(docs, query, emptyList()).rel.size
            }
        }
        val ms = ns / 1_000_000.0 / iterations
        println("BENCH relevance score: %.2f ms/iteration, %d docs, %d scored".format(ms, docs.size, scored))
        assertEquals(docs.size, scored)
        assertTrue("relevance scoring regressed to %.2f ms/iteration".format(ms), ms < 500.0)
    }
}
