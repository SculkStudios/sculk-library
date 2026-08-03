package studio.sculk.discord.jda

import net.dv8tion.jda.api.interactions.commands.OptionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.command.DiscordPermission
import studio.sculk.discord.command.OptionChoice
import studio.sculk.discord.command.discordCommand

class JdaCommandsTest {
    @Test
    fun `a flat command carries its options`() {
        val data = discordCommand("ban") {
            description = "Ban someone"
            user("target", "Who", required = true)
            string("reason", "Why")
            executes { }
        }.toJda()

        assertEquals("ban", data.name)
        assertEquals(listOf("target", "reason"), data.options.map { it.name })
        assertEquals(OptionType.USER, data.options.first().type)
        assertTrue(data.options.first().isRequired)
    }

    @Test
    fun `subcommands become subcommands, not options`() {
        val data = discordCommand("kit") {
            description = "Kits"
            sub("give") {
                description = "Give one"
                user("target", "Who", required = true)
                executes { }
            }
            sub("list") {
                description = "List them"
                executes { }
            }
        }.toJda()

        assertEquals(listOf("give", "list"), data.subcommands.map { it.name })
        assertTrue(data.options.isEmpty())
    }

    @Test
    fun `a child that has children of its own becomes a subcommand group`() {
        // The distinction Discord draws and the spec does not: a node is a group only because it has
        // children, so getting this wrong produces a command tree that registers but is unusable.
        val data = discordCommand("admin") {
            description = "Admin"
            sub("kit") {
                description = "Kit admin"
                sub("reload") {
                    description = "Reload kits"
                    executes { }
                }
            }
            sub("status") {
                description = "Status"
                executes { }
            }
        }.toJda()

        assertEquals(listOf("kit"), data.subcommandGroups.map { it.name })
        assertEquals(listOf("reload"), data.subcommandGroups.single().subcommands.map { it.name })
        assertEquals(listOf("status"), data.subcommands.map { it.name })
    }

    @Test
    fun `an option with an autocomplete lambda is marked autocompletable`() {
        val data = discordCommand("kit") {
            description = "Kits"
            string("name", "Which") { listOf(OptionChoice("starter", "starter")) }
            executes { }
        }.toJda()

        assertTrue(data.options.single().isAutoComplete)
    }

    @Test
    fun `fixed choices survive the translation`() {
        val data = discordCommand("mode") {
            description = "Set a mode"
            string("value", "Which", choices = listOf(OptionChoice("Fast", "fast"), OptionChoice("Slow", "slow")))
            executes { }
        }.toJda()

        assertEquals(listOf("Fast", "Slow"), data.options.single().choices.map { it.name })
    }

    @Test
    fun `a required permission becomes a default member permission`() {
        val data = discordCommand("ban") {
            description = "Ban"
            defaultPermission = DiscordPermission.BanMembers
            executes { }
        }.toJda()

        assertNotNull(data.defaultPermissions.permissionsRaw)
        assertEquals(DiscordPermission.BanMembers.bit, data.defaultPermissions.permissionsRaw)
    }

    @Test
    fun `an integer option gets long bounds, which is not interchangeable with double`() {
        // JDA throws rather than coercing when a double bound is set on an INTEGER option, so the
        // adapter has to pick the right overload per type.
        val data = discordCommand("give") {
            description = "Give"
            integer("amount", "How many", min = 1, max = 64)
            string("note", "A note")
            executes { }
        }.toJda()

        assertEquals(1L, data.options.first().minValue)
        assertEquals(64L, data.options.first().maxValue)
    }

    @Test
    fun `a number option gets double bounds`() {
        val data = discordCommand("scale") {
            description = "Scale"
            number("factor", "How much", min = 0.5, max = 2.0)
            executes { }
        }.toJda()

        assertEquals(0.5, data.options.single().minValue)
    }
}
