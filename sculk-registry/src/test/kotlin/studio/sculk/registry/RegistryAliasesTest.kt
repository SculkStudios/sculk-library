package studio.sculk.registry

import net.kyori.adventure.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The alias table as lookups read it, without a server. */
class RegistryAliasesTest {
    private val item = Key.key("minecraft:item")
    private val entity = Key.key("minecraft:entity_type")
    private val sound = Key.key("minecraft:sound_event")

    @Test
    fun `a renamed key leads to its new name, and the new name back to the old`() {
        assertEquals(listOf(Key.key("minecraft:iron_chain")), RegistryAliases.candidates(item, Key.key("minecraft:chain")))
        assertEquals(listOf(Key.key("minecraft:chain")), RegistryAliases.candidates(item, Key.key("minecraft:iron_chain")))
    }

    @Test
    fun `a split key leads to every part, and each part back to the whole`() {
        assertEquals(
            listOf(Key.key("minecraft:splash_potion"), Key.key("minecraft:lingering_potion")),
            RegistryAliases.candidates(entity, Key.key("minecraft:potion")),
        )
        assertEquals(listOf(Key.key("minecraft:potion")), RegistryAliases.candidates(entity, Key.key("minecraft:lingering_potion")))
    }

    @Test
    fun `aliases belong to one registry, and a removal is known as one`() {
        assertEquals(emptyList<Key>(), RegistryAliases.candidates(entity, Key.key("minecraft:chain")))
        assertTrue(RegistryAliases.removed(sound, Key.key("minecraft:entity.wolf.howl")))
        assertFalse(RegistryAliases.removed(item, Key.key("minecraft:chain")))
    }

    @Test
    fun `the table format names the line it cannot read`() {
        val parsed = RegistryAliases.parse("# comment\nitem old -> new other # trailing\n\nsound_event gone -> -\n")
        assertEquals(
            listOf("minecraft:item minecraft:old -> minecraft:new minecraft:other", "minecraft:sound_event minecraft:gone -> -"),
            parsed.map {
                it.toString()
            },
        )
        val failure = assertThrows<IllegalArgumentException> { RegistryAliases.parse("item old new\n") }
        assertTrue("line 1" in (failure.message ?: ""), failure.message)
    }
}
