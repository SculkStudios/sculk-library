package studio.sculk.registry

import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow

/** Parsing needs no server; resolving runs in `integration-tests/registry`. */
class RegistryEntrySetTest {
    @Test
    fun `entries, tags and exclusions parse as configuration writes them`() {
        val set = RegistryEntrySet.parse(
            RegistryKey.BLOCK,
            listOf("#minecraft:logs", "stone", "!minecraft:oak_log", " #minecraft:leaves "),
        ).getOrThrow()
        assertEquals(
            listOf(
                RegistryEntrySet.Selector(Key.key("minecraft:logs"), true),
                RegistryEntrySet.Selector(Key.key("minecraft:stone"), false),
                RegistryEntrySet.Selector(Key.key("minecraft:leaves"), true),
            ),
            set.includes,
        )
        assertEquals(listOf(RegistryEntrySet.Selector(Key.key("minecraft:oak_log"), false)), set.excludes)
        assertEquals("[#minecraft:logs, minecraft:stone, #minecraft:leaves, !minecraft:oak_log]", set.toString())
    }

    @Test
    fun `text that is not a key fails with what was wrong`() {
        val result = RegistryEntrySet.parse(RegistryKey.BLOCK, listOf("minecraft:stone", "Not A Block!"))
        assertTrue(result is SculkFailure.InvalidInput && "Not A Block!" in result.describe(), "got $result")
    }

    @Test
    fun `duplicates collapse, so a set written twice is equal`() {
        val once = RegistryEntrySet.parse(RegistryKey.BLOCK, listOf("stone")).getOrThrow()
        val twice = RegistryEntrySet.parse(RegistryKey.BLOCK, listOf("stone", "minecraft:stone")).getOrThrow()
        assertEquals(once, twice)
    }
}
