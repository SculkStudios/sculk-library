package studio.sculk.platform

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.command.CommandHelp
import studio.sculk.command.CommandSpec
import studio.sculk.command.HelpCommand
import studio.sculk.command.CommandText
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
 *         // command { } builds the spec; the leading + is what registers it.
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

    /**
     * Whether to print the start-up banner at all.
     *
     * Turn it off in a plugin that prints its own. A framework mark in a paying customer's console
     * is the product's branding, not the framework's, and a plugin that banners for itself would
     * otherwise print two — with the server and version rows duplicated across both.
     */
    @SculkStable
    protected open val banner: Boolean get() = true

    /**
     * The art printed beside [bannerFacts]. Override it to print your own mark instead of Sculk's,
     * which is the better answer than [banner] `= false` when you still want the facts.
     */
    @SculkStable
    protected open fun bannerArt(): BannerArt = BannerArt.SCULK

    /** Extra lines beside the start-up banner: version numbers, world names, anything diagnostic. */
    @SculkStable
    protected open fun bannerFacts(): List<Pair<String, String>> = emptyList()

    /**
     * The wording of the errors the command framework itself emits.
     *
     * `BrigadierAdapter` has taken a [CommandText] since it was written and **nothing ever passed
     * one**, so every plugin got `CommandText.DEFAULT` and the customisation point was unreachable.
     * A product with its own message prefix therefore had one set of lines -- "Usage: /claim <id>",
     * "You do not have permission to do that." -- arriving unbranded next to everything else it
     * says, which reads as a different plugin answering.
     */
    @SculkStable
    protected open fun commandText(): CommandText = CommandText.DEFAULT

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

    /**
     * **Registers this command.** `command("x") { … }` only *builds* a spec; the leading `+` is
     * what hands it to the plugin.
     *
     * ```kotlin
     * override fun setup() {
     *     +command("gifts") {          // the + registers it
     *         executes { reply("<value>3</value> per player.") }
     *     }
     * }
     * ```
     *
     * Splitting the two means a spec can be built anywhere — a test, a helper, a list assembled at
     * runtime — and registered only where a plugin exists to own it. [commands] takes several at
     * once, and [declaredCommands] is the resulting list.
     */
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
            val adapter = BrigadierAdapter(sculk.messages, sculk.cooldowns, sculk.scope, logger, commandText())
            for (spec in declared) {
                event.registrar().register(adapter.build(spec), spec.description, spec.aliases)
            }
        }

        setup()

        // After setup, so it sees every command; a supplier so it also sees any registered later.
        if (builtInHelp) declared += HelpCommand({ declaredCommands }, CommandHelp()).spec()

        if (banner) {
            SculkBanner(componentLogger, bannerArt()).show(
                name = pluginMeta.name,
                version = pluginMeta.version,
                facts = standardFacts() + bannerFacts() + ("Started in" to "${System.currentTimeMillis() - startedAt}ms"),
            )
        }
    }

    final override fun onDisable() {
        runCatching { shutdown() }.onFailure { logger.warning("[Sculk] shutdown() failed: ${it.message}") }
        SculkHandle.all(kept).close()
        kept.clear()
        sculk.close()
    }

    /**
     * The facts that answer the first support question before anyone asks.
     *
     * The packet backend is named only when one actually loaded. It used to report `none`
     * otherwise, which is accurate and unhelpful: most plugins use no packet features at all, so
     * their owners read a line saying `none` on an otherwise healthy startup and open a ticket
     * asking what is broken. A plugin that does depend on a backend can say so in [bannerFacts],
     * where it knows whether the absence matters.
     */
    private fun standardFacts(): List<Pair<String, String>> = buildList {
        add("Server" to "${server.name} ${server.minecraftVersion}")
        sculk.packets.getOrNull()?.let { add("Packets" to it.backend.name) }
    }
}
