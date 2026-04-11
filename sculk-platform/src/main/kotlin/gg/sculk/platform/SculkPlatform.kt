package gg.sculk.platform

import gg.sculk.config.SculkConfig
import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.gui.GuiContext
import gg.sculk.core.gui.GuiRegistry
import gg.sculk.core.scheduler.SculkScheduler
import gg.sculk.data.SculkData
import gg.sculk.platform.command.SculkCommandBridge
import gg.sculk.platform.event.SculkEventBus
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * The Sculk Studio platform — the single entry point that wires all modules together.
 *
 * Create one instance in `onEnable` and close it in `onDisable`.
 *
 * ```kotlin
 * class MyPlugin : JavaPlugin() {
 *     lateinit var sculk: SculkPlatform
 *
 *     override fun onEnable() {
 *         sculk = SculkPlatform.create(this) {
 *             gui()
 *             config()
 *             data()
 *         }
 *         sculk.commands.register(command("hello") {
 *             player { reply("<green>Hello!") }
 *         })
 *     }
 *
 *     override fun onDisable() = sculk.close()
 * }
 * ```
 */
@SculkStable
public class SculkPlatform internal constructor(
    private val plugin: JavaPlugin,
    /** The scheduler for running sync and async tasks. */
    public val scheduler: SculkScheduler,
    /** The event bus for registering auto-cleaned listeners. */
    public val events: SculkEventBus,
    /** Command registration bridge. Use [commands.register] to add commands. */
    public val commands: SculkCommandBridge,
    /** Config system. Non-null if [SculkPlatformBuilder.config] was called. */
    public val config: SculkConfig?,
    /** Data layer. Non-null if [SculkPlatformBuilder.data] was called. */
    public val data: SculkData?,
    private val handles: List<SculkHandle>,
) : SculkHandle {
    /**
     * Tears down all Sculk components in reverse-registration order.
     *
     * Unregisters all event listeners, commands, closes GUI sessions, and shuts down the data pool.
     * Safe to call multiple times.
     */
    override fun close() {
        handles.asReversed().forEach { it.close() }
        GuiRegistry.closeAll()
        commands.close()
        events.close()
        data?.close()
    }

    public companion object {
        /**
         * Creates a [SculkPlatform] for [plugin] with the given configuration.
         *
         * ```kotlin
         * sculk = SculkPlatform.create(this) {
         *     gui()
         *     config()
         *     data()
         * }
         * ```
         */
        @SculkStable
        @JvmStatic
        public fun create(
            plugin: JavaPlugin,
            block: SculkPlatformBuilder.() -> Unit = {},
        ): SculkPlatform = SculkPlatformBuilder(plugin).apply(block).build()
    }
}

/**
 * DSL builder for [SculkPlatform].
 */
@SculkStable
public class SculkPlatformBuilder(
    private val plugin: JavaPlugin,
) {
    private var configEnabled = false
    private var dataEnabled = false
    private var guiEnabled = false

    /** Enables the config system (auto-loads configs from [JavaPlugin.getDataFolder]). */
    @SculkStable
    public fun config() {
        configEnabled = true
    }

    /** Enables the data layer (SQLite by default, config via storage.yml). */
    @SculkStable
    public fun data() {
        dataEnabled = true
    }

    /** Registers GUI event listeners (inventory click, close, player quit routing). */
    @SculkStable
    public fun gui() {
        guiEnabled = true
    }

    internal fun build(): SculkPlatform {
        val scheduler = PaperScheduler(plugin)
        val events = SculkEventBus(plugin)
        val commands = SculkCommandBridge(plugin)
        val extraHandles = mutableListOf<SculkHandle>()

        // Config
        val sculkConfig =
            if (configEnabled) {
                SculkConfig.create(plugin.dataFolder, plugin.logger)
            } else {
                null
            }

        // Data
        val sculkData =
            if (dataEnabled) {
                SculkData.create(plugin.dataFolder, plugin.logger)
            } else {
                null
            }

        // GUI event routing
        if (guiEnabled) {
            extraHandles +=
                events.listen<InventoryClickEvent> { event ->
                    val inv = event.clickedInventory ?: return@listen
                    val session = GuiRegistry.sessionForInventory(event.view.topInventory) ?: return@listen
                    val player = event.whoClicked as? Player ?: return@listen
                    event.isCancelled = true
                    val item = session.gui.items[event.rawSlot] ?: return@listen
                    val handler = item.clickHandler ?: return@listen

                    @OptIn(gg.sculk.core.annotation.SculkInternal::class)
                    val ctx = GuiContext(player, event.click, event, session)
                    handler(ctx)
                    // Handle pending GUI switch (opened via ctx.open())
                    val pending = session.pendingSwitch()
                    if (pending != null) pending.openFor(player)
                }

            extraHandles +=
                events.listen<InventoryCloseEvent> { event ->
                    val player = event.player as? Player ?: return@listen
                    val session = GuiRegistry.sessionFor(player) ?: return@listen
                    GuiRegistry.unregister(player)
                    // If session has a pending switch, it was already opened by the click handler
                    if (session.pendingSwitch() == null) {
                        // Normal close — nothing extra needed
                    }
                }

            extraHandles +=
                events.listen<PlayerQuitEvent> { event ->
                    GuiRegistry.unregister(event.player)
                }
        }

        return SculkPlatform(plugin, scheduler, events, commands, sculkConfig, sculkData, extraHandles)
    }
}
