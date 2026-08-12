package studio.sculk.discord

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.interaction.DiscordActor

/**
 * A role, with the parts of it something outside Discord can act on.
 *
 * [DiscordActor.roles] carries ids and nothing else, which is enough to decide *whether* someone holds
 * a role and useless for saying anything *about* it. A chat bridge that tints a relayed line to match
 * the sender's Discord colour, a panel that lists a member's ranks in the order the server shows them,
 * and a sync that refuses to touch anything above its own position all need the same three fields, so
 * they are read once here rather than by each consumer reaching past the boundary for a native role.
 *
 * [colorRgb] is null for an uncoloured role rather than zero. Discord's own "no colour" is a sentinel
 * that renders as the default grey, and collapsing it to black — which is what `0` would mean to
 * anything reading this as a colour — turns "this role sets no colour" into "this role sets the colour
 * black", which is a visible and wrong result rather than an ignorable one.
 */
@SculkStable
public data class DiscordRole(
    public val id: RoleId,
    public val name: String,
    /** `0xRRGGBB`, or null when the role sets no colour. */
    public val colorRgb: Int? = null,
    /**
     * Where the role sits in the server's list. Higher outranks lower.
     *
     * The number is only meaningful against other roles in the same guild — Discord makes no promise
     * that positions are contiguous, and they shift whenever anyone reorders the list.
     */
    public val position: Int = 0,
    /** Whether the role is shown separately in the member list. */
    public val hoisted: Boolean = false,
    public val mentionable: Boolean = false,
)

/**
 * The role Discord would take this member's name colour from, or null when none of their roles set one.
 *
 * Highest *coloured* role, not highest role: a member whose top role is an uncoloured organisational
 * one still shows the colour of whatever sits below it. Ranking by anything else — first match, lowest
 * position, the order the ids happen to iterate in — produces a colour that disagrees with the one
 * Discord is displaying next to the same name, and the mismatch reads as a bug in the bridge.
 *
 * [guildRoles] is the guild's roles, from [GuildService.roles]. Roles the member does not hold are
 * ignored, so the full list can be fetched once and reused across every member.
 *
 * ```kotlin
 * val roles = gateway.guilds.roles(guild).getOrNull().orEmpty()
 * val accent = message.author.highestColoredRole(roles)?.colorRgb
 * ```
 */
@SculkStable
public fun DiscordActor.highestColoredRole(guildRoles: List<DiscordRole>): DiscordRole? = guildRoles
    .filter { it.id in roles && it.colorRgb != null }
    .maxByOrNull { it.position }
