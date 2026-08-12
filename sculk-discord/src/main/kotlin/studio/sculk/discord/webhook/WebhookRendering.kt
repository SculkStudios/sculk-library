package studio.sculk.discord.webhook

import studio.sculk.discord.Mentions
import studio.sculk.discord.message.Button
import studio.sculk.discord.message.Container
import studio.sculk.discord.message.DiscordMessage
import studio.sculk.discord.message.Divider
import studio.sculk.discord.message.EntitySelect
import studio.sculk.discord.message.MediaGallery
import studio.sculk.discord.message.MessageComponent
import studio.sculk.discord.message.Row
import studio.sculk.discord.message.Section
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
 *
 * **This is why a gateway alert and its webhook fallback are two different messages**, and it is not
 * obvious until something hits it. A failover that reuses the gateway's message body is refused here
 * rather than posted without its buttons — so build the fallback separately, with a link or a record
 * id that tells staff where to go instead. Link buttons are fine: they never produce an interaction.
 */
internal fun undeliverableReason(message: DiscordMessage): String? {
    val flat = message.flatten()
    val interactive = flat.filterIsInstance<Button>().count { it.id != null } +
        flat.filterIsInstance<SelectMenu>().size +
        flat.filterIsInstance<EntitySelect>().size
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
 * This path does not carry Components V2. Sending V2 through a webhook is possible — it needs the
 * `IS_COMPONENTS_V2` flag and Discord's raw component schema — but this module speaks HTTP directly
 * rather than through a backend library, so supporting it means hand-writing and maintaining that
 * whole schema. Until something needs it, a [Container] becomes an embed, which is what an embed is:
 * the thing V2 containers replaced. The accent becomes the embed colour.
 *
 * Nothing is dropped in silence. A [Row] of link buttons becomes a line of markdown links rather than
 * vanishing, because a fallback alert whose "Open incident" button quietly disappeared is worse than
 * one that reads slightly differently from its gateway twin. Interactive components never reach here
 * at all — [undeliverableReason] refuses the message first.
 */
internal fun payloadFor(message: DiscordMessage, username: String?, avatarUrl: String?): WebhookPayload {
    val loose = message.components.filterNot { it is Container }.flatten().joinToString("\n")
    val embeds = message.components.filterIsInstance<Container>().map { container ->
        WebhookEmbed(
            description = container.children.flatten().joinToString("\n").take(MAX_DESCRIPTION),
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

/** Flattens components to the lines an embed description or a content field can hold. */
private fun List<MessageComponent>.flatten(): List<String> = mapNotNull { child ->
    when (child) {
        is Text -> child.markdown

        is Divider -> DIVIDER_RULE

        is Row -> child.components.filterIsInstance<Button>().mapNotNull { it.asMarkdownLink() }
            .joinToString(" · ")
            .ifBlank { null }

        // The accessory image is lost — an embed has one image slot and a message may hold many
        // sections — but the text is not, and a link-button accessory survives as a link.
        is Section -> (child.content.map { it.markdown } + listOfNotNull((child.accessory as? Button)?.asMarkdownLink()))
            .joinToString("\n")

        is MediaGallery -> child.items.joinToString("\n") { it.url }

        else -> null
    }
}

/** A link button as `[label](url)`, or null for anything that is not a link. */
private fun Button.asMarkdownLink(): String? = link?.let { "[$label]($it)" }

private const val DIVIDER_RULE = "───"
