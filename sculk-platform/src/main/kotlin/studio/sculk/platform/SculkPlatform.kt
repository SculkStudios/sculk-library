package studio.sculk.platform

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.config.SculkConfig
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkStable
import studio.sculk.core.gui.GuiContext
import studio.sculk.core.gui.GuiRegistry
import studio.sculk.core.scheduler.SculkScheduler
import studio.sculk.data.SculkData
import studio.sculk.integrations.SculkIntegrations
import studio.sculk.packets.PacketServiceConfig
import studio.sculk.packets.SculkPacketService
import studio.sculk.packets.SculkPacketServices
import studio.sculk.platform.command.SculkCommandBridge
import studio.sculk.platform.event.SculkEventBus
import java.util.concurrent.atomic.AtomicBoolean

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
    private val configService: SculkConfig?,
    private val dataService: SculkData?,
    private val integrationService: SculkIntegrations?,
    private val packetServiceResult: SculkResult<SculkPacketService>?,
    private val handles: List<SculkHandle>,
) : SculkHandle {
    private val closed = AtomicBoolean(false)

    /** Config system. Requires [SculkPlatformBuilder.config] during platform creation. */
    public val config: SculkConfig
        get() = configService ?: missingSubsystem("config", "config()")

    /** Data layer. Requires [SculkPlatformBuilder.data] during platform creation. */
    public val data: SculkData
        get() = dataService ?: missingSubsystem("data", "data()")

    /** Optional integration adapters. Requires [SculkPlatformBuilder.integrations]. */
    public val integrations: SculkIntegrations
        get() = integrationService ?: missingSubsystem("integrations", "integrations()")

    /** Packet service. Requires [SculkPlatformBuilder.packets] and a successful backend. */
    public val packets: SculkPacketService
        get() =
            when (val result = packetServiceResult) {
                is SculkResult.Success -> result.value
                is SculkResult.Failure -> throw IllegalStateException(result.message, result.cause)
                null -> missingSubsystem("packets", "packets { ... }")
            }

    /** Packet backend result for plugins that can run without packet support. */
    public val packetsResult: SculkResult<SculkPacketService>?
        get() = packetServiceResult

    /**
     * Tears down all Sculk components in reverse-registration order.
     *
     * Unregisters all event listeners, commands, closes GUI sessions, and shuts down the data pool.
     * Safe to call multiple times.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handles.asReversed().forEach { it.close() }
        GuiRegistry.closeAll()
        commands.close()
        events.close()
        dataService?.close()
    }

    private fun <T> missingSubsystem(
        name: String,
        call: String,
    ): T = throw IllegalStateException("Sculk $name subsystem is not enabled. Call $call in SculkPlatform.create { ... }.")

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
    private var integrationsEnabled = false
    private var packetConfig: PacketServiceConfig? = null

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

    /** Enables optional integration adapters such as PlaceholderAPI, Vault, and LuckPerms. */
    @SculkStable
    public fun integrations() {
        integrationsEnabled = true
    }

    /** Enables optional packet APIs. PacketEvents is preferred when the backend mode is Auto. */
    @SculkStable
    public fun packets(block: PacketServiceConfig.() -> Unit = {}) {
        packetConfig = PacketServiceConfig().apply(block)
    }

    internal fun build(): SculkPlatform {
        val scheduler = PaperScheduler(plugin)
        val events = SculkEventBus(plugin)
        val commands = SculkCommandBridge(plugin)
        val extraHandles = mutableListOf<SculkHandle>()

        // Wire Folia-aware GUI dispatch — done unconditionally so Gui.openFor() works
        // even when the gui() subsystem is not enabled for event routing.
        @OptIn(studio.sculk.core.annotation.SculkInternal::class)
        GuiRegistry.init(plugin, FoliaDetector.isFolia)

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

        val sculkIntegrations = if (integrationsEnabled) SculkIntegrations(plugin) else null

        val sculkPackets =
            packetConfig?.let { config ->
                SculkPacketServices.create(plugin, scheduler, config).also { result ->
                    when (result) {
                        is SculkResult.Success -> extraHandles += result.value
                        is SculkResult.Failure ->
                            if (config.required) {
                                throw IllegalStateException(result.message, result.cause)
                            }
                    }
                }
            }

        // GUI event routing
        if (guiEnabled) {
            extraHandles +=
                events.listen<InventoryClickEvent> { event ->
                    val session = GuiRegistry.sessionForInventory(event.view.topInventory) ?: return@listen
                    val player = event.whoClicked as? Player ?: return@listen
                    event.isCancelled = true
                    val item = session.gui.items[event.rawSlot] ?: return@listen
                    val handler = item.clickHandler ?: return@listen

                    @OptIn(studio.sculk.core.annotation.SculkInternal::class)
                    val ctx = GuiContext(player, event.click, event, session)
                    handler(ctx)
                    // Handle pending GUI switch (opened via ctx.open())
                    val pending = session.pendingSwitch()
                    if (pending != null) pending.openFor(player)
                }

            extraHandles +=
                events.listen<InventoryCloseEvent> { event ->
                    val player = event.player as? Player ?: return@listen

                    // Use inventory identity — prevents unregistering a *new* session when
                    // openFor() is called during a click handler (the new session is already
                    // registered under the player's UUID before the old inventory fires its close event).
                    @OptIn(studio.sculk.core.annotation.SculkInternal::class)
                    val session = GuiRegistry.sessionForInventory(event.inventory) ?: return@listen
                    session.gui.closeHandler?.invoke(session)
                    GuiRegistry.unregister(player)
                    @OptIn(studio.sculk.core.annotation.SculkInternal::class)
                    session.markClosed()
                }

            extraHandles +=
                events.listen<InventoryDragEvent> { event ->
                    GuiRegistry.sessionForInventory(event.view.topInventory) ?: return@listen
                    if (event.rawSlots.any { it < event.view.topInventory.size }) {
                        event.isCancelled = true
                    }
                }

            extraHandles +=
                events.listen<PlayerQuitEvent> { event ->
                    GuiRegistry.unregister(event.player)
                }
        }

        return SculkPlatform(
            plugin,
            scheduler,
            events,
            commands,
            sculkConfig,
            sculkData,
            sculkIntegrations,
            sculkPackets,
            extraHandles,
        )
    }
}
