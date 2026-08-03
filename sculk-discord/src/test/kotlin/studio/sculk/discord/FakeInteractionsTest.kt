package studio.sculk.discord

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.discord.command.DiscordPermission
import studio.sculk.discord.command.discordCommand
import studio.sculk.discord.interaction.DiscordActor
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.discord.interaction.modal
import studio.sculk.discord.message.message
import java.util.logging.Logger

class FakeInteractionsTest {
    private val actor = DiscordActor(UserId("2035829461209384961"), "Daisy", GuildId("3035829461209384962"))
    private val router = InteractionRouter(Logger.getLogger("test"))
    private val id = ComponentId.of("punish", "ban", "r1").getOrThrow()

    @Test
    fun `a command handler is testable end to end with no gateway`() = runTest {
        router.register(
            discordCommand("kit") {
                sub("give") {
                    user("target", "Who", required = true)
                    executes { reply(message { text("Gave it to <@${option("target").asUser.raw}>.") }) }
                }
            },
        )
        val target = UserId("4035829461209384963")
        val context = FakeCommandContext(actor, "kit give", mapOf("target" to FakeOption(user = target)))

        router.dispatch(context)

        assertEquals("Gave it to <@${target.raw}>.", context.recorder.lastAnswer?.text)
    }

    @Test
    fun `a component handler is testable end to end`() = runTest {
        router.onComponent("punish") { it.reply(message { text("Banned ${it.componentId.part(1)}.") }) }
        val interaction = FakeComponentInteraction(actor, id)

        router.dispatch(interaction)

        assertEquals("Banned r1.", interaction.recorder.lastAnswer?.text)
    }

    @Test
    fun `a modal submission is testable end to end`() = runTest {
        router.onModal("punish") { it.reply(message { text("Reason: ${it.field("reason")}") }) }
        val submission = FakeModalInteraction(actor, id, mapOf("reason" to "spam"))

        router.dispatch(submission)

        assertEquals("Reason: spam", submission.recorder.lastAnswer?.text)
    }

    @Test
    fun `a handler that says nothing is caught, which is the bug that hides`() = runTest {
        // Silence leaves the user on "thinking…" until Discord times it out — indistinguishable from
        // the bot being dead, and where the historical bugs in this area lived.
        router.onComponent("punish") { }
        val interaction = FakeComponentInteraction(actor, id)

        router.dispatch(interaction)

        assertTrue(interaction.recorder.silent)
    }

    @Test
    fun `opening a modal after acknowledging fails, the way Discord fails`() = runTest {
        val interaction = FakeComponentInteraction(actor, id)
        interaction.defer()

        val result = interaction.replyModal(modal(id, "Why?") { field("reason", "Reason") }) as SculkResult.Failure

        assertTrue(result.message.contains("first response"), result.message)
    }

    @Test
    fun `replying twice fails, the way Discord fails`() = runTest {
        val interaction = FakeComponentInteraction(actor, id)
        interaction.reply(message { text("once") })

        assertTrue(interaction.reply(message { text("twice") }).isFailure)
    }

    @Test
    fun `a deferred follow-up can be public, for the notice the channel must see`() = runTest {
        val interaction = FakeComponentInteraction(actor, id)
        val deferred = interaction.defer().getOrThrow()

        deferred.respond("Ephemeral by default")
        deferred.respond("**Daisy** banned Steve", ephemeral = false)

        assertTrue(interaction.recorder.answers.first().ephemeral)
        assertFalse(interaction.recorder.answers.last().ephemeral)
    }

    @Test
    fun `an option read as the wrong type throws rather than answering zero`() = runTest {
        val context = FakeCommandContext(actor, "kit", mapOf("amount" to FakeOption(long = 3)))

        val error = runCatching { context.option("amount").asUser }.exceptionOrNull()

        assertTrue(error != null)
    }

    @Test
    fun `role grants fold to the union across a member's roles`() {
        val staff = RoleId("7")
        val admin = RoleId("8")
        val member = actor.copy(roles = setOf(staff, admin))

        val nodes = member.permissionsFor(
            mapOf(
                staff to setOf("punish.warn"),
                admin to setOf("punish.ban", "punish.warn"),
                RoleId("9") to setOf("never.granted"),
            ),
        )

        assertEquals(setOf("punish.warn", "punish.ban"), nodes)
    }

    @Test
    fun `a member with no mapped role gets nothing, not a fallback`() {
        // Being server owner in Discord grants nothing here. Powers over players are stated by the
        // operator or they do not exist.
        val owner = actor.copy(roles = emptySet(), permissionBits = DiscordPermission.Administrator.bit)

        assertTrue(owner.permissionsFor(mapOf(RoleId("7") to setOf("punish.ban"))).isEmpty())
    }
}
