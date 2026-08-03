package studio.sculk.discord.webhook

import studio.sculk.discord.Mentions
import studio.sculk.discord.message.Button
import studio.sculk.discord.message.Container
import studio.sculk.discord.message.DiscordMessage
import studio.sculk.discord.message.Divider
import studio.sculk.discord.message.SelectMenu
import studio.sculk.discord.message.Text

// Pure functions, hoisted out of DiscordWebhook so the parts most likely to be wrong — the allow-list
// and the message-to-embed mapping — are testable without an HTTP client or a network.

internal const val MAX_CONTENT = 2000
internal const val MAX_USERNAME = 80
internal const val MAX_DESCRIPTION = 4096

/**
 * Why this message cannot go through a webhook, or null if it can.
 *
 * A webhook has no application behind it, so a component that reports clicks has nothing to report
 * them to. Dropping the buttons silently would post an alert that looks actionable and is not.
 */
internal fun undeliverableReason(message: DiscordMessage): String? {
    val flat = message.flatten()
    val interactive = flat.filterIsInstance<Button>().count { it.id != null } +
        flat.filterIsInstance<SelectMenu>().size
    return if (interactive == 0) {
        null
    } else {
        "This message has $interactive interactive component(s), and a webhook has no application behind " +
            "it to receive their clicks. Send it through a gateway, or use link buttons."
    }
}

/**
 * The allow-list for a mention policy.
 *
 * `parse` is always present, empty by default. Omitting the field entirely means "resolve whatever
 * the text says", so there is no such thing as leaving it out.
 */
internal fun allowedMentionsFor(mentions: Mentions): AllowedMentions = when (mentions) {
    Mentions.None -> AllowedMentions()

    Mentions.All -> AllowedMentions(parse = listOf("users", "roles", "everyone"))

    is Mentions.Allow -> AllowedMentions(
        // `parse` and an explicit id list are mutually exclusive per kind in Discord's API, so
        // everyone is the only thing that can be parsed alongside named users and roles.
        parse = if (mentions.everyone) listOf("everyone") else emptyList(),
        users = mentions.users.map { it.raw },
        roles = mentions.roles.map { it.raw },
    )
}

/**
 * Renders a message into a webhook body.
 *
 * A webhook cannot carry Components V2, so a [Container] becomes an embed — which is what it is the
 * modern replacement for — and its accent becomes the embed colour. Loose [Text] outside any
 * container becomes the content line.
 */
internal fun payloadFor(message: DiscordMessage, username: String?, avatarUrl: String?): WebhookPayload {
    val loose = message.components.filterIsInstance<Text>().joinToString("\n") { it.markdown }
    val embeds = message.components.filterIsInstance<Container>().map { container ->
        WebhookEmbed(
            description = container.children
                .mapNotNull { child ->
                    when (child) {
                        is Text -> child.markdown
                        is Divider -> "───"
                        else -> null
                    }
                }.joinToString("\n")
                .take(MAX_DESCRIPTION),
            color = container.accentRgb,
        )
    }
    return WebhookPayload(
        content = loose.ifBlank { null }?.take(MAX_CONTENT),
        username = username?.take(MAX_USERNAME),
        avatarUrl = avatarUrl,
        allowedMentions = allowedMentionsFor(message.mentions),
        embeds = embeds,
    )
}
