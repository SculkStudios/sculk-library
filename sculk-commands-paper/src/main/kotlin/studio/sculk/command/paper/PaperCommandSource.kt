package studio.sculk.command.paper

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.audience.Audience
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.command.RemoteConsoleCommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import studio.sculk.annotation.SculkStable
import studio.sculk.command.CommandContext
import studio.sculk.command.CommandSource
import studio.sculk.command.SenderKind
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.WorldKey
import studio.sculk.paper.ref
import java.util.UUID

/**
 * A Paper command sender. Under `/execute as`, the executing entity decides the kind, name and
 * context, while permissions stay the original sender's, as vanilla commands do.
 */
@SculkStable
public class PaperCommandSource(public val stack: CommandSourceStack) : CommandSource {
    private val entity: Entity? = stack.executor

    override val name: String get() = entity?.name ?: stack.sender.name

    override val kind: SenderKind
        get() = when {
            entity is Player -> SenderKind.PLAYER
            entity != null -> SenderKind.ENTITY
            stack.sender is ConsoleCommandSender -> SenderKind.CONSOLE
            stack.sender is RemoteConsoleCommandSender -> SenderKind.REMOTE
            stack.sender is BlockCommandSender -> SenderKind.BLOCK
            else -> SenderKind.CONSOLE
        }

    override val uniqueId: UUID? get() = entity?.uniqueId

    override val audience: Audience get() = entity ?: stack.sender

    override val executionContext: ExecutionContext
        get() {
            entity?.let { return ExecutionContext.Entity(it.ref) }
            val sender = stack.sender
            if (sender is BlockCommandSender) {
                val block = sender.block
                return ExecutionContext.Region.ofBlock(WorldKey(block.world.key.asString()), block.x, block.z)
            }
            return ExecutionContext.Global
        }

    override val platformSender: Any get() = stack

    override fun hasPermission(permission: String): Boolean = stack.sender.hasPermission(permission)

    override fun toString(): String = "PaperCommandSource($kind $name)"
}

/** The Paper sender, for code that needs Paper's own types. */
@SculkStable
public val CommandContext.stack: CommandSourceStack get() = source.platformSender as CommandSourceStack

/**
 * The player who ran the command. Declare the executor with `player { }` so only players reach it;
 * reading this anywhere else is a wiring bug and throws.
 */
@SculkStable
public val CommandContext.player: Player
    get() = stack.executor as? Player
        ?: throw IllegalStateException("/$label was not run by a player; declare its executor with player { }.")
