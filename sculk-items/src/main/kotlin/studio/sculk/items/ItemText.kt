package studio.sculk.items

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

private val miniMessage: MiniMessage = MiniMessage.miniMessage()

internal fun parseItemText(text: String): Component = miniMessage.deserialize(text)

internal fun serializeItemText(component: Component): String = miniMessage.serialize(component)
