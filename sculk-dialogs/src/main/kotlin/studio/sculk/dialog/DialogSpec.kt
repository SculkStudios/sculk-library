package studio.sculk.dialog

import org.bukkit.entity.Player
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.annotation.SculkStable
import studio.sculk.text.Amounts
import java.math.BigDecimal

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

    /**
     * An amount typed plainly or compactly, `250`, `1.5k` or `2m`, read exactly by [amounts] and kept
     * from [min] to [max] where given. It shows as a text field holding [initial] in compact form.
     */
    @SculkExperimental
    public class Amount(
        key: String,
        label: String,
        public val min: BigDecimal? = null,
        public val max: BigDecimal? = null,
        public val initial: BigDecimal? = null,
        public val amounts: Amounts = Amounts.DEFAULT,
    ) : DialogInput(key, label) {
        init {
            require(min == null || max == null || min <= max) { "$key: min $min is above max $max" }
            initial?.let { require(it in this) { "$key: initial must be from min to max" } }
        }

        internal operator fun contains(value: BigDecimal): Boolean = (min == null || value >= min) && (max == null || value <= max)

        /** The most characters the field takes: room for any amount, compact or written out. */
        public val maxLength: Int get() = MAX_AMOUNT_LENGTH
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
        const val MAX_AMOUNT_LENGTH = 64
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
    /**
     * What the player typed in the text input [key], plain text and never markup; null when it is
     * missing or longer than the input allows. Throws when the dialog has no text input [key].
     */
    public fun text(key: String): String? {
        val input = spec.input<DialogInput.Text>(key)
        return (values[key] as? String)?.takeIf { it.length <= input.maxLength }
    }

    /** Whether the checkbox [key] is ticked; null when missing. Throws when the dialog has no toggle [key]. */
    public fun toggle(key: String): Boolean? {
        spec.input<DialogInput.Toggle>(key)
        return values[key] as? Boolean
    }

    /** The slider [key]'s value; null when missing or outside its range. Throws when the dialog has no number input [key]. */
    public fun number(key: String): Float? {
        val input = spec.input<DialogInput.Number>(key)
        return (values[key] as? Float)?.takeIf { it in input.range }
    }

    /**
     * The amount typed in [key], read exactly: `1.5k` is 1500. Null when missing, not an amount or
     * outside the input's range. Throws when the dialog has no amount input [key].
     */
    @SculkExperimental
    public fun amount(key: String): BigDecimal? {
        val input = spec.input<DialogInput.Amount>(key)
        val typed = values[key] as? String ?: return null
        return (input.amounts.parse(typed) as? SculkResult.Success)?.value?.takeIf { it in input }
    }

    /** The id of the option chosen in [key]; null when missing or not an option. Throws when the dialog has no choice [key]. */
    public fun choice(key: String): String? {
        val input = spec.input<DialogInput.Choice>(key)
        return (values[key] as? String)?.takeIf { id -> input.options.any { it.first == id } }
    }

    /** What was typed in [input], as [text] reads it; the input must belong to this dialog. */
    @SculkExperimental
    public operator fun get(input: DialogInput.Text): String? = text(own(input).key)

    /** Whether [input] is ticked, as [toggle] reads it; the input must belong to this dialog. */
    @SculkExperimental
    public operator fun get(input: DialogInput.Toggle): Boolean? = toggle(own(input).key)

    /** [input]'s value, as [number] reads it; the input must belong to this dialog. */
    @SculkExperimental
    public operator fun get(input: DialogInput.Number): Float? = number(own(input).key)

    /** The amount typed in [input], as [amount] reads it; the input must belong to this dialog. */
    @SculkExperimental
    public operator fun get(input: DialogInput.Amount): BigDecimal? = amount(own(input).key)

    /** The option chosen in [input], as [choice] reads it; the input must belong to this dialog. */
    @SculkExperimental
    public operator fun get(input: DialogInput.Choice): String? = choice(own(input).key)

    // An input from another dialog may share a key with one of this dialog's; reading it would be a lie.
    private fun <I : DialogInput> own(input: I): I {
        require(spec.inputs.any { it === input }) { "'${input.key}' is an input of another dialog" }
        return input
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

    /** Whether the player can close the dialog with Escape. */
    public var canCloseWithEscape: Boolean = true

    /** Appends paragraphs of text above the inputs, each a template. */
    public fun body(vararg paragraphs: String) {
        body += paragraphs
    }

    /** Adds [input] as built and returns it, to read the answer with; each input's key may appear once. */
    public fun <I : DialogInput> input(input: I): I {
        inputs += input
        return input
    }

    /** Adds a text input; see [DialogInput.Text]. Read it with [DialogResponse.text]. */
    public fun text(key: String, label: String, initial: String = "", maxLength: Int = 32, lines: Int = 1): DialogInput.Text =
        input(DialogInput.Text(key, label, initial, maxLength, lines))

    /** Adds a checkbox; see [DialogInput.Toggle]. Read it with [DialogResponse.toggle]. */
    public fun toggle(key: String, label: String, initial: Boolean = false): DialogInput.Toggle =
        input(DialogInput.Toggle(key, label, initial))

    /** Adds a slider; see [DialogInput.Number]. Read it with [DialogResponse.number]. */
    public fun number(
        key: String,
        label: String,
        range: ClosedFloatingPointRange<Float>,
        step: Float? = null,
        initial: Float? = null,
    ): DialogInput.Number = input(DialogInput.Number(key, label, range, step, initial))

    /** Adds a field for an amount such as `1.5k`; see [DialogInput.Amount]. Read it with [DialogResponse.amount]. */
    @SculkExperimental
    public fun amount(
        key: String,
        label: String,
        min: BigDecimal? = null,
        max: BigDecimal? = null,
        initial: BigDecimal? = null,
        amounts: Amounts = Amounts.DEFAULT,
    ): DialogInput.Amount = input(DialogInput.Amount(key, label, min, max, initial, amounts))

    /** Adds a choice of options; see [DialogInput.Choice]. Read it with [DialogResponse.choice]. */
    public fun choice(key: String, label: String, options: List<Pair<String, String>>, initial: String? = null): DialogInput.Choice =
        input(DialogInput.Choice(key, label, options, initial))

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
