package studio.sculk.platform

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.command.CommandHelp
import studio.sculk.command.CommandSpec
import studio.sculk.command.HelpCommand
import studio.sculk.command.brigadier.BrigadierAdapter
import studio.sculk.fold
import studio.sculk.text.SculkTheme

/**
 * The base class for a Sculk plugin.
 *
 * ```kotlin
 * class MyPlugin : SculkPlugin() {
 *     override fun setup() {
 *         services.register(EconomyService(sculk.data))
 *         +command("balance") { executes { reply("<value><coins></value>") } }
 *     }
 * }
 * ```
 *
 * [onEnable] and [onDisable] are final. A subclass overriding them could tear its own state down
 * while the framework still expected it, or skip the framework's setup entirely and then wonder
 * why its menus were lootable — both were possible before, and neither produced a useful error.
 */
@SculkStable
public abstract class SculkPlugin : JavaPlugin() {
    /** The palette this plugin renders against. */
    @SculkStable
    protected open val theme: SculkTheme get() = SculkTheme.EMPTY

    /** ARGB drop shadow applied to every message, or null for none. */
    @SculkStable
    protected open val shadow: Int? get() = null

    /** Whether to register the generated `/help`. */
    @SculkStable
    protected open val builtInHelp: Boolean get() = true

    /** Extra lines beside the start-up banner: version numbers, world names, anything diagnostic. */
    @SculkStable
    protected open fun bannerFacts(): List<Pair<String, String>> = emptyList()

    @SculkStable
    public lateinit var sculk: SculkPlatform
        private set

    private val declared = mutableListOf<CommandSpec>()
    private val kept = mutableListOf<SculkHandle>()

    /** Where a plugin wires itself up. */
    @SculkStable
    protected abstract fun setup()

    /** Anything the plugin must do before the framework tears itself down. */
    @SculkStable
    protected open fun shutdown() {}

    /** Registers a command. `+command("x") { … }` reads better in a long `setup()`. */
    @SculkStable
    protected operator fun CommandSpec.unaryPlus() {
        declared += this
    }

    @SculkStable
    protected fun commands(vararg specs: CommandSpec) {
        declared += specs
    }

    /** Every command this plugin declared, so anything else can use the list Brigadier got. */
    @SculkStable
    public val declaredCommands: List<CommandSpec> get() = declared.toList()

    /** Registers a Bukkit listener, unregistered automatically on disable. */
    @SculkStable
    protected fun listen(listener: Listener) {
        kept += sculk.events.listen(listener)
    }

    /** Keeps [handle] alive until disable, then closes it. */
    @SculkStable
    protected fun keep(handle: SculkHandle) {
        kept += handle
    }

    final override fun onEnable() {
        val startedAt = System.currentTimeMillis()
        sculk = SculkPlatform(this, theme, shadow)
        sculk.start()

        // Registered *before* setup(). The COMMANDS lifecycle event fires after onEnable returns,
        // so a spec declared anywhere in setup() is picked up regardless of the order a plugin
        // happens to wire things in. Registering this after setup would silently drop everything
        // declared from a helper that ran first.
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val adapter = BrigadierAdapter(sculk.messages, sculk.cooldowns, sculk.scope, logger)
            for (spec in declared) {
                event.registrar().register(adapter.build(spec), spec.description, spec.aliases)
            }
        }

        setup()

        // After setup, so it sees every command; a supplier so it also sees any registered later.
        if (builtInHelp) declared += HelpCommand({ declaredCommands }, CommandHelp()).spec()

        SculkBanner(componentLogger).show(
            name = pluginMeta.name,
            version = pluginMeta.version,
            facts = standardFacts() + bannerFacts() + ("Started in" to "${System.currentTimeMillis() - startedAt}ms"),
        )
    }

    final override fun onDisable() {
        runCatching { shutdown() }.onFailure { logger.warning("[Sculk] shutdown() failed: ${it.message}") }
        SculkHandle.all(kept).close()
        kept.clear()
        sculk.close()
    }

    /**
     * The facts that answer the first two support questions before anyone asks: which storage
     * backend is live, and whether a packet backend loaded.
     */
    private fun standardFacts(): List<Pair<String, String>> = listOf(
        "Server" to "${server.name} ${server.minecraftVersion}",
        "Packets" to sculk.packets.fold({ it.backend.name }, { _, _ -> "none" }),
    )
}
