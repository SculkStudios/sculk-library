package studio.sculk.world

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.annotation.SculkExperimental

/** The name table as lookups read it, without a server. */
@OptIn(SculkExperimental::class)
class GameRuleNamesTest {
    @Test
    fun `every vanilla rule has exactly one line and every line names a vanilla rule`() {
        val keyed = GameRuleNames.rows.mapNotNull { it.key }
        assertEquals(keyed.distinct(), keyed, "a key appears twice")
        assertEquals(GameRuleKey.vanilla.map { it.key }.toSet(), keyed.toSet())
    }

    @Test
    fun `no two lines claim the same 1-21-4 name`() {
        val names = GameRuleNames.rows.mapNotNull { it.legacyName }
        assertEquals(names.distinct(), names)
        assertEquals(53, names.size, "Paper 1.21.4 has 53 game rules")
    }

    @Test
    fun `only boolean rules are inverted and only the fire radius is bridged from a boolean`() {
        GameRuleNames.rows.filter { it.conversion == Conversion.INVERTED }.forEach { row ->
            assertEquals(GameRuleType.BOOLEAN, row.key?.let { GameRuleKey.byKey(it) }?.type, row.toString())
        }
        val bridged = GameRuleNames.rows.filter { it.conversion == Conversion.FIRE_RADIUS }.map { it.key }
        assertEquals(listOf(GameRuleKey.FIRE_SPREAD_RADIUS_AROUND_PLAYER.key), bridged)
    }

    @Test
    fun `the table format names the line it cannot read`() {
        val rows = GameRuleNames.parse(
            "# comment\nadvance_time doDaylightCycle # trailing\n\npvp -\n- spawnChunkRadius\nraids disableRaids inverted\n",
        )
        assertEquals(
            listOf(
                "minecraft:advance_time doDaylightCycle none",
                "minecraft:pvp - none",
                "- spawnChunkRadius none",
                "minecraft:raids disableRaids inverted",
            ),
            rows.map { it.toString() },
        )
        val shape = assertThrows<IllegalArgumentException> { GameRuleNames.parse("\nadvance_time\n") }
        assertTrue("line 2" in (shape.message ?: ""), shape.message)
        val conversion = assertThrows<IllegalArgumentException> { GameRuleNames.parse("raids disableRaids upside-down\n") }
        assertTrue("upside-down" in (conversion.message ?: ""), conversion.message)
        assertThrows<IllegalArgumentException> { GameRuleNames.parse("- -\n") }
    }
}
