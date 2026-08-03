package studio.sculk.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal

@OptIn(SculkInternal::class)
class SculkBannerTest {
    @Test
    fun `a utf-8 console gets the block art`() {
        assertTrue(SculkBanner.artFor("UTF-8").any { it.contains("█") })
    }

    @Test
    fun `an ascii console falls back rather than printing question marks`() {
        val art = SculkBanner.artFor("US-ASCII")

        // A stripped locale is exactly the setup least able to investigate a console full of
        // replacement characters, so the fallback has to be automatic.
        assertTrue(art.none { line -> line.any { it.code > 127 } }, "fallback art is not ascii: $art")
    }

    @Test
    fun `an unknown encoding name falls back instead of throwing`() {
        // Charset.forName throws on a malformed name, and a plugin must not fail to enable over the
        // value of a system property that describes the console.
        val art = SculkBanner.artFor("not-a-real-charset")

        assertTrue(art.none { line -> line.any { it.code > 127 } })
    }

    @Test
    fun `both art sets are the same height so facts line up`() {
        // Facts are printed beside the art by index; a shorter fallback silently drops the last one.
        assertEquals(
            SculkBanner.artFor("UTF-8").size,
            SculkBanner.artFor("US-ASCII").size,
        )
    }

    @Test
    fun `each art set has lines of one width so the fact column is straight`() {
        for (set in listOf(BannerArt.SCULK.lines, BannerArt.SCULK.fallback)) {
            assertEquals(1, set.map { it.length }.distinct().size, "ragged art: $set")
        }
    }

    @Test
    fun `art whose fallback is a different height is refused at construction`() {
        // Rather than at the one moment it matters, which is on somebody else's console.
        assertThrows(IllegalArgumentException::class.java) {
            BannerArt(listOf("aa", "bb"), listOf("cc"))
        }
    }

    @Test
    fun `custom art replaces sculk's rather than being drawn beside it`() {
        val art = BannerArt(listOf("MINE"))

        assertEquals(listOf("MINE"), SculkBanner.artFor("UTF-8", art))
        assertEquals(listOf("MINE"), SculkBanner.artFor("US-ASCII", art))
    }

    @Test
    fun `a fact past the last line of art is still printed`() {
        // Iterating the art dropped every fact past its height, so a plugin adding two facts of its
        // own silently lost the framework's own "Started in" row and never saw a reason why.
        val facts = (1..9).map { "Key$it" to "Value$it" }

        val rows = SculkBanner.layout(listOf("##", "##"), facts)

        assertEquals(facts, rows.mapNotNull { it.second })
    }

    @Test
    fun `a fact printed past the art is indented to the same column as the rest`() {
        val rows = SculkBanner.layout(listOf("####"), listOf("A" to "1", "B" to "2"))

        assertEquals("####", rows[0].first)
        assertEquals("    ", rows[1].first, "the overflow row must hold the fact column")
    }

    @Test
    fun `art taller than the fact list still draws every line`() {
        val rows = SculkBanner.layout(BannerArt.SCULK.lines, listOf("Only" to "one"))

        assertEquals(BannerArt.SCULK.lines.size, rows.size)
        assertEquals(BannerArt.SCULK.lines, rows.map { it.first })
    }
}
