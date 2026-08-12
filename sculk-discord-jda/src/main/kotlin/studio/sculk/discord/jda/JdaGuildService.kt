package studio.sculk.discord.jda

import kotlinx.coroutines.suspendCancellableCoroutine
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.utils.concurrent.Task
import studio.sculk.SculkResult
import studio.sculk.coroutine.await
import studio.sculk.discord.DiscordRole
import studio.sculk.discord.GuildId
import studio.sculk.discord.GuildService
import studio.sculk.discord.RoleId
import studio.sculk.discord.UserId
import studio.sculk.discord.interaction.DiscordActor
import studio.sculk.map
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    override suspend fun role(guild: GuildId, role: RoleId): SculkResult<DiscordRole> = withGuild(guild) { target ->
        val resolved = target.getRoleById(role.raw)
            ?: return@withGuild SculkResult.failure("Role ${role.raw} does not exist in ${target.name}.")
        SculkResult.success(resolved.toRole())
    }

    override suspend fun roles(guild: GuildId): SculkResult<List<DiscordRole>> = withGuild(guild) { target ->
        SculkResult.success(target.roles.map { it.toRole() }.sortedByDescending { it.position })
    }

    override suspend fun members(guild: GuildId, users: Set<UserId>): SculkResult<Map<UserId, DiscordActor>> {
        if (users.isEmpty()) return SculkResult.success(emptyMap())
        return withGuild(guild) { target ->
            val found = mutableMapOf<UserId, DiscordActor>()
            for (batch in users.chunked(GuildService.MAX_MEMBER_LOOKUP)) {
                val members = runCatching { target.retrieveMembersByIds(*batch.map { it.raw }.toTypedArray()).await() }
                    .getOrElse { error ->
                        // JDA refuses this outright without the intent, rather than returning nothing,
                        // and its own message does not say which intent or where to enable it.
                        return@withGuild SculkResult.failure(
                            "Could not look up ${batch.size} member(s) of ${target.name}: " +
                                "${error.message ?: error::class.simpleName}. Bulk member lookup needs the " +
                                "GuildMembers intent, enabled on the application at discord.com/developers " +
                                "and requested in the bot config.",
                            error,
                        )
                    }
                members.forEach { found[UserId(it.id)] = it.toActor() }
            }
            SculkResult.success(found)
        }
    }

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

/**
 * Reads a role's colour from [Role.getColors] rather than the deprecated `getColor`.
 *
 * Discord grew gradient and holographic roles, so a role no longer has *a* colour. JDA kept the old
 * single-colour accessor working by returning the primary, and deprecated it. Taking the primary is
 * still the right answer for anything tinting text — a gradient cannot survive the trip to a Minecraft
 * chat line — but it is a deliberate flattening rather than an accident of the old API.
 *
 * `isDefault` is the uncoloured case, and it is not the same as black: Discord's sentinel for "sets no
 * colour" would otherwise arrive as `0x000000` and be rendered as an actual black name.
 */
private fun Role.toRole(): DiscordRole = DiscordRole(
    id = RoleId(id),
    name = name,
    colorRgb = colors.takeIf { !it.isDefault }?.primaryRaw?.and(RGB_MASK),
    position = position,
    hoisted = isHoisted,
    mentionable = isMentionable,
)

/**
 * Suspends on a JDA [Task], which is its member-chunking type and is not a `CompletableFuture`.
 *
 * `Task.get()` blocks, and blocking here would tie up a dispatcher thread for the length of a member
 * chunk request — the exact cost bulk lookup exists to avoid. Cancelling the coroutine cancels the
 * request, so an abandoned sync stops asking Discord for members nobody is waiting on.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    onSuccess { continuation.resume(it) }
    onError { continuation.resumeWithException(it) }
    continuation.invokeOnCancellation { runCatching { cancel() } }
}

private const val RGB_MASK = 0xFFFFFF

/**
 * Flattens a member into the neutral actor, keeping the display name and the handle apart.
 *
 * `effectiveName` is nickname, then global name, then username — right for display and useless for
 * anything that has to still match tomorrow. Carrying `user.name` alongside it is what lets a consumer
 * record who linked an account without the record changing every time they rename themselves.
 */
internal fun Member.toActor(): DiscordActor = DiscordActor(
    id = UserId(id),
    name = effectiveName,
    guild = GuildId(guild.id),
    roles = roles.map { RoleId(it.id) }.toSet(),
    permissionBits = Permission.getRaw(permissions),
    username = user.name,
    nickname = nickname,
    avatarUrl = effectiveAvatarUrl,
)

/**
 * The same, for someone seen outside a guild or not cached as a member.
 *
 * No roles and no permission bits, because there is no guild to hold them in — not because they were
 * dropped. A caller deciding a permission from this gets an empty set and refuses, which is the safe
 * direction for the DM case.
 */
internal fun User.toActor(guild: GuildId?): DiscordActor = DiscordActor(
    id = UserId(id),
    name = effectiveName,
    guild = guild,
    username = name,
    avatarUrl = effectiveAvatarUrl,
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
