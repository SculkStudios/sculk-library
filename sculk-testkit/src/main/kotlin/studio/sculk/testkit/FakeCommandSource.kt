package studio.sculk.testkit

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import studio.sculk.annotation.SculkStable
import studio.sculk.command.CommandSource
import studio.sculk.command.SenderKind
import studio.sculk.execution.ExecutionContext
import java.util.UUID

/**
 * A [CommandSource] for tests: holds its permissions, records every message it is sent. A
 * permission of `*` grants everything, as an operator has. Thread-safe.
 */
@SculkStable
public class FakeCommandSource(
    override val name: String = "Steve",
    override val kind: SenderKind = SenderKind.PLAYER,
    permissions: Set<String> = emptySet(),
    override val executionContext: ExecutionContext = ExecutionContext.Global,
    override val uniqueId: UUID? = if (kind.isPlayer) UUID.nameUUIDFromBytes("FakePlayer:$name".toByteArray()) else null,
) : CommandSource {
    private val lock = Any()
    private val granted = permissions.toMutableSet()
    private val received = mutableListOf<Component>()

    override val audience: Audience = object : Audience {
        override fun sendMessage(message: Component) {
            synchronized(lock) { received += message }
        }
    }

    override val platformSender: Any get() = this

    /** Every message sent so far, oldest first. */
    public val messages: List<Component> get() = synchronized(lock) { received.toList() }

    /** [messages] as plain text, for assertions that ignore colour. */
    public val plainMessages: List<String> get() = messages.map(PlainTextComponentSerializer.plainText()::serialize)

    override fun hasPermission(permission: String): Boolean = synchronized(lock) { "*" in granted || permission in granted }

    public fun grant(permission: String) {
        synchronized(lock) { granted += permission }
    }

    public fun revoke(permission: String) {
        synchronized(lock) { granted -= permission }
    }

    public fun clearMessages() {
        synchronized(lock) { received.clear() }
    }

    override fun toString(): String = "FakeCommandSource($kind $name)"

    @SculkStable
    public companion object {
        /** The server console: every permission, no unique id. */
        public fun console(): FakeCommandSource = FakeCommandSource("Console", SenderKind.CONSOLE, setOf("*"))
    }
}
