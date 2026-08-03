package studio.sculk.discord.interaction

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.discord.ChannelId
import studio.sculk.discord.ComponentId
import studio.sculk.discord.GuildId
import studio.sculk.discord.MessageId
import studio.sculk.discord.UserId
import studio.sculk.discord.command.discordCommand
import studio.sculk.discord.message.DiscordMessage
import studio.sculk.discord.message.Text
import studio.sculk.discord.message.message
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

class InteractionRouterTest {
    private val router = InteractionRouter(Logger.getLogger("test"))
    private val actor = DiscordActor(UserId("1"), "Daisy", GuildId("2"))

    /** Records what was said back, so a test can assert the user was answered at all. */
    private open class RecordingInteraction(override val actor: DiscordActor, override val path: String = "") : DiscordCommandContext {
        val said = mutableListOf<String>()
        override var acknowledged: Boolean = false
            protected set

        override val channel: ChannelId = ChannelId("3")
        override val guild: GuildId? = actor.guild

        override fun optionOrNull(name: String): OptionValue? = null

        override suspend fun reply(message: DiscordMessage): SculkResult<Unit> {
            acknowledged = true
            said += (message.components.first() as Text).markdown
            return SculkResult.ok()
        }

        override suspend fun replyModal(modal: Modal): SculkResult<Unit> {
            acknowledged = true
            said += "modal:${modal.id}"
            return SculkResult.ok()
        }

        override suspend fun defer(ephemeral: Boolean): SculkResult<DeferredInteraction> {
            acknowledged = true
            deferrals++
            return SculkResult.failure("not needed in this test")
        }

        /** How many times something deferred this, so the auto-defer watchdog is observable. */
        var deferrals: Int = 0
            private set
    }

    @Test
    fun `a subcommand resolves by walking the tree`() {
        router.register(
            discordCommand("kit") {
                sub("give") { executes { } }
                sub("list") { executes { } }
            },
        )

        assertNotNull(router.resolve("kit give"))
        assertNotNull(router.resolve("kit list"))
    }

    @Test
    fun `both branches of a two-subcommand tree are reachable`() = runTest {
        // The bug this shape prevents: a `when (event.name)` chain reached only the first arm, so
        // half of what the DSL advertised silently did nothing.
        val reached = mutableListOf<String>()
        router.register(
            discordCommand("kit") {
                sub("give") { executes { reached += "give" } }
                sub("list") { executes { reached += "list" } }
            },
        )

        router.dispatch(RecordingInteraction(actor, "kit give"))
        router.dispatch(RecordingInteraction(actor, "kit list"))

        assertEquals(listOf("give", "list"), reached)
    }

    @Test
    fun `a group is not resolvable, because Discord never invokes one`() {
        router.register(discordCommand("kit") { sub("give") { executes { } } })

        assertNull(router.resolve("kit"))
    }

    @Test
    fun `an unknown command still answers, rather than leaving the user on thinking`() = runTest {
        val interaction = RecordingInteraction(actor, "ghost")

        router.dispatch(interaction)

        assertEquals(1, interaction.said.size)
        assertTrue(interaction.said.single().contains("cached"), interaction.said.single())
    }

    @Test
    fun `a handler that throws still answers`() = runTest {
        router.register(discordCommand("boom") { executes { error("kaboom") } })
        val interaction = RecordingInteraction(actor, "boom")

        router.dispatch(interaction)

        assertEquals(1, interaction.said.size)
        assertTrue(interaction.said.single().contains("went wrong"), interaction.said.single())
    }

    @Test
    fun `a handler slower than the deadline is deferred for, rather than left to time out`() = runTest {
        // Discord discards an unanswered interaction after three seconds and shows a permanent
        // "the application did not respond", which reads as the bot being dead rather than as a
        // handler that forgot to defer.
        val router = InteractionRouter(Logger.getLogger("test"), autoDeferAfter = 2.seconds)
        router.register(discordCommand("slow") { executes { delay(10.seconds) } })
        val interaction = RecordingInteraction(actor, "slow")

        router.dispatch(interaction)

        assertTrue(interaction.acknowledged, "the router should have acknowledged on the handler's behalf")
        assertEquals(1, interaction.deferrals)
    }

    @Test
    fun `a handler that answers in time is not deferred for`() = runTest {
        val router = InteractionRouter(Logger.getLogger("test"), autoDeferAfter = 2.seconds)
        router.register(discordCommand("fast") { executes { reply(message { text("done") }) } })
        val interaction = RecordingInteraction(actor, "fast")

        router.dispatch(interaction)

        assertEquals(0, interaction.deferrals)
        assertEquals(listOf("done"), interaction.said)
    }

    @Test
    fun `registering the same command name twice fails loudly`() {
        router.register(discordCommand("kit") { executes { } })

        assertThrows(IllegalArgumentException::class.java) {
            router.register(discordCommand("kit") { executes { } })
        }
    }

    @Test
    fun `closing a registration handle removes the command`() {
        val handle = router.register(discordCommand("kit") { executes { } })
        assertNotNull(router.resolve("kit"))

        handle.close()

        assertNull(router.resolve("kit"))
    }

    @Test
    fun `a component addressed to another plugin's namespace is ignored`() = runTest {
        var reached = false
        router.onComponent("mine") { reached = true }

        router.dispatch(componentInteraction(ComponentId.parse("theirs:thing")!!))

        assertTrue(!reached)
    }

    @Test
    fun `a component in a registered namespace reaches its handler`() = runTest {
        var seen: ComponentId? = null
        router.onComponent("punish") { seen = it.componentId }

        val id = ComponentId.of("punish", "ban", "r1").getOrThrow()
        router.dispatch(componentInteraction(id))

        assertEquals(id, seen)
    }

    private fun componentInteraction(id: ComponentId): ComponentInteraction = object : ComponentInteraction {
        override val actor: DiscordActor = this@InteractionRouterTest.actor
        override val channel: ChannelId = ChannelId("3")
        override val guild: GuildId? = actor.guild
        override val acknowledged: Boolean = false
        override val componentId: ComponentId = id
        override val messageId: MessageId = MessageId("9")
        override val selected: List<String> = emptyList()

        override suspend fun reply(message: DiscordMessage): SculkResult<Unit> = SculkResult.ok()

        override suspend fun replyModal(modal: Modal): SculkResult<Unit> = SculkResult.ok()

        override suspend fun defer(ephemeral: Boolean): SculkResult<DeferredInteraction> = SculkResult.failure("not needed in this test")
    }
}
