package studio.sculk.discord

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.interaction.DiscordActor

/**
 * A message somebody posted.
 *
 * The other direction of a chat bridge, and the reason [DiscordGateway.onMessage] exists at all —
 * everything else in the API is the bot talking, and a relay has to listen.
 *
 * [content] is **untrusted**. It was typed by a person, and it reaches Minecraft as a value, never
 * as a template: putting it through MiniMessage is the same markup-injection hole the Minecraft side
 * closes with `Placeholder.unparsed`, only pointed the other way.
 */
@SculkStable
public data class DiscordChatMessage(
    public val id: MessageId,
    public val channel: ChannelId,
    public val guild: GuildId?,
    public val author: DiscordActor,
    /** The raw markdown, exactly as typed. */
    public val content: String,
    /** File names attached, for a relay that wants to say "sent 2 images" rather than nothing. */
    public val attachments: List<String> = emptyList(),
    /** True when the author is a bot or webhook. Filtered before a handler sees it. */
    public val fromBot: Boolean = false,
)
