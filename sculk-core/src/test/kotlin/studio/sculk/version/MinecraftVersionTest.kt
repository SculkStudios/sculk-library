package studio.sculk.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow

class MinecraftVersionTest {
    private fun v(text: String): MinecraftVersion = MinecraftVersion.parse(text).getOrThrow()

    @ParameterizedTest
    @ValueSource(strings = ["1.21", "1.21.4", "1.21.11", "26.1", "26.1.2", "26.3", "26.3-pre-2", "26.3-rc-1"])
    fun `every supported spelling survives a round trip`(text: String) {
        assertEquals(text, v(text).toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1", "1.", "1.21.x", "26w41a", "1.21.4.1", "-1.21", "1.21-pre-0", "1.21-beta-1", "v1.21"])
    fun `anything that is not a release or pre-release is refused`(text: String) {
        val failure = MinecraftVersion.parse(text) as SculkFailure.InvalidInput
        assertEquals("'$text'", failure.received)
    }

    @Test
    fun `a missing patch component is zero`() {
        assertEquals(v("1.21"), v("1.21.0"))
        assertEquals("1.21", v("1.21.0").toString())
        assertEquals(v("1.21").hashCode(), v("1.21.0").hashCode())
    }

    @Test
    fun `every year-based release orders after every legacy release`() {
        assertTrue(v("26.1") > v("1.21.11"))
        assertTrue(v("1.21.11") > v("1.21.4"))
        assertTrue(v("1.21.10") > v("1.21.9"))
    }

    @Test
    fun `pre-releases precede release candidates which precede the release`() {
        val ordered = listOf("26.3-rc-2", "26.3", "26.2", "26.3-pre-1", "26.3-rc-1", "26.3-pre-2").map(::v).sorted()
        assertEquals(listOf("26.2", "26.3-pre-1", "26.3-pre-2", "26.3-rc-1", "26.3-rc-2", "26.3"), ordered.map(MinecraftVersion::toString))
    }

    @Test
    fun `Paper's unhyphenated pre-release spelling is accepted and normalised`() {
        assertEquals(v("26.3-pre-2"), v("26.3-pre2"))
        assertEquals("26.3-pre-2", v("26.3-pre2").toString())
    }

    @Test
    fun `release drops only the pre-release qualifier`() {
        assertEquals(v("26.3"), v("26.3-rc-1").release())
        assertTrue(v("26.3-rc-1").isPreRelease)
        assertFalse(v("26.3-rc-1").release().isPreRelease)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(v("1.21.4"), v(" 1.21.4\n"))
    }

    @Test
    fun `negative components are a programming error`() {
        assertThrows<IllegalArgumentException> { MinecraftVersion.of(1, -21) }
    }
}
