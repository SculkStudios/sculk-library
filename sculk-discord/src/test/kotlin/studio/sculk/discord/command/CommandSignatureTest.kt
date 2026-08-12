package studio.sculk.discord.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CommandSignatureTest {
    private fun link(description: String = "Link a Minecraft account") = discordCommand("link") {
        this.description = description
        string("code", "Your in-game code", required = true)
    }

    @Test
    fun `the same command twice fingerprints the same`() {
        assertEquals(link().signature(), link().signature())
    }

    @Test
    fun `changing what Discord is told changes the fingerprint`() {
        assertNotEquals(link().signature(), link("Link your account").signature())
    }

    @Test
    fun `changing only the handler does not, since Discord stores none of it`() {
        val withHandler = discordCommand("link") {
            description = "Link a Minecraft account"
            string("code", "Your in-game code", required = true)
            executes { }
        }

        assertEquals(link().signature(), withHandler.signature())
    }

    @Test
    fun `an added option changes the fingerprint`() {
        val extra = discordCommand("link") {
            description = "Link a Minecraft account"
            string("code", "Your in-game code", required = true)
            boolean("quiet", "Answer privately")
        }

        assertNotEquals(link().signature(), extra.signature())
    }

    @Test
    fun `a changed subcommand changes the parent fingerprint`() {
        fun spec(subDescription: String) = discordCommand("daisy") {
            description = "Admin"
            sub("status") { description = subDescription }
        }

        assertNotEquals(spec("Show status").signature(), spec("Show bridge status").signature())
    }

    @Test
    fun `registration order does not count as a change`() {
        val console = discordCommand("console") { description = "Run a command" }

        assertEquals(
            DiscordCommandSpec.signatureOf(listOf(link(), console)),
            DiscordCommandSpec.signatureOf(listOf(console, link())),
        )
    }

    @Test
    fun `a different permission is a change worth re-registering for`() {
        val open = discordCommand("console") { description = "Run a command" }
        val gated = discordCommand("console") {
            description = "Run a command"
            defaultPermission = DiscordPermission.Administrator
        }

        assertNotEquals(open.signature(), gated.signature())
    }
}
