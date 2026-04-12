package gg.sculk.core.adventure

import gg.sculk.core.annotation.SculkStable
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import java.time.Duration
import net.kyori.adventure.sound.Sound as AdventureSound

private val miniMessage = MiniMessage.miniMessage()

/**
 * Parses a MiniMessage [text] string into an Adventure Component.
 *
 * This is the only supported text format in Sculk Studio. Legacy color codes
 * are not supported and will be treated as plain text.
 */
@SculkStable
public fun parseMessage(text: String): net.kyori.adventure.text.Component = miniMessage.deserialize(text)

/**
 * Sends a MiniMessage-formatted [message] to this [Audience].
 *
 * Example:
 * ```kotlin
 * player.reply("<green>Hello, <bold>${player.name}</bold>!")
 * ```
 */
@SculkStable
public fun Audience.reply(message: String): Unit = sendMessage(parseMessage(message))

/**
 * Sends a title and optional subtitle to this [Audience].
 *
 * All strings are parsed as MiniMessage.
 *
 * @param title The main title text.
 * @param subtitle The subtitle text. Defaults to empty.
 * @param fadeIn Fade-in duration in ticks. Defaults to 10.
 * @param stay Stay duration in ticks. Defaults to 70.
 * @param fadeOut Fade-out duration in ticks. Defaults to 20.
 */
@SculkStable
public fun Audience.title(
    title: String,
    subtitle: String = "",
    fadeIn: Int = 10,
    stay: Int = 70,
    fadeOut: Int = 20,
) {
    val times =
        Title.Times.times(
            Duration.ofMillis(fadeIn * 50L),
            Duration.ofMillis(stay * 50L),
            Duration.ofMillis(fadeOut * 50L),
        )
    showTitle(
        Title.title(
            parseMessage(title),
            parseMessage(subtitle),
            times,
        ),
    )
}

/**
 * Sends an action bar [message] to this [Audience].
 *
 * The message is parsed as MiniMessage.
 */
@SculkStable
public fun Audience.actionbar(message: String): Unit = sendActionBar(parseMessage(message))

/**
 * Plays a [sound] to this [Audience] at their location.
 *
 * @param sound The Bukkit [org.bukkit.Sound] to play.
 * @param volume The volume. Defaults to 1.0.
 * @param pitch The pitch. Defaults to 1.0.
 */
@SculkStable
public fun Audience.playSound(
    sound: org.bukkit.Sound,
    volume: Float = 1.0f,
    pitch: Float = 1.0f,
) {
    playSound(
        AdventureSound.sound(
            sound.key(),
            AdventureSound.Source.MASTER,
            volume,
            pitch,
        ),
    )
}
