package studio.sculk.discord

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.discord.interaction.DiscordActor

class DiscordRoleTest {
    private val guild = GuildId("3035829461209384962")
    private val admin = DiscordRole(RoleId("10"), "Admin", colorRgb = 0xFF0000, position = 30)
    private val organisational = DiscordRole(RoleId("11"), "Staff", colorRgb = null, position = 20)
    private val donor = DiscordRole(RoleId("12"), "Donor", colorRgb = 0x00FF00, position = 10)

    private fun actor(vararg roles: DiscordRole) = DiscordActor(
        id = UserId("2035829461209384961"),
        name = "Daisy",
        guild = guild,
        roles = roles.map { it.id }.toSet(),
    )

    @Test
    fun `the highest coloured role wins, not simply the highest`() {
        val member = actor(organisational, donor)

        assertEquals(donor, member.highestColoredRole(listOf(admin, organisational, donor)))
    }

    @Test
    fun `a member with no coloured role has no accent`() {
        val member = actor(organisational)

        assertNull(member.highestColoredRole(listOf(admin, organisational, donor)))
    }

    @Test
    fun `roles the member does not hold are ignored, so one guild list serves every member`() {
        val member = actor(donor)

        assertEquals(donor, member.highestColoredRole(listOf(admin, organisational, donor)))
    }

    @Test
    fun `a member with no roles at all has no accent`() {
        assertNull(actor().highestColoredRole(listOf(admin)))
    }

    @Test
    fun `the fake refuses a role nobody declared, rather than inventing one`() = runTest {
        val guilds = FakeGuildService()

        val result = guilds.role(guild, RoleId("10"))

        assertTrue(result is SculkResult.Failure)
    }

    @Test
    fun `declared roles come back highest first`() = runTest {
        val guilds = FakeGuildService()
        listOf(donor, admin, organisational).forEach { guilds.putRole(guild, it) }

        assertEquals(listOf(admin, organisational, donor), guilds.roles(guild).getOrNull())
    }

    @Test
    fun `a bulk lookup asks once and omits members who left`() = runTest {
        val guilds = FakeGuildService()
        val present = actor(donor)
        guilds.put(guild, present)
        val departed = UserId("999")

        val found = guilds.members(guild, setOf(present.id, departed)).getOrNull()

        assertEquals(mapOf(present.id to present), found)
        assertEquals(1, guilds.memberLookups.size)
    }
}
