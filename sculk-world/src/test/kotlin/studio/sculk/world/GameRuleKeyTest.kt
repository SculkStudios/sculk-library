package studio.sculk.world

import net.kyori.adventure.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
import studio.sculk.getOrNull
import studio.sculk.getOrThrow

@OptIn(SculkExperimental::class)
class GameRuleKeyTest {
    @Test
    fun `the vanilla list holds every rule of 26-3 once, in key order`() {
        val keys = GameRuleKey.vanilla.map { it.key.asString() }
        assertEquals(59, keys.size)
        assertEquals(keys.distinct(), keys)
        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun `a rule is found by its key, with or without the namespace, and by its 1-21-4 name`() {
        assertEquals(GameRuleKey.ADVANCE_TIME, GameRuleKey.parse("minecraft:advance_time").getOrThrow())
        assertEquals(GameRuleKey.ADVANCE_TIME, GameRuleKey.parse("advance_time").getOrThrow())
        assertEquals(GameRuleKey.ADVANCE_TIME, GameRuleKey.parse(" doDaylightCycle ").getOrThrow())
        assertEquals(GameRuleKey.RAIDS, GameRuleKey.parse("disableRaids").getOrThrow())
    }

    @Test
    fun `a 1-21-4 rule with no successor fails with a hint saying so`() {
        val failure = GameRuleKey.parse("spawnChunkRadius")
        assertTrue(failure is SculkFailure.InvalidInput && failure.hint?.contains("no successor") == true, failure.toString())
    }

    @Test
    fun `a name that is no rule fails as invalid input, malformed keys included`() {
        assertTrue(GameRuleKey.parse("keep_inventroy") is SculkFailure.InvalidInput)
        assertTrue(GameRuleKey.parse("Not A Key!") is SculkFailure.InvalidInput)
        assertTrue(GameRuleKey.parse("") is SculkFailure.InvalidInput)
    }

    @Test
    fun `keys compare by key and type, so the same key with another type is another rule`() {
        assertEquals(GameRuleKey.KEEP_INVENTORY, GameRuleKey.boolean(Key.key("minecraft:keep_inventory")))
        assertNotEquals(GameRuleKey.KEEP_INVENTORY, GameRuleKey.integer(Key.key("minecraft:keep_inventory")))
    }

    @Test
    fun `values parse as gamerule reads them and nothing looser`() {
        assertEquals(true, GameRuleType.BOOLEAN.parse("true").getOrNull())
        assertEquals(false, GameRuleType.BOOLEAN.parse(" false ").getOrNull())
        assertTrue(GameRuleType.BOOLEAN.parse("yes") is SculkFailure.InvalidInput)
        assertEquals(-1, GameRuleType.INTEGER.parse("-1").getOrNull())
        assertTrue(GameRuleType.INTEGER.parse("1.5") is SculkFailure.InvalidInput)
    }
}
