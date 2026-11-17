package studio.sculk.world

import net.kyori.adventure.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
import studio.sculk.getOrNull
import studio.sculk.getOrThrow

/**
 * A world's rules under platform names, as a server keeps them. It refuses what it cannot model: a
 * write of the wrong type throws, as Paper's own cast would.
 */
private class FakeRuleStore(vararg rules: Pair<String, Any>) : RuleStore {
    val values = linkedMapOf(*rules)
    private val defaults = linkedMapOf(*rules)
    var cancelAll = false
    var disabled = setOf<String>()
    var maximum = Int.MAX_VALUE

    override val worldName: String = "minecraft:overworld"

    override fun typeOf(name: String): Class<*>? = defaults[name]?.javaClass

    override fun value(name: String): Any? {
        check(name !in disabled) { "Paper throws on reading $name while its feature flag is off" }
        return values[name]
    }

    override fun default(name: String): Any? = defaults[name]

    override fun write(name: String, value: Any): Boolean {
        val type = checkNotNull(typeOf(name)) { "no rule $name" }
        check(type.isInstance(value)) { "$name holds ${type.simpleName}, not ${value.javaClass.simpleName}" }
        require(value !is Int || value <= maximum) { "Value $value is above $maximum" }
        if (cancelAll || name in disabled) return false
        values[name] = value
        return true
    }

    override fun enabled(name: String): Boolean = name !in disabled
}

@OptIn(SculkExperimental::class)
class GameRuleEngineTest {
    private val modern = GameRuleEngine(RuleNaming(registryBacked = true))
    private val legacy = GameRuleEngine(RuleNaming(registryBacked = false))

    @Test
    fun `from 1-21-11 a rule is read and written under its registry key`() {
        val store = FakeRuleStore("minecraft:keep_inventory" to false, "minecraft:random_tick_speed" to 3)
        assertEquals(false, modern.read(store, GameRuleKey.KEEP_INVENTORY).getOrThrow())
        assertEquals(true, modern.write(store, GameRuleKey.KEEP_INVENTORY, true).getOrThrow())
        assertEquals(true, store.values["minecraft:keep_inventory"])
        assertEquals(10, modern.write(store, GameRuleKey.RANDOM_TICK_SPEED, 10).getOrThrow())
    }

    @Test
    fun `on 1-21-4 the same key reaches the rule under its old name`() {
        val store = FakeRuleStore("doDaylightCycle" to true)
        assertEquals(true, legacy.read(store, GameRuleKey.ADVANCE_TIME).getOrThrow())
        legacy.write(store, GameRuleKey.ADVANCE_TIME, false).getOrThrow()
        assertEquals(false, store.values["doDaylightCycle"])
    }

    @Test
    fun `an inverted rule keeps its meaning on 1-21-4`() {
        val store = FakeRuleStore("disableRaids" to true)
        assertEquals(false, legacy.read(store, GameRuleKey.RAIDS).getOrThrow())
        assertEquals(true, legacy.write(store, GameRuleKey.RAIDS, true).getOrThrow())
        assertEquals(false, store.values["disableRaids"])
        assertEquals(true, legacy.readDefault(FakeRuleStore("disableRaids" to false), GameRuleKey.RAIDS).getOrThrow())
    }

    @Test
    fun `on 1-21-4 the fire radius is everywhere or nowhere, and any other radius is refused untouched`() {
        val store = FakeRuleStore("doFireTick" to true)
        assertEquals(-1, legacy.read(store, GameRuleKey.FIRE_SPREAD_RADIUS_AROUND_PLAYER).getOrThrow())
        assertEquals(0, legacy.write(store, GameRuleKey.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0).getOrThrow())
        assertEquals(false, store.values["doFireTick"])
        val refused = legacy.write(store, GameRuleKey.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 128)
        assertTrue(refused is SculkFailure.UnsupportedCapability, refused.toString())
        assertEquals(false, store.values["doFireTick"])
    }

    @Test
    fun `a rule added after 1-21-4 is an unsupported capability there, not an error`() {
        val result = legacy.read(FakeRuleStore("doDaylightCycle" to true), GameRuleKey.PVP)
        assertTrue(result is SculkFailure.UnsupportedCapability, result.toString())
    }

    @Test
    fun `a vanilla rule the server lacks is unsupported, an unknown key is invalid input`() {
        val store = FakeRuleStore("minecraft:keep_inventory" to false)
        assertTrue(modern.read(store, GameRuleKey.PVP) is SculkFailure.UnsupportedCapability)
        assertTrue(modern.read(store, GameRuleKey.boolean(Key.key("sculk:nope"))) is SculkFailure.InvalidInput)
        assertTrue(legacy.read(store, GameRuleKey.boolean(Key.key("sculk:nope"))) is SculkFailure.InvalidInput)
    }

    @Test
    fun `a key of the wrong type fails before anything is written`() {
        val store = FakeRuleStore("minecraft:keep_inventory" to false)
        val result = modern.write(store, GameRuleKey.integer(Key.key("minecraft:keep_inventory")), 1)
        assertTrue(result is SculkFailure.InvalidInput && "boolean" in result.message, result.toString())
        assertEquals(false, store.values["minecraft:keep_inventory"])
    }

    @Test
    fun `a value the server rejects is invalid input carrying the server's reason`() {
        val store = FakeRuleStore("minecraft:random_tick_speed" to 3).apply { maximum = 4096 }
        val result = modern.write(store, GameRuleKey.RANDOM_TICK_SPEED, 5000)
        assertTrue(result is SculkFailure.InvalidInput && result.hint?.contains("4096") == true, result.toString())
    }

    @Test
    fun `a cancelled change and a disabled rule fail differently`() {
        val cancelled = FakeRuleStore("minecraft:keep_inventory" to false).apply { cancelAll = true }
        assertTrue(modern.write(cancelled, GameRuleKey.KEEP_INVENTORY, true) is ChangeCancelled)
        val disabled = FakeRuleStore("minecraft:max_minecart_speed" to 8).apply { disabled = setOf("minecraft:max_minecart_speed") }
        assertTrue(modern.write(disabled, GameRuleKey.MAX_MINECART_SPEED, 20) is SculkFailure.UnsupportedCapability)
    }

    @Test
    fun `a rule switched off by its feature flag reads as unsupported, while its default is still known`() {
        val store = FakeRuleStore("minecartMaxSpeed" to 8).apply { disabled = setOf("minecartMaxSpeed") }
        assertTrue(legacy.read(store, GameRuleKey.MAX_MINECART_SPEED) is SculkFailure.UnsupportedCapability)
        assertEquals(8, legacy.readDefault(store, GameRuleKey.MAX_MINECART_SPEED).getOrThrow())
    }

    @Test
    fun `values reported by platform events read in the registry form`() {
        assertEquals(false, legacy.fromPlatformText(GameRuleKey.RAIDS, "true").getOrNull())
        assertEquals(-1, legacy.fromPlatformText(GameRuleKey.FIRE_SPREAD_RADIUS_AROUND_PLAYER, "true").getOrNull())
        assertEquals(128, modern.fromPlatformText(GameRuleKey.FIRE_SPREAD_RADIUS_AROUND_PLAYER, "128").getOrNull())
        assertTrue(modern.fromPlatformText(GameRuleKey.KEEP_INVENTORY, "maybe") is SculkFailure.InvalidInput)
    }

    @Test
    fun `platform names lead back to keys, rules newer than the list included`() {
        assertEquals(GameRuleKey.RAIDS, legacy.naming.ruleFor("disableRaids", Boolean::class.javaObjectType))
        assertNull(legacy.naming.ruleFor("spawnChunkRadius", Int::class.javaObjectType))
        assertEquals(GameRuleKey.ADVANCE_TIME, modern.naming.ruleFor("minecraft:advance_time", Boolean::class.javaObjectType))
        assertEquals(GameRuleKey.integer(Key.key("minecraft:newer")), modern.naming.ruleFor("minecraft:newer", Int::class.javaObjectType))
    }
}
