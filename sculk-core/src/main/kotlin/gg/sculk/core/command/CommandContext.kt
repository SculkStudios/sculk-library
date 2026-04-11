package gg.sculk.core.command

import gg.sculk.core.adventure.actionbar
import gg.sculk.core.adventure.playSound
import gg.sculk.core.adventure.reply
import gg.sculk.core.adventure.title
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Execution context available inside every command handler.
 *
 * Provides access to the sender, parsed arguments, and convenience
 * messaging helpers sourced from the Adventure wrapper.
 */
@SculkStable
public class CommandContext
    @SculkInternal
    constructor(
        /** The entity or console that executed the command. */
        public val sender: CommandSender,
        /** The raw argument tokens passed to the command. */
        @SculkInternal public val rawArgs: Array<String>,
        @SculkInternal public val parsedArgs: MutableMap<String, Any?> = mutableMapOf(),
    ) {
        /**
         * The sender as a [Player], or null if this is a console execution.
         * Only non-null inside `player { }` blocks.
         */
        public val player: Player? get() = sender as? Player

        /**
         * Sends a MiniMessage-formatted [message] to the command sender.
         *
         * Works for both players and console.
         */
        public fun reply(message: String): Unit = sender.reply(message)

        /**
         * Sends a title to the player executing this command.
         * No-op if the sender is the console.
         */
        public fun title(
            title: String,
            subtitle: String = "",
            fadeIn: Int = 10,
            stay: Int = 70,
            fadeOut: Int = 20,
        ) {
            player?.title(title, subtitle, fadeIn, stay, fadeOut)
        }

        /**
         * Sends an action bar message to the player executing this command.
         * No-op if the sender is the console.
         */
        public fun actionbar(message: String) {
            player?.actionbar(message)
        }

        /**
         * Plays a sound to the player executing this command.
         * No-op if the sender is the console.
         */
        public fun playSound(
            sound: org.bukkit.Sound,
            volume: Float = 1.0f,
            pitch: Float = 1.0f,
        ) {
            player?.playSound(sound, volume, pitch)
        }

        /**
         * Retrieves a parsed argument by [name].
         *
         * Throws [IllegalArgumentException] if the argument was not registered
         * or could not be parsed.
         *
         * Use `argument<Player>("target")` to get a typed argument value.
         */
        public inline fun <reified T> argument(name: String): T {
            val value =
                parsedArgs[name]
                    ?: error("Argument '$name' not found. Make sure it is registered on the command.")
            return value as? T
                ?: error("Argument '$name' is not of type ${T::class.simpleName}.")
        }

        /**
         * Returns a parsed argument by [name], or null if not present or wrong type.
         */
        public inline fun <reified T> argumentOrNull(name: String): T? = parsedArgs[name] as? T
    }
