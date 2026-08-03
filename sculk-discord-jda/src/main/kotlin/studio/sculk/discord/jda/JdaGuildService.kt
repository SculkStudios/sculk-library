package studio.sculk.discord.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import studio.sculk.SculkResult
import studio.sculk.coroutine.await
import studio.sculk.discord.GuildId
import studio.sculk.discord.GuildService
import studio.sculk.discord.RoleId
import studio.sculk.discord.UserId
import studio.sculk.discord.interaction.DiscordActor
import studio.sculk.map
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import java.time.Duration as JavaDuration

/**
 * Roles, nicknames and moderation, on JDA.
 *
 * Every failure here names the role hierarchy where it plausibly applies. Discord refuses any action
 * against a member whose highest role sits at or above the bot's, and no amount of granting the bot
 * permissions fixes it — the bot's role has to be dragged up the list. That is the cause of the
 * overwhelming majority of "the bot has Administrator and still cannot ban" reports, and it is not
 * discoverable from the error Discord returns.
 */
internal class JdaGuildService(private val client: () -> JDA?) : GuildService {
    override suspend fun member(guild: GuildId, user: UserId): SculkResult<DiscordActor> = withGuild(guild) { target ->
        val member = runCatching { target.retrieveMemberById(user.raw).submit().await() }.getOrNull()
            ?: return@withGuild SculkResult.failure(
                "User ${user.raw} is not a member of ${target.name}. If they are, the bot needs the " +
                    "GuildMembers intent to see members who have not spoken since it started.",
            )
        SculkResult.success(member.toActor())
    }

    override suspend fun isPresent(guild: GuildId): Boolean = client()?.getGuildById(guild.raw) != null

    override suspend fun addRole(guild: GuildId, user: UserId, role: RoleId): SculkResult<Unit> =
        withMemberAndRole(guild, user, role) { target, member, resolved ->
            attemptHierarchy("add ${resolved.name} to ${member.effectiveName}") {
                target.addRoleToMember(member, resolved).submit().await()
            }
        }

    override suspend fun removeRole(guild: GuildId, user: UserId, role: RoleId): SculkResult<Unit> =
        withMemberAndRole(guild, user, role) { target, member, resolved ->
            attemptHierarchy("remove ${resolved.name} from ${member.effectiveName}") {
                target.removeRoleFromMember(member, resolved).submit().await()
            }
        }

    override suspend fun setRoles(guild: GuildId, user: UserId, roles: Set<RoleId>): SculkResult<Unit> =
        withMember(guild, user) { target, member ->
            val resolved = roles.map { id ->
                target.getRoleById(id.raw)
                    ?: return@withMember SculkResult.failure("Role ${id.raw} does not exist in ${target.name}.")
            }
            attemptHierarchy("set ${member.effectiveName}'s roles") {
                target.modifyMemberRoles(member, resolved).submit().await()
            }
        }

    override suspend fun setNickname(guild: GuildId, user: UserId, nickname: String?): SculkResult<Unit> =
        withMember(guild, user) { _, member ->
            attemptHierarchy("set ${member.effectiveName}'s nickname") {
                member.modifyNickname(nickname).submit().await()
            }
        }

    override suspend fun kick(guild: GuildId, user: UserId, reason: String?): SculkResult<Unit> =
        withMember(guild, user) { target, member ->
            attemptHierarchy("kick ${member.effectiveName}") {
                target.kick(member).reason(reason).submit().await()
            }
        }

    override suspend fun ban(guild: GuildId, user: UserId, reason: String?, deleteMessageHours: Int): SculkResult<Unit> {
        // Failing rather than clamping: "delete the last month" quietly becoming seven days is the
        // kind of surprise that only surfaces during an incident, when nobody is checking.
        if (deleteMessageHours !in 0..GuildService.MAX_DELETE_HOURS) {
            return SculkResult.failure(
                "deleteMessageHours must be 0..${GuildService.MAX_DELETE_HOURS} (Discord's seven-day cap), " +
                    "got $deleteMessageHours.",
            )
        }
        return withGuild(guild) { target ->
            attemptHierarchy("ban ${user.raw}") {
                target.ban(UserSnowflake(user.raw), deleteMessageHours, java.util.concurrent.TimeUnit.HOURS)
                    .reason(reason)
                    .submit()
                    .await()
            }
        }
    }

    override suspend fun unban(guild: GuildId, user: UserId): SculkResult<Unit> = withGuild(guild) { target ->
        attemptHierarchy("unban ${user.raw}") { target.unban(UserSnowflake(user.raw)).submit().await() }
    }

    override suspend fun timeout(guild: GuildId, user: UserId, duration: Duration, reason: String?): SculkResult<Unit> {
        val max = JavaDuration.ofDays(GuildService.MAX_TIMEOUT_DAYS.toLong())
        val java = duration.toJavaDuration()
        if (java.isNegative || java.isZero || java > max) {
            return SculkResult.failure(
                "A timeout must be between zero and ${GuildService.MAX_TIMEOUT_DAYS} days, got $duration.",
            )
        }
        return withMember(guild, user) { _, member ->
            attemptHierarchy("time out ${member.effectiveName}") {
                member.timeoutFor(java).reason(reason).submit().await()
            }
        }
    }

    override suspend fun clearTimeout(guild: GuildId, user: UserId): SculkResult<Unit> = withMember(guild, user) { _, member ->
        attemptHierarchy("clear ${member.effectiveName}'s timeout") { member.removeTimeout().submit().await() }
    }

    private inline fun <T> withGuild(guild: GuildId, block: (Guild) -> SculkResult<T>): SculkResult<T> {
        val jda = client() ?: return SculkResult.failure("The gateway is not connected.")
        val target = jda.getGuildById(guild.raw)
            ?: return SculkResult.failure("The bot is not in guild ${guild.raw}, or the id is wrong.")
        return block(target)
    }

    private suspend inline fun <T> withMember(guild: GuildId, user: UserId, block: (Guild, Member) -> SculkResult<T>): SculkResult<T> =
        withGuild(guild) { target ->
            val member = runCatching { target.retrieveMemberById(user.raw).submit().await() }.getOrNull()
                ?: return@withGuild SculkResult.failure("User ${user.raw} is not a member of ${target.name}.")
            block(target, member)
        }

    private suspend inline fun <T> withMemberAndRole(
        guild: GuildId,
        user: UserId,
        role: RoleId,
        block: (Guild, Member, net.dv8tion.jda.api.entities.Role) -> SculkResult<T>,
    ): SculkResult<T> = withMember(guild, user) { target, member ->
        val resolved = target.getRoleById(role.raw)
            ?: return@withMember SculkResult.failure("Role ${role.raw} does not exist in ${target.name}.")
        block(target, member, resolved)
    }
}

private fun Member.toActor(): DiscordActor = DiscordActor(
    id = UserId(id),
    name = effectiveName,
    guild = GuildId(guild.id),
    roles = roles.map { RoleId(it.id) }.toSet(),
    permissionBits = Permission.getRaw(permissions),
)

/** JDA wants a `UserSnowflake` for actions on someone who may not be a member any more. */
private fun UserSnowflake(id: String): net.dv8tion.jda.api.entities.UserSnowflake = net.dv8tion.jda.api.entities.UserSnowflake.fromId(id)

/**
 * Runs a moderation call, naming the role hierarchy when it is refused.
 *
 * A raw JDA `HierarchyException` says only that the action was not permitted, which sends people to
 * check the bot's permissions — where everything looks correct, because the fix is in the role
 * *order*, not the permission set.
 */
private inline fun <T> attemptHierarchy(what: String, block: () -> T): SculkResult<Unit> = runCatching { block() }.fold(
    { SculkResult.ok() },
    { error ->
        val hint = if (error is net.dv8tion.jda.api.exceptions.HierarchyException) {
            " The bot's own role must sit ABOVE the target's in the server's role list — granting the bot " +
                "more permissions will not help."
        } else {
            ""
        }
        SculkResult.failure("Could not $what: ${error.message ?: error::class.simpleName}.$hint", error)
    },
).map { }
