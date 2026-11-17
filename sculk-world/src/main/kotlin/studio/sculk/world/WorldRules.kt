package studio.sculk.world

import io.papermc.paper.event.world.WorldGameRuleChangeEvent
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.command.CommandSender
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.event.EventPriority
import studio.sculk.event.SculkEvents
import studio.sculk.event.listen
import studio.sculk.execution.withGlobal
import studio.sculk.flatMap
import studio.sculk.getOrNull

/**
 * Reads and sets game rules by [GameRuleKey] on every supported Paper version, Folia and Canvas.
 * From 1.21.11 a rule is found by its registry key; on 1.21.4 by its old name, with inverted rules
 * and the fire radius translated, and a rule 1.21.4 lacks fails with
 * [studio.sculk.SculkFailure.UnsupportedCapability].
 *
 * Game rules are global state: every write runs on the global region (the main thread on Paper, the
 * global region thread on Folia and Canvas). Reads run on any thread, as Minecraft's own region
 * threads do, and see the value as of the last global tick that changed it.
 */
@SculkExperimental
public object WorldRules {
    private val engine: GameRuleEngine by lazy {
        // org.bukkit.GameRules arrived with the game rule registry in 1.21.11.
        GameRuleEngine(RuleNaming(registryBacked = classExists("org.bukkit.GameRules")))
    }

    /** True where game rules are a registry and named by key (1.21.11 and later); false on 1.21.4. */
    public val registryBacked: Boolean get() = engine.naming.registryBacked

    /**
     * Every rule this server registers, vanilla and newer, by its registry key. A rule its feature flag
     * switched off is listed, and reading it fails with `UnsupportedCapability`. Any thread.
     */
    public fun available(): List<GameRuleKey<*>> = GameRule.values().mapNotNull { engine.naming.ruleFor(it.name, it.type) }

    /**
     * The rule [text] names: anything [GameRuleKey.parse] accepts, or a rule this server has that is
     * newer than Sculk's list. Otherwise [studio.sculk.SculkFailure.InvalidInput]. Any thread.
     */
    public fun resolve(text: String): SculkResult<GameRuleKey<*>> {
        val parsed = GameRuleKey.parse(text)
        if (parsed is SculkResult.Success || text.isBlank()) return parsed
        val platform = platformRule(text.trim()) ?: return parsed
        return engine.naming.ruleFor(platform.name, platform.type)?.let { SculkResult.success(it) } ?: parsed
    }

    // From 1.21.11 the name is parsed as a namespaced key, which may refuse it rather than return null.
    private fun platformRule(name: String): GameRule<*>? = try {
        GameRule.getByName(name)
    } catch (invalid: IllegalArgumentException) {
        null
    }

    /**
     * [rule]'s value in [world]. Any thread. Fails with `UnsupportedCapability` where this server has
     * no such rule and `InvalidInput` for a key it does not know or a type that does not match.
     */
    public fun <T : Any> get(world: World, rule: GameRuleKey<T>): SculkResult<T> = engine.read(BukkitRuleStore(world), rule)

    /** [rule]'s default value, what a new world starts with. Any thread; fails as [get] does. */
    public fun <T : Any> default(world: World, rule: GameRuleKey<T>): SculkResult<T> = engine.readDefault(BukkitRuleStore(world), rule)

    /**
     * Sets [rule] to [value] in [world] from any thread of a Sculk scope, on the global region, and
     * returns the value in force afterwards, which a listener may have changed. Runs inline when the
     * caller is already on the global region. Fails as [setNow] does.
     */
    public suspend fun <T : Any> set(world: World, rule: GameRuleKey<T>, value: T): SculkResult<T> =
        withGlobal { setNow(world, rule, value) }

    /**
     * Parses [text] with [rule]'s type and sets it as [set] does: for rules and values that come from
     * configuration or a command. Returns the value in force afterwards.
     */
    public suspend fun setText(world: World, rule: GameRuleKey<*>, text: String): SculkResult<Any> =
        withGlobal { parseAndSet(world, rule, text) }

    /**
     * Sets [rule] to [value] in [world] and returns the value in force afterwards. The caller must be
     * on the global region; anywhere else this throws [IllegalStateException]. Fails with
     * `InvalidInput` for a value the server rejects (out of range), `UnsupportedCapability` where
     * the rule or the value cannot exist (a fire radius other than 0 or -1 on 1.21.4), and
     * [ChangeCancelled] when a `WorldGameRuleChangeEvent` listener cancelled the change.
     */
    public fun <T : Any> setNow(world: World, rule: GameRuleKey<T>, value: T): SculkResult<T> {
        requireGlobal("Setting a game rule")
        return engine.write(BukkitRuleStore(world), rule, value)
    }

    /**
     * Calls [handler] for every game rule change that took effect, from `/gamerule` or a plugin, for
     * as long as [events]' scope is open. It runs on the thread that made the change, after every
     * listener has decided and just before the world stores the value, so read the value from the
     * change rather than from the world. Changes to rules Sculk cannot read are skipped.
     */
    public fun onChange(events: SculkEvents, handler: (GameRuleChange<*>) -> Unit): SculkHandle =
        events.listen<WorldGameRuleChangeEvent>(EventPriority.MONITOR) { event ->
            ruleOf(event)?.let { rule -> changeOf(event, rule)?.let(handler) }
        }

    /** As [onChange], for [rule] only and with its value typed. */
    public fun <T : Any> onChange(events: SculkEvents, rule: GameRuleKey<T>, handler: (GameRuleChange<T>) -> Unit): SculkHandle =
        events.listen<WorldGameRuleChangeEvent>(EventPriority.MONITOR) { event ->
            if (ruleOf(event) == rule) changeOf(event, rule)?.let(handler)
        }

    private fun ruleOf(event: WorldGameRuleChangeEvent): GameRuleKey<*>? = engine.naming.ruleFor(event.gameRule.name, event.gameRule.type)

    private fun <T : Any> changeOf(event: WorldGameRuleChangeEvent, rule: GameRuleKey<T>): GameRuleChange<T>? =
        engine.fromPlatformText(rule, event.value).getOrNull()?.let { GameRuleChange(event.world, rule, it, event.commandSender) }

    private fun <T : Any> parseAndSet(world: World, rule: GameRuleKey<T>, text: String): SculkResult<Any> =
        rule.type.parse(text).flatMap { setNow(world, rule, it) }
}

/** A game rule change that took effect: [rule] became [value] in [world], by [sender] or, when null, by a plugin. */
@SculkExperimental
public class GameRuleChange<T : Any> internal constructor(
    public val world: World,
    public val rule: GameRuleKey<T>,
    public val value: T,
    public val sender: CommandSender?,
) {
    override fun toString(): String = "GameRuleChange(${world.key.asString()} $rule = $value)"
}

/** [world]'s rules under their platform names, through the API every supported version shares. */
private class BukkitRuleStore(private val world: World) : RuleStore {
    override val worldName: String get() = world.key.asString()

    override fun typeOf(name: String): Class<*>? = rule(name)?.type

    override fun value(name: String): Any? = refusedAsNull { rule(name)?.let { world.getGameRuleValue(it) } }

    override fun default(name: String): Any? = refusedAsNull { rule(name)?.let { world.getGameRuleDefault(it) } }

    override fun write(name: String, value: Any): Boolean = rule(name)?.let { write(it, value) } ?: false

    override fun enabled(name: String): Boolean = world.isGameRule(name)

    private fun <T> write(rule: GameRule<T>, value: Any): Boolean = world.setGameRule(rule, requireNotNull(rule.type.cast(value)))

    private fun rule(name: String): GameRule<*>? = GameRule.getByName(name)

    // Paper refuses a rule its feature flag switched off with IllegalArgumentException, on every version.
    private inline fun refusedAsNull(read: () -> Any?): Any? = try {
        read()
    } catch (refused: IllegalArgumentException) {
        null
    }
}
