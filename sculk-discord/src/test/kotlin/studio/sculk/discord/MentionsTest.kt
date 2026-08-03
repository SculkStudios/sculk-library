package studio.sculk.discord

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.message.Text
import studio.sculk.discord.message.message

class MentionsTest {
    @Test
    fun `a message pings nothing unless it says otherwise`() {
        val alert = message { text("@everyone <@&123> hello") }

        assertEquals(Mentions.None, alert.mentions)
    }

    @Test
    fun `an at-everyone in the body is left in the text rather than mangled`() {
        // The body is evidence. Rewriting it to defuse a ping corrupts what a moderator is reading,
        // and misses <@&roleId> anyway; the allow-list is what makes it inert.
        val body = "@everyone look at this"
        val alert = message { text(body) }

        assertEquals(body, (alert.components.single() as Text).markdown)
    }

    @Test
    fun `allowing one user does not allow everyone`() {
        val allow = Mentions.user(UserId("42"))

        assertEquals(setOf(UserId("42")), allow.users)
        assertTrue(allow.roles.isEmpty())
        assertFalse(allow.everyone)
    }

    @Test
    fun `roles and users are separate allow-lists`() {
        val allow = Mentions.roles(listOf(RoleId("7"), RoleId("8")))

        assertEquals(2, allow.roles.size)
        assertTrue(allow.users.isEmpty())
    }
}
