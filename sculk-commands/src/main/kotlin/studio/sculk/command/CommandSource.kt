package studio.sculk.command

import net.kyori.adventure.audience.Audience
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.ExecutionContext
import studio.sculk.permission.PermissionSubject
import java.util.UUID

/**
 * Who ran a command, as every platform describes it. Platform modules wrap their own sender types;
 * code that needs the platform object reads [platformSender]. Permissions, and the context that
 * contextual permissions read, are the sender's as a [PermissionSubject].
 */
@SculkStable
public interface CommandSource : PermissionSubject {
    /** A player's name, `Console`, or a command block's name. */
    public val name: String

    public val kind: SenderKind

    /** The sender's identity for cooldowns and confirmations; null for the console and blocks, which share one. */
    public val uniqueId: UUID?

    /** Where messages to this sender go. */
    public val audience: Audience

    /**
     * The context the executor starts in: the player's entity, the global region for the console,
     * the proxy executor on Velocity (THREADING.md rule 9).
     */
    public val executionContext: ExecutionContext

    /** The platform's own sender object, such as Paper's `CommandSourceStack`. */
    public val platformSender: Any
}

/** What kind of sender ran a command. */
@SculkStable
public enum class SenderKind {
    PLAYER,
    CONSOLE,

    /** A non-player entity, through `/execute as`. */
    ENTITY,

    /** A command block or command block minecart. */
    BLOCK,

    /** A remote console (RCON). */
    REMOTE,
    ;

    public val isPlayer: Boolean get() = this == PLAYER
}
