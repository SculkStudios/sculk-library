package studio.sculk.dialog

import org.bukkit.entity.Player
import studio.sculk.annotation.SculkStable

/** One field of a dialog's form. [key] names it in the [DialogResponse]; [label] is a template. */
@SculkStable
public sealed class DialogInput(public val key: String, public val label: String) {
    init {
        require(KEY.matches(key)) { "input key '$key' must be letters, digits and '_'" }
    }

    /** Free text of at most [maxLength] characters; [lines] above one makes it a text area. */
    @SculkStable
    public class Text(
        key: String,
        label: String,
        public val initial: String = "",
        public val maxLength: Int = 32,
        public val lines: Int = 1,
    ) : DialogInput(key, label) {
        init {
            require(maxLength in 1..ABSOLUTE_MAX_LENGTH) { "$key: maxLength must be 1 to $ABSOLUTE_MAX_LENGTH" }
            require(initial.length <= maxLength) { "$key: the initial text is longer than maxLength" }
            require(lines in 1..MAX_LINES) { "$key: lines must be 1 to $MAX_LINES" }
        }
    }

    /** A checkbox. */
    @SculkStable
    public class Toggle(key: String, label: String, public val initial: Boolean = false) : DialogInput(key, label)

    /** A slider over [range] in steps of [step]. */
    @SculkStable
    public class Number(
        key: String,
        label: String,
        public val range: ClosedFloatingPointRange<Float>,
        public val step: Float? = null,
        public val initial: Float? = null,
    ) : DialogInput(key, label) {
        init {
            require(range.start < range.endInclusive) { "$key: the range must not be empty" }
            step?.let { require(it > 0f) { "$key: step must be positive" } }
            initial?.let { require(it in range) { "$key: initial must be in the range" } }
        }
    }

    /** One of [options], given as id to label template; [initial] is an id. */
    @SculkStable
    public class Choice(key: String, label: String, options: List<Pair<String, String>>, public val initial: String? = null) :
        DialogInput(key, label) {
        public val options: List<Pair<String, String>> = options.toList()

        init {
            require(options.isNotEmpty()) { "$key: a choice needs at least one option" }
            require(options.map { it.first }.distinct().size == options.size) { "$key: option ids must differ" }
            initial?.let { id -> require(options.any { it.first == id }) { "$key: initial '$id' is not an option" } }
        }
    }

    private companion object {
        val KEY = Regex("[A-Za-z0-9_]+")
        const val ABSOLUTE_MAX_LENGTH = 32_767
        const val MAX_LINES = 512
    }
}

/**
 * A player's answer to a dialog, checked against the spec: a client may send anything, so a value
 * missing, of the wrong kind or outside what the input allows reads as null.
 */
@SculkStable
public class DialogResponse internal constructor(
    public val player: Player,
    private val spec: DialogSpec,
    private val values: Map<String, Any?>,
) {
    public fun text(key: String): String? {
        val input = spec.input<DialogInput.Text>(key)
        return (values[key] as? String)?.takeIf { it.length <= input.maxLength }
    }

    public fun toggle(key: String): Boolean? {
        spec.input<DialogInput.Toggle>(key)
        return values[key] as? Boolean
    }

    public fun number(key: String): Float? {
        val input = spec.input<DialogInput.Number>(key)
        return (values[key] as? Float)?.takeIf { it in input.range }
    }

    /** The chosen option's id. */
    public fun choice(key: String): String? {
        val input = spec.input<DialogInput.Choice>(key)
        return (values[key] as? String)?.takeIf { id -> input.options.any { it.first == id } }
    }

    override fun toString(): String = "DialogResponse(${player.name}, $values)"
}

/** A button: its [label] template, and what pressing it does. */
@SculkStable
public class DialogButton internal constructor(
    public val label: String,
    public val tooltip: String?,
    internal val onPress: ((DialogResponse) -> Unit)?,
)

/**
 * A dialog: a [title], [body] paragraphs, form [inputs] and [buttons], all templates rendered with
 * the theme of the `Dialogs` that shows it. One button makes a notice, two a confirmation where
 * Escape presses the second, more a list of actions.
 *
 * Dialogs exist from Minecraft 1.21.6 (Paper 1.21.7). Elsewhere a dialog shows its [fallback] if it
 * has one, and otherwise `Dialogs.show` reports the capability as missing; there is no silent
 * substitute, because what a form becomes without dialogs is a decision only the plugin can make.
 */
@SculkStable
public class DialogSpec internal constructor(
    public val title: String,
    body: List<String>,
    inputs: List<DialogInput>,
    buttons: List<DialogButton>,
    public val canCloseWithEscape: Boolean,
    internal val fallback: ((Player) -> Unit)?,
) {
    public val body: List<String> = body.toList()
    public val inputs: List<DialogInput> = inputs.toList()
    public val buttons: List<DialogButton> = buttons.toList()

    init {
        require(buttons.isNotEmpty()) { "a dialog needs at least one button" }
        require(inputs.map { it.key }.distinct().size == inputs.size) { "each input key may appear once" }
        require(buttons.map { it.label }.distinct().size == buttons.size) { "each button label may appear once" }
    }

    internal inline fun <reified T : DialogInput> input(key: String): T =
        requireNotNull(inputs.firstOrNull { it.key == key } as? T) { "this dialog has no ${T::class.simpleName} input '$key'" }
}

/** Marks the dialog DSL so a nested block cannot reach an outer builder by accident. */
@SculkStable
@DslMarker
public annotation class DialogDsl

/** Builds a [DialogSpec]; see [dialog]. */
@SculkStable
@DialogDsl
public class DialogBuilder internal constructor(private val title: String) {
    private val body = mutableListOf<String>()
    private val inputs = mutableListOf<DialogInput>()
    private val buttons = mutableListOf<DialogButton>()
    private var fallback: ((Player) -> Unit)? = null
    public var canCloseWithEscape: Boolean = true

    public fun body(vararg paragraphs: String) {
        body += paragraphs
    }

    public fun input(input: DialogInput) {
        inputs += input
    }

    public fun text(key: String, label: String, initial: String = "", maxLength: Int = 32, lines: Int = 1) {
        input(DialogInput.Text(key, label, initial, maxLength, lines))
    }

    public fun toggle(key: String, label: String, initial: Boolean = false) {
        input(DialogInput.Toggle(key, label, initial))
    }

    public fun number(key: String, label: String, range: ClosedFloatingPointRange<Float>, step: Float? = null, initial: Float? = null) {
        input(DialogInput.Number(key, label, range, step, initial))
    }

    public fun choice(key: String, label: String, options: List<Pair<String, String>>, initial: String? = null) {
        input(DialogInput.Choice(key, label, options, initial))
    }

    /** A button; [onPress] runs on the player's thread with the checked answer. Without it the button only closes the dialog. */
    public fun button(label: String, tooltip: String? = null, onPress: ((DialogResponse) -> Unit)? = null) {
        buttons += DialogButton(label, tooltip, onPress)
    }

    /** What to show instead on a server without dialogs, such as a chat prompt or a menu. Runs on the player's thread. */
    public fun fallback(show: (Player) -> Unit) {
        fallback = show
    }

    internal fun build(): DialogSpec = DialogSpec(title, body, inputs, buttons, canCloseWithEscape, fallback)
}

/**
 * Declares a dialog:
 *
 * ```kotlin
 * val rename = dialog("<primary>Rename your home") {
 *     text("name", "New name", maxLength = 24)
 *     toggle("public", "Let others visit")
 *     button("Save") { answer -> homes.rename(answer.player, answer.text("name"), answer.toggle("public") == true) }
 *     button("Cancel")
 * }
 * dialogs.show(player, rename)
 * ```
 */
@SculkStable
public fun dialog(title: String, build: DialogBuilder.() -> Unit): DialogSpec = DialogBuilder(title).apply(build).build()
