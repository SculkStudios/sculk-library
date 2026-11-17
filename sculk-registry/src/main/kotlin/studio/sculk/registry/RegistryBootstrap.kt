package studio.sculk.registry

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.registry.RegistryBuilder
import io.papermc.paper.registry.TypedKey
import io.papermc.paper.registry.event.RegistryEventProvider
import io.papermc.paper.registry.event.WritableRegistry
import net.kyori.adventure.key.Key
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import java.net.URI

/** The registry event that differs across Paper versions (ADR-0028). Not API. */
@SculkInternal
public interface ComposeAdapter {
    public fun <T : Any, B : RegistryBuilder<T>> compose(
        context: BootstrapContext,
        provider: RegistryEventProvider<T, B>,
        action: (WritableRegistry<T, B>) -> Unit,
    )
}

internal object RegistryAdapters {
    private const val MODERN_ADAPTER = "studio.sculk.registry.adapter.ComposeEventAdapter"

    // RegistryFreezeEvent (1.21.4) became RegistryComposeEvent (1.21.11); the provider's methods say
    // which this server has. The compose adapter is compiled against the newest anchor.
    val compose: ComposeAdapter by lazy {
        val modern = RegistryEventProvider::class.java.methods.any { it.name == "compose" && it.parameterCount == 0 }
        if (modern) {
            Class.forName(
                MODERN_ADAPTER,
                true,
                RegistryAdapters::class.java.classLoader,
            ).getDeclaredConstructor().newInstance() as ComposeAdapter
        } else {
            FreezeEventAdapter()
        }
    }
}

/**
 * Runs [compose] once [provider]'s registry is ready for new entries, the point Paper calls
 * freezing on 1.21.4 and composing from 1.21.11. Which registries accept entries depends on the
 * server; the `RegistryEvents` field for a registry exists only where it does.
 *
 * This and the other bootstrap functions register lifecycle handlers, so they are called from a
 * `PluginBootstrap`, before the registries freeze:
 *
 * ```kotlin
 * class ShopBootstrap : PluginBootstrap {
 *     override fun bootstrap(context: BootstrapContext) {
 *         context.composeRegistry(RegistryEvents.ENCHANTMENT) { registry ->
 *             registry.register(TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("shop:lifesteal"))) { builder -> ... }
 *         }
 *         context.discoverDatapack(javaClass.classLoader, "datapack", "shop")
 *     }
 * }
 * ```
 */
@SculkStable
public fun <T : Any, B : RegistryBuilder<T>> BootstrapContext.composeRegistry(
    provider: RegistryEventProvider<T, B>,
    compose: (WritableRegistry<T, B>) -> Unit,
) {
    RegistryAdapters.compose.compose(this, provider, compose)
}

/** Runs [modify] on the builder of each entry in [keys] as [provider]'s registry adds it, vanilla entries included. */
@SculkStable
public fun <T : Any, B : RegistryBuilder<T>> BootstrapContext.modifyEntries(
    provider: RegistryEventProvider<T, B>,
    keys: Collection<TypedKey<T>>,
    modify: (key: TypedKey<T>, builder: B) -> Unit,
) {
    require(keys.isNotEmpty()) { "name at least one entry to modify" }
    val wanted = keys.toSet()
    lifecycleManager.registerEventHandler(
        provider.entryAdd().newHandler { event -> if (event.key() in wanted) modify(event.key(), event.builder()) },
    )
}

/**
 * Adds the datapack in the directory [resource] of the plugin jar as `<plugin>/[id]`, so its data
 * (tags, loot tables, advancements, dimension types) loads with the server's own. Throws
 * [IllegalArgumentException] when [resource] has no `pack.mcmeta` in the jar.
 */
@SculkStable
public fun BootstrapContext.discoverDatapack(classLoader: ClassLoader, resource: String, id: String) {
    require(Key.parseableValue(id)) { "datapack id '$id' must be lower-case letters, digits, '_', '-', '.' or '/'" }
    val folder = resource.trim('/')
    // Jars need not contain directory entries, so the pack is found through its pack.mcmeta.
    val meta = requireNotNull(classLoader.getResource("$folder/pack.mcmeta")) { "no $folder/pack.mcmeta in the plugin jar" }
    val uri = URI(meta.toString().removeSuffix("/pack.mcmeta"))
    lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY) { event -> event.registrar().discoverPack(uri, id) }
}
