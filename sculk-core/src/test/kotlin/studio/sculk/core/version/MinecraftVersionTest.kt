package studio.sculk.core.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MinecraftVersionTest {
    @Test
    fun `parses legacy minecraft versions`() {
        listOf(
            "1.8.8" to MinecraftVersion(1, 8, 8),
            "1.12.2" to MinecraftVersion(1, 12, 2),
            "1.16.5" to MinecraftVersion(1, 16, 5),
            "1.20.6" to MinecraftVersion(1, 20, 6),
            "1.21.11" to MinecraftVersion(1, 21, 11),
        ).forEach { (input, expected) ->
            assertEquals(expected, MinecraftVersion.parse(input))
        }
    }

    @Test
    fun `parses calendar style minecraft versions`() {
        listOf(
            "26.1" to MinecraftVersion(26, 1),
            "26.1.1" to MinecraftVersion(26, 1, 1),
            "26.1.2" to MinecraftVersion(26, 1, 2),
            "26.1.2.build.64-stable" to MinecraftVersion(26, 1, 2, "build.64-stable"),
        ).forEach { (input, expected) ->
            assertEquals(expected, MinecraftVersion.parse(input))
        }
    }

    @Test
    fun `compares numeric version parts without one dot x assumptions`() {
        assertTrue(MinecraftVersion.parse("26.1.2") > MinecraftVersion.parse("1.21.11"))
        assertTrue(MinecraftVersion.parse("26.1.2") > MinecraftVersion.parse("26.1.1"))
    }
}
