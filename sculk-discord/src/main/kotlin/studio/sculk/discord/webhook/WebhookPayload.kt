package studio.sculk.discord.webhook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.sculk.annotation.SculkInternal

/**
 * Discord's webhook execute body.
 *
 * Serialized rather than concatenated. Both places this replaced built the JSON by hand with a
 * bespoke escaper, and the two escapers did not agree on which control characters to escape — one
 * covered `0x00..0x1F` and the other everything below `' '`, so the same player name could post
 * cleanly through one path and produce a 400 through the other.
 */
@SculkInternal
@Serializable
internal data class WebhookPayload(
    val content: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("allowed_mentions") val allowedMentions: AllowedMentions = AllowedMentions(),
    val embeds: List<WebhookEmbed> = emptyList(),
)

/**
 * The allow-list.
 *
 * `parse` being an empty list is what makes an `@everyone` in the body inert. Omitting the field
 * entirely means "resolve whatever is in the text", so this is never optional.
 */
@SculkInternal
@Serializable
internal data class AllowedMentions(
    val parse: List<String> = emptyList(),
    val users: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
)

@SculkInternal
@Serializable
internal data class WebhookEmbed(
    val title: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val timestamp: String? = null,
    val footer: EmbedFooter? = null,
    val fields: List<EmbedField> = emptyList(),
)

@SculkInternal
@Serializable
internal data class EmbedFooter(val text: String)

@SculkInternal
@Serializable
internal data class EmbedField(val name: String, val value: String, val inline: Boolean = false)
