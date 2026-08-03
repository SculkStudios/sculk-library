package studio.sculk.discord.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscordCommandSpecTest {
    private val kit = discordCommand("kit") {
        description = "Manage kits"
        sub("give") {
            description = "Give a kit to someone"
            user("target", "Who gets it", required = true)
            string("kit", "Which kit")
            executes { }
        }
        sub("list") {
            description = "List kits"
            executes { }
        }
    }

    @Test
    fun `usage renders required and optional options the way the Brigadier side does`() {
        assertEquals("/kit give <target> [kit]", kit.at("kit give")!!.usage("kit"))
    }

    @Test
    fun `flatten yields every invokable leaf with its full path`() {
        assertEquals(listOf("kit give", "kit list"), kit.flatten().map { it.first })
    }

    @Test
    fun `a group is not itself invokable`() {
        assertTrue(!kit.executable)
        assertNull(kit.flatten().find { it.first == "kit" })
    }

    @Test
    fun `an unknown path resolves to null rather than to the nearest match`() {
        assertNull(kit.at("kit destroy"))
        assertNull(kit.at("other give"))
    }

    @Test
    fun `an uppercase name is refused, because Discord rejects it at registration`() {
        // Rejecting the whole batch is how Discord reports this, so one bad name takes out every
        // command the bot tried to register.
        val error = assertThrows(IllegalArgumentException::class.java) { discordCommand("Kit") { executes { } } }

        assertTrue(error.message!!.contains("lowercase"), error.message)
    }

    @Test
    fun `a fourth level of nesting is refused, since Discord has only three`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            discordCommand("a") {
                sub("b") {
                    sub("c") {
                        sub("d") { executes { } }
                    }
                }
            }
        }

        assertTrue(error.message!!.contains("3"), error.message)
    }

    @Test
    fun `a node cannot have both subcommands and its own options`() {
        assertThrows(IllegalArgumentException::class.java) {
            discordCommand("a") {
                string("x", "an option")
                sub("b") { executes { } }
            }
        }
    }

    @Test
    fun `a group cannot carry its own handler, because Discord never invokes one`() {
        assertThrows(IllegalArgumentException::class.java) {
            discordCommand("a") {
                executes { }
                sub("b") { executes { } }
            }
        }
    }

    @Test
    fun `a required option after an optional one is refused`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            discordCommand("a") {
                string("first", "optional")
                string("second", "required", required = true)
                executes { }
            }
        }

        assertTrue(error.message!!.contains("required"), error.message)
    }

    @Test
    fun `an option cannot offer fixed choices and autocomplete at once`() {
        assertThrows(IllegalArgumentException::class.java) {
            CommandOption(
                name = "kit",
                description = "Which kit",
                type = OptionType.String,
                choices = listOf(OptionChoice("a", "a")),
                autocomplete = { emptyList() },
            )
        }
    }

    @Test
    fun `autocomplete is a lambda, so a set that changes after registration still suggests correctly`() {
        val kits = mutableListOf("starter")
        val spec = discordCommand("kit") {
            string("name", "Which kit") { typed -> kits.filter { it.startsWith(typed) }.map { OptionChoice(it, it) } }
            executes { }
        }

        kits += "veteran"

        val suggest = spec.options.single().autocomplete!!
        val suggested = kotlinx.coroutines.runBlocking { suggest("") }
        assertEquals(listOf("starter", "veteran"), suggested.map { it.name })
    }

    @Test
    fun `a permission bit is checked against the member, with administrator overriding`() {
        val plainMod = studio.sculk.discord.interaction.DiscordActor(
            id = studio.sculk.discord.UserId("1"),
            name = "Mod",
            guild = null,
            permissionBits = DiscordPermission.ManageMessages.bit,
        )
        val admin = plainMod.copy(permissionBits = DiscordPermission.Administrator.bit)

        assertTrue(plainMod.holds(DiscordPermission.ManageMessages))
        assertTrue(!plainMod.holds(DiscordPermission.BanMembers))
        assertTrue(admin.holds(DiscordPermission.BanMembers))
    }
}
