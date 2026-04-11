package gg.sculk.platform.command

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.command.CommandBuilder
import gg.sculk.core.command.CommandNode
import org.bukkit.plugin.java.JavaPlugin

/**
 * Registers [CommandNode] trees into Paper's server command map and provides cleanup.
 */
@SculkInternal
public class SculkCommandBridge(
    private val plugin: JavaPlugin,
) : SculkHandle {
    private val registered = mutableListOf<String>()

    /**
     * Registers a command built with [builder] into the Paper command map.
     *
     * Returns the [SculkCommandBridge] for chaining.
     */
    public fun register(builder: CommandBuilder): SculkCommandBridge = register(builder.node)

    /**
     * Registers a [CommandNode] into the Paper command map.
     */
    public fun register(node: CommandNode): SculkCommandBridge {
        val cmd = SculkBukkitCommand(node, plugin.name.lowercase())
        val commandMap = plugin.server.commandMap
        commandMap.register(plugin.name.lowercase(), cmd)
        registered += node.name
        return this
    }

    /** Unregisters all commands registered through this bridge. */
    override fun close() {
        val commandMap = plugin.server.commandMap
        registered.forEach { name ->
            commandMap.getCommand(name)?.unregister(commandMap)
        }
        registered.clear()
    }
}
