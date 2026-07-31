package studio.sculk.series

import org.bukkit.GameMode
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SculkSeriesTest {
    @Test
    fun `a material resolves regardless of case`() {
        assertEquals(Material.DIAMOND_SWORD, SculkSeries.material("DIAMOND_SWORD"))
        assertEquals(Material.DIAMOND_SWORD, SculkSeries.material("diamond_sword"))
        assertEquals(Material.DIAMOND_SWORD, SculkSeries.material("Diamond_Sword"))
    }

    @Test
    fun `a namespaced key resolves`() {
        assertEquals(Material.DIAMOND_SWORD, SculkSeries.material("minecraft:diamond_sword"))
    }

    @Test
    fun `surrounding whitespace does not stop a lookup`() {
        // Config values arrive with whatever spacing the owner left behind.
        assertEquals(Material.DIAMOND_SWORD, SculkSeries.material("  diamond_sword  "))
    }

    @Test
    fun `an unknown key is null rather than an exception`() {
        assertNull(SculkSeries.material("diamon_sword"))
        assertNull(SculkSeries.material(""))
    }

    @Test
    fun `require names both what was wanted and what was given`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            SculkSeries.requireMaterial("diamon_sword")
        }

        // The message is what a server owner sees in the log, so it must carry the typo itself.
        assertTrue(failure.message!!.contains("diamon_sword"), failure.message)
        assertTrue(failure.message!!.contains("material"), failure.message)
    }

    @Test
    fun `require returns the value when the key is good`() {
        assertEquals(Material.DIAMOND_SWORD, SculkSeries.requireMaterial("diamond_sword"))
    }

    @Test
    fun `a repeated lookup returns the cached instance`() {
        val first = SculkSeries.material("diamond_sword")
        val second = SculkSeries.material("diamond_sword")

        assertSame(first, second)
    }

    @Test
    fun `game modes and difficulties resolve by name`() {
        assertEquals(GameMode.CREATIVE, SculkSeries.gameMode("creative"))
        assertEquals(GameMode.SURVIVAL, SculkSeries.gameMode("SURVIVAL"))
        assertNull(SculkSeries.gameMode("spectatorr"))
    }

    @Test
    fun `keys are exposed for suggestion and validation`() {
        val keys = SculkSeries.materialKeys()

        assertTrue(keys.isNotEmpty())
        assertTrue(keys.any { it.equals("diamond_sword", ignoreCase = true) }, "expected diamond_sword among ${keys.size} keys")
    }

    @Test
    fun `a custom registry resolves through the same facade`() {
        SculkSeries.register(TestKind::class.java) { key -> TestKind.entries.firstOrNull { it.name.equals(key, true) } }

        assertEquals(TestKind.ALPHA, SculkSeries.lookup(TestKind::class.java, "alpha"))
        assertEquals(TestKind.BETA, SculkSeries.lookup<TestKind>("BETA"))
        assertNull(SculkSeries.lookup<TestKind>("gamma"))
    }

    @Test
    fun `an unregistered custom type resolves to null rather than throwing`() {
        assertNull(SculkSeries.lookup(Unregistered::class.java, "anything"))
    }

    private enum class TestKind { ALPHA, BETA }

    private class Unregistered
}
