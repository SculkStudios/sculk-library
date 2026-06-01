package studio.sculk.adventure

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import studio.sculk.annotation.SculkStable
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.sound.Sound as AdventureSound

private val miniMessage = MiniMessage.miniMessage()
private val parsedTemplateCache: ConcurrentHashMap<String, net.kyori.adventure.text.Component> = ConcurrentHashMap()

/**
 * Parses a MiniMessage [text] string into an Adventure Component.
 *
 * This is the only supported text format in Sculk Studio. Legacy color codes
 * are not supported and will be treated as plain text.
 */
@SculkStable
public fun parseMessage(text: String): net.kyori.adventure.text.Component = miniMessage.deserialize(text)

/** Parses and caches a static MiniMessage string. */
@SculkStable
public fun cachedMessage(text: String): net.kyori.adventure.text.Component =
    parsedTemplateCache.computeIfAbsent(text, miniMessage::deserialize)

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
public fun Audience.title(title: String, subtitle: String = "", fadeIn: Int = 10, stay: Int = 70, fadeOut: Int = 20) {
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
public fun Audience.playSound(sound: org.bukkit.Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
    playSound(
        AdventureSound.sound(
            sound,
            AdventureSound.Source.MASTER,
            volume,
            pitch,
        ),
    )
}

/**
 * Broadcasts a MiniMessage-formatted [message] to every player on the server
 * and to the console.
 *
 * Fully Component-based — no legacy color codes, no raw strings reaching the wire.
 *
 * ```kotlin
 * broadcast("<#A3E4A1>✔ <#FFD6A5>│ <#E5E5E5>Server is restarting in 60 seconds!")
 * ```
 */
@SculkStable
public fun broadcast(message: String) {
    Bukkit.getServer().broadcast(parseMessage(message))
}

/**
 * Sends an action bar [message] to every online player.
 *
 * Parsed as MiniMessage.
 */
@SculkStable
public fun broadcastActionbar(message: String) {
    val component = parseMessage(message)
    Bukkit.getOnlinePlayers().forEach { it.sendActionBar(component) }
}

/** A reusable MiniMessage template with named string placeholders. */
@SculkStable
public class MessageTemplate internal constructor(private val source: String, private val defaults: Map<String, String>) {
    /** Renders this template with [values] layered over default placeholders. */
    public fun render(values: Map<String, String> = emptyMap()): net.kyori.adventure.text.Component {
        val merged = defaults + values
        val rendered =
            merged.entries.fold(source) { text, (key, value) ->
                text.replace("<$key>", value)
            }
        return parseMessage(rendered)
    }

    /** Sends this rendered template to [audience]. */
    public fun sendTo(audience: Audience, values: Map<String, String> = emptyMap()) {
        audience.sendMessage(render(values))
    }
}

/** Builder for [MessageTemplate]. */
@SculkStable
public class MessageTemplateBuilder internal constructor(private val source: String) {
    private val placeholders: MutableMap<String, String> = linkedMapOf()

    /** Adds a default placeholder value. */
    public fun placeholder(key: String, value: String) {
        placeholders[key] = value
    }

    internal fun build(): MessageTemplate = MessageTemplate(source, placeholders.toMap())
}

/** Runtime placeholder builder used when sending templates. */
@SculkStable
public class MessagePlaceholders internal constructor() {
    internal val values: MutableMap<String, String> = linkedMapOf()

    /** Adds a runtime placeholder value. */
    public fun placeholder(key: String, value: String) {
        values[key] = value
    }
}

/** Creates a reusable MiniMessage template. */
@SculkStable
public fun messageTemplate(source: String, block: MessageTemplateBuilder.() -> Unit = {}): MessageTemplate =
    MessageTemplateBuilder(source).apply(block).build()

/** Sends a [template] to this [Audience] with runtime placeholders. */
@SculkStable
public fun Audience.sendTemplate(template: MessageTemplate, block: MessagePlaceholders.() -> Unit = {}) {
    val placeholders = MessagePlaceholders().apply(block)
    sendMessage(template.render(placeholders.values))
}
