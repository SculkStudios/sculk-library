package gg.sculk.platform.command

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.command.CommandBuilder
import gg.sculk.core.command.CommandNode
import org.bukkit.plugin.java.JavaPlugin

/**
 * Registers [CommandNode] trees into Paper's server command map and provides cleanup.
 */
@SculkStable
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
     * Registers multiple commands at once.
     *
     * ```kotlin
     * sculk.commands.registerAll(
     *     helloCommand(),
     *     homeCommand(),
     *     adminCommand(),
     * )
     * ```
     */
    public fun registerAll(vararg builders: CommandBuilder): SculkCommandBridge {
        builders.forEach { register(it) }
        return this
    }

    /**
     * Registers a [CommandNode] into the Paper command map.
     */
    public fun register(node: CommandNode): SculkCommandBridge {
        val cmd = SculkBukkitCommand(node, plugin.name.lowercase())
        val commandMap = plugin.server.commandMap
        commandMap.register(plugin.name.lowercase(), cmd)
        registered += node.name
        registered += node.aliases
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
