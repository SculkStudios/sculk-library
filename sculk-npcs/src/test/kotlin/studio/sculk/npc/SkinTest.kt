package studio.sculk.npc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.getOrNull
import java.util.Base64
import java.util.UUID

class SkinTest {
    private val value = Base64.getEncoder().encodeToString(
        """{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/1a2b"}}}""".toByteArray(),
    )

    @Test
    fun `textures from configuration are checked before an NPC wears them`() {
        assertTrue(Skin.parse("not base64 at all!") is SculkFailure.InvalidInput)
        val noTextures = Base64.getEncoder().encodeToString("""{"name":"Steve"}""".toByteArray())
        assertTrue(Skin.parse(noTextures) is SculkFailure.InvalidInput)
        assertTrue(Skin.parse(value, signature = " ") is SculkFailure.InvalidInput)
        val parsed = Skin.parse("  $value\n", signature = "c2lnbmVk").getOrNull() as Skin.Textures
        assertEquals(value, parsed.value)
        assertEquals("c2lnbmVk", parsed.signature)
    }

    @Test
    fun `textures written in code that are not a textures property are a programming error`() {
        assertThrows<IllegalArgumentException> { Skin.textures("plainly wrong") }
        assertEquals(Skin.parse(value).getOrNull(), Skin.textures(value))
    }

    @Test
    fun `skins are equal when they show the same thing`() {
        val id = UUID.randomUUID()
        assertEquals(Skin.player(id), Skin.player(id))
        assertNotEquals(Skin.player(id), Skin.player(UUID.randomUUID()))
        assertNotEquals(Skin.textures(value), Skin.textures(value, "c2lnbmVk"))
        assertFalse(Skin.textures(value).toString().contains(value), "a skin's text does not repeat the whole property")
    }
}
