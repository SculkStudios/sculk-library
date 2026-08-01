package studio.sculk.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal

@OptIn(SculkInternal::class)
class SculkBannerTest {
    @Test
    fun `a utf-8 console gets the block art`() {
        assertTrue(SculkBanner.artFor("UTF-8").any { it.contains("▄") })
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
}
