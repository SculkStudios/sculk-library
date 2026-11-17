package studio.sculk.text

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import studio.sculk.annotation.SculkExperimental

/**
 * Renders this MiniMessage template with the default theme: `"<primary>Balance: <balance>".mm("balance" to balance)`.
 *
 * Values are placeholders and are never parsed, so `<red>` in a player's name is printed, not obeyed.
 * Pass every value that way: a Kotlin string template such as `"Hi $name".mm()` puts the value into
 * the template and lets it inject tags. Plugins with their own theme render through their [Text].
 */
@SculkExperimental
public fun String.mm(vararg values: Pair<String, Any?>): Component = Text.DEFAULT.render(this, *values)

/**
 * Sends [template] rendered with the default theme and [values] as unparsed placeholders:
 * `player.send("<primary>Welcome back, visit <visits>.", "visits" to visits)`.
 *
 * Named `send` rather than `sendMessage` because Bukkit's `CommandSender.sendMessage(String)` is a
 * member and would always win over an extension, sending the template as legacy text.
 */
@SculkExperimental
public fun Audience.send(template: String, vararg values: Pair<String, Any?>) {
    sendMessage(Text.DEFAULT.render(template, *values))
}
