package studio.sculk.discord

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult

class ComponentIdTest {
    @Test
    fun `an id round-trips through its encoded form`() {
        val id = ComponentId.of("punish", "ban", "a1b2c3").getOrThrow()

        assertEquals("punish:ban:a1b2c3", id.encoded)
        assertEquals(id, ComponentId.parse(id.encoded))
    }

    @Test
    fun `an id at exactly the limit is allowed`() {
        // 8 + 1 + 91 = 100.
        val id = ComponentId.of("punishes", "x".repeat(91)).getOrThrow()

        assertEquals(ComponentId.MAX_LENGTH, id.encoded.length)
    }

    @Test
    fun `one character past the limit fails instead of being truncated`() {
        val result = ComponentId.of("punishes", "x".repeat(92))

        val failure = result as SculkResult.Failure
        assertTrue(failure.message.contains("101"), "the message should say how long it actually was: ${failure.message}")
        assertTrue(failure.message.contains("100"), "the message should name the limit: ${failure.message}")
    }

    @Test
    fun `a realistic profile plus uuid plus duration clears the limit, so this is not theoretical`() {
        val uuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        val result = ComponentId.of("daisyfilter-punish", "permanent-ban-with-appeal", uuid, uuid)

        assertTrue(result.isFailure)
    }

    @Test
    fun `a truncated record id would still have parsed, which is why truncation is refused`() {
        // The whole argument for failing rather than trimming: the trimmed form is indistinguishable
        // from a valid one, so the button renders, clicks, and then matches nothing.
        val trimmed = "punish:ban:0123456789abcdef0123456789abcdef01".take(ComponentId.MAX_LENGTH)

        assertNotNull(ComponentId.parse(trimmed))
    }

    @Test
    fun `a segment containing the separator is refused so the shape cannot be forged`() {
        assertTrue(ComponentId.of("punish", "ban:extra").isFailure)
    }

    @Test
    fun `an empty namespace is refused`() {
        assertTrue(ComponentId.of("").isFailure)
        assertNull(ComponentId.parse(":ban:1"))
    }

    @Test
    fun `another plugin's component id parses to a different namespace rather than throwing`() {
        val other = ComponentId.parse("someotherplugin:thing:1")

        assertNotNull(other)
        assertEquals("someotherplugin", other?.namespace)
    }

    @Test
    fun `an over-long id from the wire is rejected on the way back in`() {
        assertNull(ComponentId.parse("x".repeat(ComponentId.MAX_LENGTH + 1)))
    }

    @Test
    fun `a namespace with no parts is a valid id`() {
        val id = ComponentId.of("refresh").getOrThrow()

        assertEquals("refresh", id.encoded)
        assertEquals(emptyList<String>(), id.parts)
        assertNull(id.part(0))
    }
}
