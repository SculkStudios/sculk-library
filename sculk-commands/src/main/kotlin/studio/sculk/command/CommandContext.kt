package studio.sculk.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.text.SculkMessages

/**
 * What a command handler is given.
 *
 * Arguments are resolved by name rather than by position, so adding one in front of another does
 * not silently shift every later read.
 */
@SculkStable
public class CommandContext
@SculkInternal
constructor(
    public val sender: CommandSender,
    public val messages: SculkMessages,
    @SculkInternal public val arguments: Map<String, Any?>,
) {
    /** The sender as a player, or null from the console. Never null inside a `player { }` block. */
    public val player: Player? get() = sender as? Player

    /** A required argument. Throws if the name was never declared — that is a wiring bug. */
    @Suppress("UNCHECKED_CAST")
    @SculkStable
    public fun <T> argument(name: String): T = (arguments[name] ?: error("Command argument '$name' was not declared on this node.")) as T

    /** An optional argument, or null if it was not supplied. */
    @Suppress("UNCHECKED_CAST")
    @SculkStable
    public fun <T> argumentOrNull(name: String): T? = arguments[name] as T?

    @SculkStable
    public fun has(name: String): Boolean = arguments[name] != null

    /** Renders [template] through the plugin's theme and sends it to the sender. */
    @SculkStable
    public fun reply(template: String, vararg values: Pair<String, String>) {
        messages.send(sender, template, *values)
    }

    @SculkStable
    public fun reply(lines: List<String>, vararg values: Pair<String, String>) {
        messages.send(sender, lines, *values)
    }

    @SculkStable
    public fun actionBar(template: String, vararg values: Pair<String, String>) {
        player?.let { messages.actionBar(it, template, *values) }
    }

    @SculkStable
    public fun title(title: String, subtitle: String = "", vararg values: Pair<String, String>) {
        player?.let { messages.title(it, title, subtitle, values = values) }
    }
}
