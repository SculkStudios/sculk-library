package studio.sculk.discord

import studio.sculk.annotation.SculkStable

/**
 * Who a message is allowed to ping.
 *
 * **[None] is the default on every outbound message, and pinging is opt-in per message.** This is the
 * Discord half of the trust boundary `Placeholder.unparsed` draws for MiniMessage: a value that came
 * from a player is text, and text must not become a ping just because of the characters in it.
 *
 * The alternative — rewriting the message body to defuse it — was tried and does not hold. Replacing
 * `@everyone` with a zero-width-space variant misses `<@&roleId>` entirely, and it corrupts the very
 * string a moderator is reading as evidence of what was said. Discord already offers the correct
 * control: an allow-list sent alongside the message, which decides what resolves without touching
 * what is displayed.
 *
 * ```kotlin
 * channel.send(alert)                                  // pings nothing, including @everyone
 * channel.send(ticket, mentions = Mentions.user(staffId))    // pings exactly that one person
 * ```
 */
@SculkStable
public sealed interface Mentions {
    /** Nothing resolves. Every mention in the body renders as inert text. */
    @SculkStable
    public data object None : Mentions

    /** Only the ids named here resolve. Anything else in the body stays inert. */
    @SculkStable
    public data class Allow(
        public val users: Set<UserId> = emptySet(),
        public val roles: Set<RoleId> = emptySet(),
        /** `@everyone` and `@here`. Separate because it is the one nobody means to enable. */
        public val everyone: Boolean = false,
    ) : Mentions

    /**
     * Whatever the body says resolves, `@everyone` included.
     *
     * Only correct when the entire message is authored by the server operator. A single interpolated
     * player name makes this a way to ping a staff Discord on demand.
     */
    @SculkStable
    public data object All : Mentions

    @SculkStable
    public companion object {
        public fun user(id: UserId): Allow = Allow(users = setOf(id))

        public fun users(ids: Collection<UserId>): Allow = Allow(users = ids.toSet())

        public fun role(id: RoleId): Allow = Allow(roles = setOf(id))

        public fun roles(ids: Collection<RoleId>): Allow = Allow(roles = ids.toSet())
    }
}
