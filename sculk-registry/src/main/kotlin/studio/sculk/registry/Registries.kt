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
    /** The entry [key] of [registry], or [SculkFailure.InvalidInput] naming what was asked for. */
    public fun <T : Keyed> get(registry: RegistryKey<T>, key: Key): SculkResult<T> =
        RegistryAccess.registryAccess().getRegistry(registry).get(key)?.let { SculkResult.success(it) }
            ?: SculkFailure.InvalidInput(registry.key().asString(), "a registered entry", key.asString())

    /** Every entry tagged [tag] in [registry], or [SculkFailure.InvalidInput] when no such tag exists. */
    public fun <T : Keyed> tag(registry: RegistryKey<T>, tag: Key): SculkResult<Set<T>> {
        val entries = RegistryAccess.registryAccess().getRegistry(registry)
        val tagKey = TagKey.create(registry, tag)
        if (!entries.hasTag(tagKey)) return SculkFailure.InvalidInput(registry.key().asString(), "a registered tag", "#${tag.asString()}")
        return SculkResult.success(entries.getTag(tagKey).values().mapNotNullTo(LinkedHashSet()) { entries.get(it.key()) })
    }
}
