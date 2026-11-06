package org.nsh07.wikireader.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CladeParserTest {

    private val poaceaeTree = """
        {{clade|style=font-size:90%;line-height:75%;width:800px;
            |label1=
        |1={{clade
          |1={{clade
            |1={{clade
              |1={{clade
              |label1=[[PACMAD clade]]
              |1={{clade
          |1={{clade
               |1={{clade
              |1={{clade
            |1=[[Chloridoideae]] (1600)
            |2=[[Danthonioideae]] (300)
                 }}
              |2={{clade
            |1=[[Micrairoideae]] (200)
            |2=[[Arundinoideae]] (50)
                 }}
           }}
               |2=[[Panicoideae]] (3250)
             }}
          |2=[[Aristidoideae]] (350)
                 }}
              |label2=[[BOP clade]]
              |2={{clade
                    |1=[[Oryzoideae]] (110)
                    |2={{clade
                  |1=[[Bambusoideae]] – bamboos (1450)
                  |2=[[Pooideae]] (3850)
                }}
                 }}
                 }}
              |2=[[Puelioideae]] (11)
               }}
            |2=[[Pharoideae]] (13)
             }}
          |2=[[Anomochlooideae]] (4)
          }}
        }}
    """.trimIndent()

    @Test
    fun poaceaeTreeParses() {
        val root = parseClade(poaceaeTree)
        assertNotNull(root)
        assertTrue(root!!.children.isNotEmpty())
    }

    @Test
    fun blockTemplateAtKeepsTheWholeTree() {
        val prefix = "A cladogram shows subfamilies:\n\n"
        val text = prefix + poaceaeTree
        val block = blockTemplateAt(text, prefix.length)
        assertNotNull(block)
        assertEquals(poaceaeTree, block!!.blocks.single())
    }

    @Test
    fun extractCladeBlocksFindsTreeInLayoutTable() {
        val table = """
            {| class="wikitable"
            |-
            | Some caption
            |-
            | $poaceaeTree
            |}
        """.trimIndent()

        val clades = extractCladeBlocks(table)
        assertEquals(1, clades.size)
        assertNotNull(parseClade(clades.single()))
    }

    @Test
    fun athyriaceaeTreesInsideLayoutTableParse() {
        val wikitext = javaClass.classLoader!!.getResource("athyriaceae-wikitext.txt")!!.readText()
        val clades = extractCladeBlocks(wikitext)
        // The article has two cladograms side by side in one layout table.
        assertTrue("expected both Athyriaceae trees, found ${clades.size}", clades.size >= 2)
        clades.forEach { assertNotNull(parseClade(it)) }
    }

    @Test
    fun solanumTreeWithMultilineCommentStillParses() {
        val tree = javaClass.classLoader!!.getResource("solanum-clade.txt")!!.readText()
        val root = parseClade(tree)
        assertNotNull(root)
        // The multiline comment used to turn the whole Solanum clade into one leaf, so only a
        // handful of tips survived. A real tree has many more.
        assertTrue(countLeaves(root!!) >= 20)
    }

    private fun countLeaves(node: CladeNode): Int = when (node) {
        is CladeNode.Leaf -> 1
        is CladeNode.Branch -> node.children.sumOf { countLeaves(it.node) }
    }
}
