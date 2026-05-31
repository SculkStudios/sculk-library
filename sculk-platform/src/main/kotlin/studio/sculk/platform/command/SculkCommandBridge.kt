package studio.sculk.platform.command

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.command.CommandBuilder
import studio.sculk.command.CommandNode
import studio.sculk.command.CooldownStore
import studio.sculk.command.brigadier.CommandCompiler
import studio.sculk.coroutine.SculkCoroutineScope

/**
 * Registers Sculk [CommandNode] trees into Paper's native Brigadier command system.
 *
 * Commands are compiled to Brigadier nodes (native tab completion, client-side error highlighting)
 * and registered through the [LifecycleEvents.COMMANDS] registrar. Register your commands inside
 * `onEnable` (where `SculkPlatform.create { }` is called) so they are queued before Paper fires the
 * commands lifecycle event.
 *
 * ```kotlin
 * sculk.commands.register(command("hello") { player { reply("<green>Hi!") } })
 * ```
 */
@SculkStable
public class SculkCommandBridge
    @SculkInternal
    constructor(
        private val plugin: JavaPlugin,
        scope: SculkCoroutineScope,
    ) : SculkHandle {
        private val compiler = CommandCompiler(scope, CooldownStore())
        private val pending = mutableListOf<CommandNode>()
        private var handlerRegistered = false

        /** Registers a command built with [builder]. */
        public fun register(builder: CommandBuilder): SculkCommandBridge = register(builder.node)

        /** Registers multiple commands at once. */
        public fun registerAll(vararg builders: CommandBuilder): SculkCommandBridge {
            builders.forEach { register(it) }
            return this
        }

        /** Registers a [CommandNode] tree. */
        @OptIn(SculkInternal::class)
        public fun register(node: CommandNode): SculkCommandBridge {
            pending += node
            ensureLifecycleHandler()
            return this
        }

        /**
         * Registers the single Brigadier lifecycle handler that drains [pending] when Paper fires
         * the commands event. Registered lazily on first command registration (during `onEnable`).
         */
        private fun ensureLifecycleHandler() {
            if (handlerRegistered) return
            handlerRegistered = true
            plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
                val registrar = event.registrar()
                for (node in pending) {
                    @OptIn(SculkInternal::class)
                    registrar.register(
                        compiler.compile(node),
                        node.description.ifBlank { null },
                        node.aliases.toList(),
                    )
                }
            }
        }

        /**
         * Commands registered through the Brigadier registrar are unregistered automatically when
         * the plugin disables, so no explicit teardown is required.
         */
        override fun close() {
            pending.clear()
        }
    }
