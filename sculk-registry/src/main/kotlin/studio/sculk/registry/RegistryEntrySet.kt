package studio.sculk.registry

import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.InvalidKeyException
import net.kyori.adventure.key.Key
import org.bukkit.Keyed
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.map

/**
 * Entries of one registry chosen by text, as configuration writes them: `minecraft:stone` names
 * an entry, `#minecraft:logs` a tag, and a leading `!` excludes either. Exclusions win.
 *
 * ```yaml
 * breakable: ["#minecraft:logs", "minecraft:stone", "!minecraft:oak_log"]
 * ```
 *
 * [parse] checks the text without a server; [resolve] reads the server's registries and reports
 * names that do not exist there, so a typo in configuration is an error at load, not a rule that
 * never matches. Entries do not change after bootstrap, but tags do: a datapack reload
 * (`/minecraft:reload`, after which Paper fires `ServerResourcesReloadedEvent`) can change what a
 * tag holds, so a set with tag selectors must be resolved again after one; a set of entries keeps.
 */
@SculkStable
public class RegistryEntrySet<T : Keyed> private constructor(
    public val registry: RegistryKey<T>,
    public val includes: List<Selector>,
    public val excludes: List<Selector>,
) {
    /** One entry or one tag. */
    @SculkStable
    public class Selector(public val key: Key, public val isTag: Boolean) {
        override fun equals(other: Any?): Boolean = other is Selector && other.key == key && other.isTag == isTag

        override fun hashCode(): Int = 31 * key.hashCode() + isTag.hashCode()

        override fun toString(): String = if (isTag) "#${key.asString()}" else key.asString()
    }

    /**
     * Every entry the set selects on the running server, or [SculkFailure.InvalidInput] listing each
     * entry and tag the server does not have.
     */
    public fun resolve(): SculkResult<Set<T>> {
        val missing = mutableListOf<String>()
        fun expand(selectors: List<Selector>): Set<T> = selectors.flatMapTo(LinkedHashSet()) { selector ->
            val found = if (selector.isTag) {
                Registries.tag(registry, selector.key)
            } else {
                Registries.get(registry, selector.key).map {
                    setOf(it)
                }
            }
            when (found) {
                is SculkResult.Success -> found.value

                else -> {
                    missing += selector.toString()
                    emptySet()
                }
            }
        }
        val selected = expand(includes) - expand(excludes)
        if (missing.isNotEmpty()) {
            return SculkFailure.InvalidInput(registry.key().asString(), "registered entries and tags", missing.joinToString())
        }
        return SculkResult.success(selected)
    }

    override fun equals(other: Any?): Boolean =
        other is RegistryEntrySet<*> && other.registry == registry && other.includes == includes && other.excludes == excludes

    override fun hashCode(): Int = listOf(registry, includes, excludes).hashCode()

    override fun toString(): String = (includes.map { it.toString() } + excludes.map { "!$it" }).joinToString(", ", "[", "]")

    @SculkStable
    public companion object {
        /**
         * Reads [entries] for [registry]. A bare word means the `minecraft` namespace, as in
         * commands. Fails with [SculkFailure.InvalidInput] on the first entry that is not a key.
         */
        public fun <T : Keyed> parse(registry: RegistryKey<T>, entries: List<String>): SculkResult<RegistryEntrySet<T>> {
            val includes = mutableListOf<Selector>()
            val excludes = mutableListOf<Selector>()
            for (raw in entries) {
                val text = raw.trim()
                val excluded = text.startsWith('!')
                val body = text.removePrefix("!")
                val isTag = body.startsWith('#')
                val key = try {
                    Key.key(body.removePrefix("#"))
                } catch (_: InvalidKeyException) {
                    return SculkFailure.InvalidInput(
                        registry.key().asString(),
                        "an entry such as minecraft:stone or a tag such as #minecraft:logs",
                        raw,
                    )
                }
                (if (excluded) excludes else includes) += Selector(key, isTag)
            }
            return SculkResult.success(RegistryEntrySet(registry, includes.distinct(), excludes.distinct()))
        }
    }
}
