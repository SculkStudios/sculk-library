@file:OptIn(SculkExperimental::class)

package studio.sculk.world

import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.flatMap

/** One world's game rules under their platform names. */
internal interface RuleStore {
    /** The world's name, for failures. */
    val worldName: String

    /** The value type of the platform rule [name], or null when the server has no such rule. */
    fun typeOf(name: String): Class<*>?

    /** The rule's value, or null when it is not active in this world. */
    fun value(name: String): Any?

    /** The rule's default, or null when the server cannot say. */
    fun default(name: String): Any?

    /**
     * Sets [name] to [value]; false when the world refused it, because the rule is disabled there or a
     * listener cancelled the change. Throws [IllegalArgumentException] for a value the rule rejects.
     */
    fun write(name: String, value: Any): Boolean

    /** Whether the rule is active in this world; feature flags can switch one off. */
    fun enabled(name: String): Boolean
}

/** Reads and writes [GameRuleKey]s through a [RuleStore], translating names and values with [naming]. */
internal class GameRuleEngine(val naming: RuleNaming) {
    fun <T : Any> read(store: RuleStore, rule: GameRuleKey<T>): SculkResult<T> = locate(store, rule).flatMap { name ->
        // Both 1.21.4 and 26.3 throw on reading a rule whose feature flag is off.
        if (!store.enabled(name.name)) return@flatMap inactive(rule, store)
        val raw = store.value(name.name) ?: return@flatMap inactive(rule, store)
        typed(rule, name.conversion.fromPlatform(raw))
    }

    fun <T : Any> readDefault(store: RuleStore, rule: GameRuleKey<T>): SculkResult<T> = locate(store, rule).flatMap { name ->
        val raw = store.default(name.name) ?: return@flatMap inactive(rule, store)
        typed(rule, name.conversion.fromPlatform(raw))
    }

    fun <T : Any> write(store: RuleStore, rule: GameRuleKey<T>, value: T): SculkResult<T> = locate(store, rule).flatMap { name ->
        val platformValue = name.conversion.toPlatform(value) ?: return@flatMap SculkFailure.UnsupportedCapability(
            "Game rule $rule = $value",
            "Minecraft 1.21.4 has only doFireTick, so fire spreads everywhere or nowhere",
            hint = "Use 0 to stop fire spreading or -1 to let it spread everywhere.",
        )
        val written = try {
            store.write(name.name, platformValue)
        } catch (rejected: IllegalArgumentException) {
            return@flatMap SculkFailure.InvalidInput(
                "game rule $rule",
                "a value the server accepts",
                value.toString(),
                hint = rejected.message,
            )
        }
        when {
            written -> read(store, rule)
            !store.enabled(name.name) -> inactive(rule, store)
            else -> ChangeCancelled("the change of $rule in ${store.worldName}")
        }
    }

    /** The value of [rule] that a platform event reported as [text], in the platform's form. */
    fun <T : Any> fromPlatformText(rule: GameRuleKey<T>, text: String): SculkResult<T> {
        val name = naming.platformName(rule) ?: return absent(rule)
        val raw: Any = when (name.conversion.platformType(rule.type)) {
            Boolean::class.javaObjectType -> text.trim().toBooleanStrictOrNull()
            else -> text.trim().toIntOrNull()
        } ?: return SculkFailure.InvalidInput("game rule $rule", "${rule.type} values", "'$text'")
        return typed(rule, name.conversion.fromPlatform(raw))
    }

    private fun locate(store: RuleStore, rule: GameRuleKey<*>): SculkResult<PlatformRuleName> {
        val name = naming.platformName(rule) ?: return absent(rule)
        val actual = store.typeOf(name.name) ?: return if (rule in GameRuleKey.vanilla) {
            SculkFailure.UnsupportedCapability("Game rule $rule", "this server has no such rule")
        } else {
            SculkFailure.InvalidInput("game rule", "a game rule this server has", rule.toString())
        }
        val expected = name.conversion.platformType(rule.type)
        if (actual != expected) {
            return SculkFailure.InvalidInput("game rule $rule", "${rule.type} values", "a rule of ${typeName(actual)} values")
        }
        return SculkResult.success(name)
    }

    private fun <T : Any> typed(rule: GameRuleKey<T>, value: Any): SculkResult<T> {
        if (!rule.type.javaType.isInstance(value)) {
            return SculkFailure.InvalidInput("game rule $rule", "${rule.type} values", "a ${typeName(value.javaClass)} value")
        }
        return SculkResult.success(rule.type.javaType.cast(value))
    }

    private fun absent(rule: GameRuleKey<*>): SculkFailure =
        SculkFailure.UnsupportedCapability("Game rule $rule", "Minecraft 1.21.4 has no such rule", hint = "It exists on 1.21.11 and later.")

    private fun inactive(rule: GameRuleKey<*>, store: RuleStore): SculkFailure =
        SculkFailure.UnsupportedCapability("Game rule $rule", "it is not active in ${store.worldName}; its feature flag is off")

    private fun typeName(type: Class<*>): String = when (type) {
        Boolean::class.javaObjectType -> "boolean"
        Int::class.javaObjectType -> "integer"
        else -> type.simpleName
    }
}
