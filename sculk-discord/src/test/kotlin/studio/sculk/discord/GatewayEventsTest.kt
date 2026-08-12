package studio.sculk.discord

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.interaction.DiscordActor

class GatewayEventsTest {
    private val gateway = FakeDiscordGateway()
    private val guild = GuildId("3035829461209384962")
    private val channel = ChannelId("1035829461209384960")
    private val author = DiscordActor(UserId("2035829461209384961"), "Daisy", guild)

    private fun chat(content: String) = DiscordChatMessage(
        id = MessageId("9"),
        channel = channel,
        guild = guild,
        author = author,
        content = content,
    )

    @Test
    fun `an edit reaches an edit handler with the text as it now reads`() = runTest {
        var edited: String? = null
        gateway.onMessageEdit { edited = it.content }

        gateway.deliverEdit(chat("actually, 20 minutes"))

        assertEquals("actually, 20 minutes", edited)
    }

    @Test
    fun `an edit does not reach the plain message handler`() = runTest {
        var seen = 0
        gateway.onMessage { seen++ }

        gateway.deliverEdit(chat("edited"))

        assertEquals(0, seen)
    }

    @Test
    fun `a bot's edit is filtered, the same as a bot's message`() = runTest {
        var seen = 0
        gateway.onMessageEdit { seen++ }

        gateway.deliverEdit(chat("relayed back").copy(fromBot = true))

        assertEquals(0, seen)
    }

    @Test
    fun `a deletion carries only ids, since Discord sends nothing else`() = runTest {
        var deleted: DeletedMessage? = null
        gateway.onMessageDelete { deleted = it }

        gateway.deliverDelete(DeletedMessage(MessageId("9"), channel, guild))

        assertEquals(MessageId("9"), deleted?.id)
        assertEquals(channel, deleted?.channel)
    }

    @Test
    fun `a role change carries both the delta and the resulting set`() = runTest {
        var change: MemberChange? = null
        gateway.onMemberChange { change = it }
        val donor = RoleId("77")
        val after = author.copy(roles = setOf(donor))

        gateway.deliverMemberChange(MemberChange.RolesChanged(guild, author.id, after, added = setOf(donor)))

        val roles = change as MemberChange.RolesChanged
        assertEquals(setOf(donor), roles.added)
        assertEquals(setOf(donor), roles.actor.roles)
        assertTrue(roles.removed.isEmpty())
    }

    @Test
    fun `a departure carries no actor, because there is no member left to describe`() = runTest {
        var change: MemberChange? = null
        gateway.onMemberChange { change = it }

        gateway.deliverMemberChange(MemberChange.Left(guild, author.id))

        assertEquals(author.id, change?.user)
        assertNull((change as? MemberChange.Joined)?.actor)
    }

    @Test
    fun `a closed handle stops delivery`() = runTest {
        var seen = 0
        val handle = gateway.onMessageEdit { seen++ }

        handle.close()
        gateway.deliverEdit(chat("after close"))

        assertEquals(0, seen)
    }

    @Test
    fun `typing is recorded so a slow command can be shown to be working`() = runTest {
        gateway.connect()

        gateway.sendTyping(channel)

        assertEquals(listOf(channel), gateway.typing)
    }
}
