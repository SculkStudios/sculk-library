package studio.sculk.discord.interaction

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.ComponentId

/** How much room a modal field gives. */
@SculkStable
public enum class TextFieldStyle { Short, Paragraph }

@SculkStable
public data class TextField(
    public val name: String,
    public val label: String,
    public val style: TextFieldStyle = TextFieldStyle.Short,
    public val value: String? = null,
    public val placeholder: String? = null,
    public val required: Boolean = true,
    public val maxLength: Int? = null,
) {
    init {
        require(name.isNotBlank()) { "A modal field needs a name to read its value back by." }
        require(label.isNotBlank()) { "A modal field needs a label." }
        require(maxLength == null || maxLength in 1..MAX_LENGTH) {
            "A modal field holds at most $MAX_LENGTH characters."
        }
    }

    private companion object {
        const val MAX_LENGTH = 4000
    }
}

/**
 * A form.
 *
 * The [id] is not decoration: Discord hands a submitted modal back with no reference to whatever
 * produced it, so the id is the only place to record which thing this form is about. Reuse the id
 * the button carried and the submit path is the click path with a value attached.
 */
@SculkStable
public data class Modal(public val id: ComponentId, public val title: String, public val fields: List<TextField>) {
    init {
        require(title.isNotBlank()) { "A modal needs a title." }
        require(fields.isNotEmpty()) { "A modal with no fields cannot be submitted." }
        require(fields.size <= MAX_FIELDS) { "A modal holds at most $MAX_FIELDS fields, got ${fields.size}." }
        require(fields.map { it.name }.toSet().size == fields.size) {
            "Modal field names must be unique, or reading one back by name is ambiguous."
        }
    }

    @SculkStable
    public companion object {
        public const val MAX_FIELDS: Int = 5
    }
}

/** Builds a [Modal]. */
@SculkStable
public class ModalBuilder internal constructor(private val id: ComponentId, private val title: String) {
    private val fields = mutableListOf<TextField>()

    public fun field(
        name: String,
        label: String,
        style: TextFieldStyle = TextFieldStyle.Short,
        value: String? = null,
        placeholder: String? = null,
        required: Boolean = true,
        maxLength: Int? = null,
    ) {
        fields += TextField(name, label, style, value, placeholder, required, maxLength)
    }

    internal fun build(): Modal = Modal(id, title, fields.toList())
}

@SculkStable
public fun modal(id: ComponentId, title: String, block: ModalBuilder.() -> Unit): Modal = ModalBuilder(id, title).apply(block).build()
