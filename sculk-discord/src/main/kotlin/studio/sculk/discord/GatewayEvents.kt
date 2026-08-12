package studio.sculk.discord

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.interaction.DiscordActor

/**
 * A message that is no longer there.
 *
 * Only ids: Discord does not send the content of a deleted message, and a bot that had not already
 * seen it has no way to recover it. A relay that wants to strike through what it posted has to have
 * kept its own record — which is why the id is here and nothing else pretends to be.
 */
@SculkStable
public data class DeletedMessage(public val id: MessageId, public val channel: ChannelId, public val guild: GuildId?)

/**
 * Something changing about a member.
 *
 * One event type rather than three handlers, because the consumer is almost always a single "resync
 * this person" path — and three separate registrations is three places to forget one.
 *
 * Every case needs [Intent.GuildMembers], which is privileged. Without it Discord sends none of these
 * and the only way to notice a role change is to poll for it, which is the thing this exists to stop.
 */
@SculkStable
public sealed interface MemberChange {
    public val guild: GuildId
    public val user: UserId

    /** They joined the server. */
    @SculkStable
    public data class Joined(override val guild: GuildId, override val user: UserId, public val actor: DiscordActor) : MemberChange

    /**
     * They left, were kicked, or were banned.
     *
     * Discord does not say which, so neither does this. A consumer that needs to tell the difference
     * reads the audit log, which is a different permission and a different request.
     */
    @SculkStable
    public data class Left(override val guild: GuildId, override val user: UserId) : MemberChange

    /**
     * Their roles changed.
     *
     * [added] and [removed] are the delta, and [actor] carries the resulting whole set. A sync that
     * only reads the delta will drift, because Discord coalesces changes; one that reads [actor] is
     * correct whatever it missed.
     */
    @SculkStable
    public data class RolesChanged(
        override val guild: GuildId,
        override val user: UserId,
        public val actor: DiscordActor,
        public val added: Set<RoleId> = emptySet(),
        public val removed: Set<RoleId> = emptySet(),
    ) : MemberChange
}
