package studio.sculk.integrations

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.flatMap

/**
 * Items owned by a custom-item plugin, addressed the way a server owner already writes them:
 * `nexo:ruby_sword`, `oraxen:ruby_sword`, `itemsadder:mypack:ruby`.
 *
 * **Reflection only, and deliberately so.** Nexo and ItemsAdder ship with no licence file at all —
 * all rights reserved by default — and Oraxen carries a custom proprietary one. None of them may sit
 * on the compile classpath of a framework that plugins are sold on top of, so there is no artifact,
 * no repository and nothing bundled: the class is loaded out of the plugin's own class loader at the
 * moment it is asked for, or the lookup fails with a sentence saying which plugin is missing.
 *
 * Nothing is cached but the reflected [java.lang.reflect.Method] handles. All three APIs hand back a
 * freshly built stack, so caching one would only reintroduce the question of when to invalidate it —
 * every one of these plugins registers its items late and asynchronously, and reloads them on
 * command.
 *
 * The prefix is matched against the text before the *first* colon and nothing else. Anything with a
 * prefix that is not a provider — `minecraft:diamond`, a bare `diamond_sword` — is not this type's
 * business and resolves exactly as it did before.
 */
@SculkExperimental
public object CustomItems {
    private val providers: List<ReflectiveItemProvider> =
        listOf(
            // Nexo and Oraxen are the same API with different names: a static lookup returning a
            // nullable builder, and a no-argument build. ItemsAdder differs only in that its own ids
            // are namespaced, which the first-colon split already handles.
            ReflectiveItemProvider(
                prefix = "nexo",
                pluginName = "Nexo",
                className = "com.nexomc.nexo.api.NexoItems",
                lookup = "itemFromId",
                toStack = "build",
            ),
            ReflectiveItemProvider(
                prefix = "oraxen",
                pluginName = "Oraxen",
                className = "io.th0rgal.oraxen.api.OraxenItems",
                lookup = "getItemById",
                toStack = "build",
            ),
            ReflectiveItemProvider(
                prefix = "itemsadder",
                pluginName = "ItemsAdder",
                className = "dev.lone.itemsadder.api.CustomStack",
                lookup = "getInstance",
                toStack = "getItemStack",
            ),
        )

    private val byPrefix: Map<String, ReflectiveItemProvider> = providers.associateBy { it.prefix }

    /** The prefixes a config value may use, in the order they are documented. */
    public val prefixes: List<String> get() = providers.map { it.prefix }

    /** The plugin names behind [prefixes], for a message naming what a server would need to install. */
    public val pluginNames: List<String> get() = providers.map { it.pluginName }

    /** Whether [id] is addressed to a custom-item plugin at all. Cheap: it only reads the prefix. */
    public fun handles(id: String): Boolean = split(id) != null

    /**
     * Resolves `nexo:ruby_sword` and friends to a stack.
     *
     * Fails, with a different sentence each time, when the id names no provider, when the provider's
     * plugin is not installed, and when the plugin is installed but has no such item — three
     * conditions a server owner fixes three different ways.
     */
    public fun resolve(id: String): SculkResult<ItemStack> {
        val split = split(id)
            ?: return SculkResult.failure("'$id' does not name a custom-item plugin; expected one of ${prefixes.joinToString()}.")
        val (provider, itemId) = split
        if (itemId.isBlank()) return SculkResult.failure("'$id' names ${provider.pluginName} but no item after the ':'.")
        return provider.resolve(itemId)
    }

    /**
     * The provider addressed by [id] and the id to hand it, or null when [id] is not ours.
     *
     * Split on the *first* colon only. ItemsAdder ids are themselves `namespace:id`, so
     * `itemsadder:mypack:ruby` has to arrive at ItemsAdder as `mypack:ruby`.
     */
    internal fun split(id: String): Pair<ReflectiveItemProvider, String>? {
        val trimmed = id.trim()
        if (':' !in trimmed) return null
        val provider = byPrefix[trimmed.substringBefore(':').lowercase()] ?: return null
        return provider to trimmed.substringAfter(':').trim()
    }
}

/**
 * One custom-item plugin, reached by reflection.
 *
 * Parameterised rather than written out three times because all three APIs are the same two calls:
 * a static `lookup(String)` that returns null for an unknown id, and a no-argument [toStack] on
 * whatever it returned. Three hand-written adapters would have been three places to fix the same
 * mistake.
 *
 * The class is loaded from the plugin's own class loader rather than through `Class.forName`. Paper
 * only lets one plugin see another's classes when the dependency is declared, and a framework cannot
 * declare a `softdepend` on behalf of the plugin embedding it.
 */
internal class ReflectiveItemProvider(
    val prefix: String,
    val pluginName: String,
    private val className: String,
    private val lookup: String,
    private val toStack: String,
) {
    fun resolve(itemId: String): SculkResult<ItemStack> {
        val owner = enabledPlugin()
            ?: return SculkResult.failure("$pluginName is not installed or is not enabled, so '$prefix:$itemId' cannot be resolved.")
        return SculkResult
            .catching("ask $pluginName for the item '$itemId'") {
                val api = owner.javaClass.classLoader.loadClass(className)
                Reflect.method(api, lookup, String::class.java).invoke(null, itemId)
            }.flatMap { found ->
                if (found == null) {
                    SculkResult.failure("$pluginName has no item '$itemId'.")
                } else {
                    SculkResult.catching("build the $pluginName item '$itemId'") {
                        Reflect.method(found.javaClass, toStack).invoke(found) as ItemStack
                    }
                }
            }
    }

    /**
     * Read off [Bukkit] rather than a held [Plugin]: the item DSL is a top-level function with no
     * plugin in scope, which is the whole reason a config string can reach it. Guarded because
     * `Bukkit.getServer()` is null wherever the server is not up, which includes every unit test.
     */
    private fun enabledPlugin(): Plugin? = runCatching {
        Bukkit.getServer().pluginManager.getPlugin(pluginName)?.takeIf { it.isEnabled }
    }.getOrNull()
}
