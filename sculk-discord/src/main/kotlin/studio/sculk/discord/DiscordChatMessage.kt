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
 * closes with `Placeholder.unparsed`, only pointed the other way. [displayContent] is untrusted for
 * exactly the same reason — resolving a mention does not sanitise anything.
 */
@SculkStable
public data class DiscordChatMessage(
    public val id: MessageId,
    public val channel: ChannelId,
    public val guild: GuildId?,
    public val author: DiscordActor,
    /** The raw markdown, exactly as typed, with mentions still as `<@id>`. */
    public val content: String,
    /**
     * The same text with mentions, channels and custom emoji resolved to readable names.
     *
     * What a relay should forward. [content] carries `<@493...>` where the sender typed a mention, and
     * a bridge that relays it verbatim shows players a raw snowflake — technically the message, and
     * unreadable. Kept alongside rather than replacing [content] because anything *parsing* the
     * message, as opposed to displaying it, still needs the ids.
     *
     * Defaults to [content] so a hand-built message in a test does not have to state both.
     */
    public val displayContent: String = content,
    /** Files attached, for a relay that wants to say "sent 2 images" rather than nothing. */
    public val attachments: List<DiscordAttachment> = emptyList(),
    /** What this message was a reply to, or null. */
    public val reply: ReplyContext? = null,
    /**
     * Whether a bot or webhook wrote this.
     *
     * **Always false on anything a gateway delivers** — the filter runs before a handler is called, so
     * a real one never sees a bot message. It exists so a test can hand `FakeDiscordGateway.deliver` a
     * bot message and assert the filter holds, which is the difference between a relay that has been
     * shown not to echo itself and one that merely has not yet.
     */
    public val fromBot: Boolean = false,
)

/**
 * A file attached to a message.
 *
 * A file name alone answers "was something attached", which is enough to relay a `[image.png]` marker
 * and not enough for anything else — a bridge that wants to show the image, link to it, or refuse one
 * larger than some limit needs the rest, and the alternative is reaching past the gateway for a native
 * attachment and importing the backend along with it.
 */
@SculkStable
public data class DiscordAttachment(
    public val fileName: String,
    /** Discord's CDN link. Time-limited on newer uploads, so it is for relaying, not for storing. */
    public val url: String,
    public val sizeBytes: Long = 0,
    /** The MIME type Discord reported, or null when it reported none. */
    public val contentType: String? = null,
) {
    public val isImage: Boolean get() = contentType?.startsWith("image/") == true
}

/**
 * The message a reply was aimed at.
 *
 * Discord shows the quoted line above the reply, and a relay that drops it turns a conversation into
 * a sequence of non-sequiturs — "no it isn't" arriving in Minecraft with nothing it could be
 * answering. [excerpt] is already shortened for display; the full text is not carried, because a
 * bridge that wanted it would be re-relaying a message it has usually already relayed once.
 *
 * [author] is null when Discord did not resolve the referenced message — it was deleted, or is old
 * enough to have fallen out of the cache.
 */
@SculkStable
public data class ReplyContext(public val messageId: MessageId, public val author: DiscordActor?, public val excerpt: String) {
    @SculkStable
    public companion object {
        /** How much of the replied-to message is carried. */
        public const val MAX_EXCERPT: Int = 120
    }
}
