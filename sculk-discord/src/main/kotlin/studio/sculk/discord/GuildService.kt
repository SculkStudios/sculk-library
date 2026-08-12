package studio.sculk.discord

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.interaction.DiscordActor
import kotlin.time.Duration

/**
 * Everything a bot does *to* a server rather than in a channel.
 *
 * Grouped rather than flattened onto [DiscordGateway] for the same reason `clientBlocks` and
 * `virtualEntities` are grouped on the packet service: these are the calls that change somebody's
 * standing, and having them named together makes the permissions a bot needs obvious at a glance.
 *
 * Every one of these needs the bot's role to sit **above** the target's in the server's role list.
 * Discord refuses otherwise, and the failure names that, because it is the cause perhaps nine times
 * in ten and nothing in the bot's own config can fix it.
 */
@SculkStable
public interface GuildService {
    /**
     * One member, or a failure saying whether the guild or the member was the missing half.
     *
     * Needs [Intent.GuildMembers] for anyone not currently cached — otherwise a member who has not
     * spoken since the bot started resolves to nothing, which looks like they left the server.
     */
    public suspend fun member(guild: GuildId, user: UserId): SculkResult<DiscordActor>

    /** Whether the bot can see this guild at all. */
    public suspend fun isPresent(guild: GuildId): Boolean

    /**
     * One role's name, colour and position.
     *
     * A [RoleId] on its own says only that a role exists somewhere. Anything that renders a role —
     * a relayed chat line tinted to match Discord, a panel listing someone's ranks — needs what the
     * role actually looks like, and reaching past this interface for it is how a consumer ends up
     * depending on the backend it was the point of this module not to name.
     */
    public suspend fun role(guild: GuildId, role: RoleId): SculkResult<DiscordRole>

    /**
     * Every role in the guild, highest position first.
     *
     * Sorted here so that "the member's top role" is the first match rather than a fold each consumer
     * writes, and returned whole because the per-role alternative is one lookup per role per member —
     * which for a sync over a few thousand members is the difference between one call and thousands.
     */
    public suspend fun roles(guild: GuildId): SculkResult<List<DiscordRole>>

    /**
     * Several members at once.
     *
     * Discord resolves up to [MAX_MEMBER_LOOKUP] ids per request, and this batches [users] to that
     * limit. The single-member [member] in a loop is the same work as one request per member, which
     * for a role sync over a linked-account table is thousands of round trips against a rate limit
     * that is not generous — slow enough that a reconcile can still be running when the next one is
     * due.
     *
     * Members who are not in the guild are absent from the result rather than being an error: over a
     * stored list of linked accounts, somebody having left is expected and not a reason to abandon
     * the pass.
     */
    public suspend fun members(guild: GuildId, users: Set<UserId>): SculkResult<Map<UserId, DiscordActor>>

    public suspend fun addRole(guild: GuildId, user: UserId, role: RoleId): SculkResult<Unit>

    public suspend fun removeRole(guild: GuildId, user: UserId, role: RoleId): SculkResult<Unit>

    /**
     * Replaces a member's roles with exactly [roles].
     *
     * One request rather than a loop of adds and removes: role sync usually computes a whole target
     * set, and applying it piecewise means every intermediate state is broadcast and rate-limited
     * separately. It also makes the operation atomic from an observer's point of view.
     */
    public suspend fun setRoles(guild: GuildId, user: UserId, roles: Set<RoleId>): SculkResult<Unit>

    /** Sets a server nickname, or clears it with null. */
    public suspend fun setNickname(guild: GuildId, user: UserId, nickname: String?): SculkResult<Unit>

    public suspend fun kick(guild: GuildId, user: UserId, reason: String? = null): SculkResult<Unit>

    /**
     * Bans a member.
     *
     * [deleteMessageHours] is capped at Discord's 168 (seven days). Passing more is silently clamped
     * elsewhere; here it fails, because "delete the last month of their messages" quietly becoming
     * seven days is the kind of surprise that surfaces during an incident.
     */
    public suspend fun ban(guild: GuildId, user: UserId, reason: String? = null, deleteMessageHours: Int = 0): SculkResult<Unit>

    public suspend fun unban(guild: GuildId, user: UserId): SculkResult<Unit>

    /**
     * Times a member out, up to Discord's maximum of 28 days.
     *
     * Preferred over a kick for anything temporary: a kick lets them rejoin instantly with a fresh
     * invite, so it reads as a punishment and functions as an inconvenience.
     */
    public suspend fun timeout(guild: GuildId, user: UserId, duration: Duration, reason: String? = null): SculkResult<Unit>

    public suspend fun clearTimeout(guild: GuildId, user: UserId): SculkResult<Unit>

    @SculkStable
    public companion object {
        /** Discord's cap on message deletion when banning. */
        public const val MAX_DELETE_HOURS: Int = 168

        /** Discord's cap on a timeout. */
        public const val MAX_TIMEOUT_DAYS: Int = 28

        /** Discord's cap on how many members one lookup may name. */
        public const val MAX_MEMBER_LOOKUP: Int = 100
    }
}
