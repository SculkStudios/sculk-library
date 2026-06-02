package studio.sculk.items

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage

private val miniMessage: MiniMessage = MiniMessage.miniMessage()

// Minecraft italicises custom item names/lore by default. Item text should read upright unless the
// caller explicitly asks for italics, so default ITALIC to false while leaving explicit <i> intact.
internal fun parseItemText(text: String): Component =
    miniMessage.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

internal fun serializeItemText(component: Component): String = miniMessage.serialize(component)
