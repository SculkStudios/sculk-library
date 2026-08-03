package studio.sculk.discord

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.discord.interaction.DiscordActor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class GuildServiceTest {
    private val guild = GuildId("1035829461209384960")
    private val user = UserId("2035829461209384961")
    private val guilds = FakeGuildService().apply {
        put(guild, DiscordActor(user, "Daisy", guild))
    }

    @Test
    fun `role sync applies a whole target set in one call`() = runTest {
        // One request rather than a loop: applying a computed set piecewise broadcasts every
        // intermediate state and is rate-limited per step.
        guilds.setRoles(guild, user, setOf(RoleId("7"), RoleId("8")))

        val action = guilds.actions.single() as GuildAction.SetRoles
        assertEquals(setOf(RoleId("7"), RoleId("8")), action.roles)
    }

    @Test
    fun `acting on someone the bot cannot see fails rather than silently succeeding`() = runTest {
        val stranger = UserId("9999999999999999999")

        val result = guilds.addRole(guild, stranger, RoleId("7")) as SculkResult.Failure

        assertTrue(result.message.contains("not a known member"), result.message)
        assertTrue(guilds.actions.isEmpty())
    }

    @Test
    fun `banning someone who already left is allowed, because that is a real operation`() = runTest {
        val departed = UserId("9999999999999999999")

        val result = guilds.ban(guild, departed, reason = "evasion")

        assertTrue(result.isSuccess)
        assertEquals(departed, (guilds.actions.single() as GuildAction.Ban).user)
    }

    @Test
    fun `a deletion window past Discord's cap fails instead of being clamped`() = runTest {
        // Clamping turns "delete the last month" into seven days silently, and the only time anyone
        // notices is during an incident.
        val result = guilds.ban(guild, user, deleteMessageHours = 24 * 30) as SculkResult.Failure

        assertTrue(result.message.contains("168"), result.message)
    }

    @Test
    fun `a timeout longer than Discord allows fails by name`() = runTest {
        val result = guilds.timeout(guild, user, 40.days) as SculkResult.Failure

        assertTrue(result.message.contains("28"), result.message)
    }

    @Test
    fun `a timeout inside the cap is recorded with its duration`() = runTest {
        guilds.timeout(guild, user, 6.hours, reason = "spam")

        val action = guilds.actions.single() as GuildAction.Timeout
        assertEquals(6.hours, action.duration)
        assertEquals("spam", action.reason)
    }

    @Test
    fun `a zero-length timeout is refused`() = runTest {
        assertTrue(guilds.timeout(guild, user, kotlin.time.Duration.ZERO).isFailure)
    }

    @Test
    fun `clearing a nickname is distinct from setting one`() = runTest {
        guilds.setNickname(guild, user, null)

        assertEquals(null, (guilds.actions.single() as GuildAction.SetNickname).nickname)
    }

    @Test
    fun `a set failure makes every call fail, for testing the fallback path`() = runTest {
        guilds.failure = "the gateway is reconnecting"

        assertTrue(guilds.addRole(guild, user, RoleId("7")).isFailure)
        assertTrue(guilds.member(guild, user).isFailure)
        assertTrue(guilds.actions.isEmpty())
    }

    @Test
    fun `presence reflects only guilds with known members`() = runTest {
        assertTrue(guilds.isPresent(guild))
        assertFalse(guilds.isPresent(GuildId("4035829461209384963")))
    }
}
