package studio.sculk.core.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import studio.sculk.core.annotation.SculkInternal
import java.time.Duration
import java.util.UUID

@OptIn(SculkInternal::class)
class CommandExecutionTest {
    @Test
    fun `dispatch executes nested subcommand with parsed argument`() {
        var amount = 0
        val sender = permittedSender()
        val root =
            command("coins") {
                sub("give") {
                    int("amount", min = 1, max = 10)
                    executes {
                        amount = argument("amount")
                    }
                }
            }.node

        CommandExecutor.dispatch(root, sender, "coins", arrayOf("give", "7"))

        assertEquals(7, amount)
    }

    @Test
    fun `permission denial prevents execution`() {
        var executed = false
        val sender = mock<CommandSender>()
        whenever(sender.hasPermission("coins.admin")).thenReturn(false)
        whenever(sender.name).thenReturn("Console")
        val root =
            command("coins") {
                permission = "coins.admin"
                executes { executed = true }
            }.node

        CommandExecutor.dispatch(root, sender, "coins", emptyArray())

        assertEquals(false, executed)
    }

    @Test
    fun `cooldown blocks repeated execution`() {
        var executions = 0
        val sender = permittedSender("CooldownSender")
        val root =
            command("ping") {
                cooldown("ping", Duration.ofMinutes(1))
                executes { executions++ }
            }.node

        CommandExecutor.dispatch(root, sender, "ping", emptyArray())
        CommandExecutor.dispatch(root, sender, "ping", emptyArray())

        assertEquals(1, executions)
    }

    @Test
    fun `optional argument rejects invalid provided value`() {
        var executions = 0
        val sender = permittedSender()
        val root =
            command("limit") {
                int("amount", optional = true, min = 1, max = 10)
                executes { executions++ }
            }.node

        CommandExecutor.dispatch(root, sender, "limit", arrayOf("wrong"))

        assertEquals(0, executions)
    }

    @Test
    fun `permission aware help hides inaccessible subcommands`() {
        val sender = mock<CommandSender>()
        val messages = mutableListOf<String>()
        whenever(sender.name).thenReturn("Console")
        whenever(sender.hasPermission("coins.admin")).thenReturn(false)
        whenever(sender.sendMessage(org.mockito.kotlin.any<net.kyori.adventure.text.Component>())).thenAnswer {
            messages += it.arguments[0].toString()
            Unit
        }
        val root =
            command("coins") {
                sub("public") {
                    description = "Visible command"
                    executes {}
                }
                sub("admin") {
                    permission = "coins.admin"
                    description = "Hidden command"
                    executes {}
                }
            }.node

        CommandExecutor.dispatch(root, sender, "coins", arrayOf("help"))

        assertEquals(true, messages.any { it.contains("public") })
        assertEquals(false, messages.any { it.contains("admin") })
    }

    @Test
    fun `player executor only runs for players`() {
        var executions = 0
        val player = mock<Player>()
        whenever(player.uniqueId).thenReturn(UUID.randomUUID())
        whenever(player.name).thenReturn("Player")
        val root =
            command("profile") {
                player { executions++ }
            }.node

        CommandExecutor.dispatch(root, player, "profile", emptyArray())

        assertEquals(1, executions)
    }

    private fun permittedSender(name: String = "Console"): CommandSender {
        val sender = mock<CommandSender>()
        whenever(sender.name).thenReturn(name)
        return sender
    }
}
