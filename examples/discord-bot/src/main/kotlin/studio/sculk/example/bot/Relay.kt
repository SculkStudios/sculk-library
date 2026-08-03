package studio.sculk.example.bot

import studio.sculk.SculkHandle
import studio.sculk.discord.DiscordGateway
import studio.sculk.discord.Mentions

/**
 * Reacts to what people say — the half of a chat bridge that listens.
 *
 * A relay to Minecraft would take exactly this shape, and the two things it must get right are both
 * handled for it:
 *
 * - **The bot never hears itself.** Bot and webhook messages are filtered before a handler sees them.
 *   Without that, a relay that echoes is an infinite loop with a rate limit as its only brake.
 * - **What people type is a value, not a template.** `message.content` is untrusted. Passing it to
 *   MiniMessage on the Minecraft side is the same injection hole `Placeholder.unparsed` closes,
 *   pointed the other way — so it goes in as a placeholder value, never substituted into markup.
 */
fun registerRelay(gateway: DiscordGateway): SculkHandle = gateway.onMessage { message ->
    if (!message.content.startsWith("!echo ")) return@onMessage

    val said = message.content.removePrefix("!echo ")

    // Mentions.None is already the default; naming it here because this is precisely the line where
    // it matters. `said` was typed by somebody who may well have typed "@everyone".
    gateway.sendText(
        message.channel,
        "**${message.author.name}** said: $said",
        mentions = Mentions.None,
    )

    gateway.react(message.channel, message.id, "✅")
}
