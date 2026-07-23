package studio.sculk.items

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import studio.sculk.adventure.parseMessage

private val miniMessage: MiniMessage = MiniMessage.miniMessage()

// Parsed through the shared text style so item names and lore pick up the server's
// drop shadow like every other message.
//
// Minecraft italicises custom item names/lore by default. Item text should read upright unless the
// caller explicitly asks for italics, so default ITALIC to false while leaving explicit <i> intact —
// unconditionally here, because that is a property of items rather than a server preference.
internal fun parseItemText(text: String): Component =
    parseMessage(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

internal fun serializeItemText(component: Component): String = miniMessage.serialize(component)
