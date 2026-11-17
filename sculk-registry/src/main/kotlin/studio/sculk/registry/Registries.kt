package studio.sculk.registry

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.tag.TagKey
import net.kyori.adventure.key.Key
import org.bukkit.Keyed
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * Reads the server's registries by key, for keys that come from configuration or commands and so
 * may not exist. Any thread once the server has started; registries do not change after bootstrap.
 */
@SculkStable
public object Registries {
    /**
     * The entry [key] of [registry], following [aliases] when the key was renamed on another
     * supported version: `minecraft:chain` finds `minecraft:iron_chain` on 26.3 and the reverse on
     * 1.21.4. Otherwise [SculkFailure.InvalidInput] naming what was asked for, with a hint when the
     * key was split or removed.
     */
    public fun <T : Keyed> get(registry: RegistryKey<T>, key: Key): SculkResult<T> {
        val entries = RegistryAccess.registryAccess().getRegistry(registry)
        entries.get(key)?.let { return SculkResult.success(it) }
        val name = registry.key()
        val candidates = RegistryAliases.candidates(name, key)
        val found = candidates.filter { entries.get(it) != null }
        found.singleOrNull()?.let { only -> return SculkResult.success(checkNotNull(entries.get(only))) }
        return when {
            found.size > 1 -> SculkFailure.InvalidInput(
                name.asString(),
                "one of ${found.joinToString { it.asString() }}",
                key.asString(),
                hint = "${key.asString()} was split; name the entry you mean.",
            )

            RegistryAliases.removed(name, key) ->
                SculkFailure.InvalidInput(
                    name.asString(),
                    "a registered entry",
                    key.asString(),
                    hint = "${key.asString()} was removed from Minecraft.",
                )

            else -> SculkFailure.InvalidInput(name.asString(), "a registered entry", key.asString())
        }
    }

    /** Every entry tagged [tag] in [registry], or [SculkFailure.InvalidInput] when no such tag exists. */
    public fun <T : Keyed> tag(registry: RegistryKey<T>, tag: Key): SculkResult<Set<T>> {
        val entries = RegistryAccess.registryAccess().getRegistry(registry)
        val tagKey = TagKey.create(registry, tag)
        if (!entries.hasTag(tagKey)) return SculkFailure.InvalidInput(registry.key().asString(), "a registered tag", "#${tag.asString()}")
        return SculkResult.success(entries.getTag(tagKey).values().mapNotNullTo(LinkedHashSet()) { entries.get(it.key()) })
    }

    /** Every rename and removal within the supported Minecraft range that lookups follow. */
    public val aliases: List<RegistryAlias> get() = RegistryAliases.all
}
