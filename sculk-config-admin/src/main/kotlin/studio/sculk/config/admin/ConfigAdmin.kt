package studio.sculk.config.admin

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.Plugin
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.config.ConfigField
import studio.sculk.config.ConfigFile
import studio.sculk.dialog.DialogInput
import studio.sculk.dialog.DialogResponse
import studio.sculk.dialog.Dialogs
import studio.sculk.dialog.dialog
import studio.sculk.inventory.MenuClick
import studio.sculk.inventory.MenuLayout
import studio.sculk.inventory.Menus
import studio.sculk.inventory.menu
import studio.sculk.item.ItemSpec
import studio.sculk.item.item
import studio.sculk.text.Text
import java.math.BigDecimal

/**
 * An in-game editor for a [ConfigFile], built from its schema ([ConfigFile.fields]): a dialog where
 * the server has them, and otherwise a menu, which edits toggles, choices and bounded numbers and
 * sends the operator to the file for text. Every change goes through [ConfigFile.update], so it is
 * checked as a load would check it and keeps the file's comments.
 *
 * Saving writes the file off the player's thread, and never once [plugin] is disabling; the operator
 * is told the outcome in chat. Who may open the editor is the plugin's decision: call [open] behind
 * a permission.
 */
@SculkStable
public class ConfigAdmin(
    private val plugin: Plugin,
    private val dialogs: Dialogs,
    private val menus: Menus,
    private val text: Text = Text(),
) {
    /**
     * Opens the editor for [file] to [player], from any thread. [title] is a trusted MiniMessage
     * template, as every dialog and menu title is; by default the file's name, shown as written.
     */
    public fun open(
        player: Player,
        file: ConfigFile<*>,
        title: String = MiniMessage.miniMessage().escapeTags(file.path.fileName.toString()),
    ): SculkResult<Unit> {
        val fields = file.fields()
        if (fields.isEmpty()) return SculkFailure.InvalidInput(file.path.toString(), "a configuration with editable settings", "none")
        return dialogs.show(player, form(file, fields, title))
    }

    private fun form(file: ConfigFile<*>, fields: List<ConfigField>, title: String) = dialog("<primary>$title") {
        fields.forEachIndexed { index, field -> input(input(field, key(index))) }
        button("Save") { answer -> save(answer.player, file, changes(fields, answer)) }
        button("Cancel")
        fallback { player -> menus.open(player, fallbackMenu(file, title), 0) }
    }

    private fun input(field: ConfigField, key: String): DialogInput {
        val label = field.path + if (field.secret) " (leave empty to keep)" else ""
        return when (field.kind) {
            ConfigField.Kind.TOGGLE -> DialogInput.Toggle(key, label, field.current == "true")

            ConfigField.Kind.CHOICE -> {
                val initial = field.current.takeIf { it in field.choices }
                DialogInput.Choice(key, label, field.choices.map { it to it }, initial)
            }

            ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL -> {
                val range = sliderRange(field)
                if (range != null) {
                    val whole = field.kind == ConfigField.Kind.WHOLE
                    val bounds = range.start.toFloat()..range.endInclusive.toFloat()
                    DialogInput.Number(
                        key,
                        label,
                        bounds,
                        step = if (whole) 1f else ((range.endInclusive - range.start) / DECIMAL_STEPS).toFloat(),
                        initial = field.current.toFloatOrNull()?.coerceIn(bounds.start, bounds.endInclusive),
                    )
                } else {
                    DialogInput.Text(key, label, field.current, maxLength = TEXT_LIMIT)
                }
            }

            ConfigField.Kind.TEXT -> DialogInput.Text(key, label, if (field.secret) "" else field.current, maxLength = TEXT_LIMIT)
        }
    }

    /** Only what differs from the file, so an untouched secret or slider never rewrites a value. */
    private fun changes(fields: List<ConfigField>, answer: DialogResponse): Map<String, String> = buildMap {
        fields.forEachIndexed { index, field ->
            val key = key(index)
            val value = when (field.kind) {
                ConfigField.Kind.TOGGLE -> answer.toggle(key)?.toString()
                ConfigField.Kind.CHOICE -> answer.choice(key)
                ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL -> numberAnswer(field, answer, key)
                ConfigField.Kind.TEXT -> answer.text(key)?.takeUnless { field.secret && it.isEmpty() }
            } ?: return@forEachIndexed
            if (value != field.current || field.secret) put(field.path, value)
        }
    }

    private fun numberAnswer(field: ConfigField, answer: DialogResponse, key: String): String? {
        if (sliderRange(field) == null) return answer.text(key)
        return answer.number(key)?.let { editedNumber(field.kind, field.current, it) }
    }

    /** The field's bounds when a slider can edit it exactly, otherwise null and it is edited as text. */
    private fun sliderRange(field: ConfigField): ClosedFloatingPointRange<Double>? = field.range?.takeIf { slides(field.kind, it) }

    // Writing the file is blocking I/O, which never runs on a player's or the main thread.
    private fun save(player: Player, file: ConfigFile<*>, changes: Map<String, String>, then: () -> Unit = {}) {
        if (changes.isEmpty()) {
            player.sendMessage(text.render("<muted>Nothing changed."))
            return
        }
        try {
            Bukkit.getAsyncScheduler().runNow(plugin) {
                // The plugin's lifetime bounds every save: a task that starts once it is disabling writes nothing.
                if (!plugin.isEnabled) return@runNow
                when (val result = file.update(changes)) {
                    is SculkResult.Success -> player.sendMessage(text.render("<success>Saved <count> setting(s).", "count" to changes.size))
                    is SculkFailure -> player.sendMessage(text.render("<danger>Not saved: <reason>", "reason" to result.message))
                }
                then()
            }
        } catch (_: IllegalPluginAccessException) {
            player.sendMessage(text.render("<danger>Not saved: <reason>", "reason" to "${plugin.name} is shutting down."))
        }
    }

    // The fallback edits what a click can: toggles flip, choices cycle, bounded numbers step. Its state is the page shown.
    private fun fallbackMenu(file: ConfigFile<*>, title: String) = menu<Int>(MenuLayout.chest(6)) {
        this.text = this@ConfigAdmin.text
        title("<primary>$title")
        render { page ->
            val shown = paginate(PAGE_SLOTS, file.fields(), page, ::icon) { field -> edit(this, file, field) }
            if (shown.hasPrevious) slot(PREVIOUS_SLOT, arrow("Previous page")) { update { shown.number - 1 } }
            if (shown.hasNext) slot(NEXT_SLOT, arrow("Next page")) { update { shown.number + 1 } }
        }
    }

    private fun edit(click: MenuClick<Int>, file: ConfigFile<*>, field: ConfigField) {
        val next = step(field, click.click.isRightClick, click.click.isShiftClick)
        if (next == null) {
            val name = file.path.fileName.toString()
            click.player.sendMessage(text.render("<muted>Edit <path> in <file>.", "path" to field.path, "file" to name))
        } else {
            // The menu draws from the file, so redrawing the same page after the save shows the new value.
            save(click.player, file, mapOf(field.path to next)) { click.refresh() }
        }
    }

    private fun arrow(label: String): ItemSpec = item(Material.ARROW) { name = "<primary>$label" }

    private fun icon(field: ConfigField): ItemSpec = item(material(field)) {
        name = "<primary><setting>"
        value("setting", field.path)
        value("current", if (field.secret) "(secret)" else field.current.ifEmpty { "(empty)" })
        field.comment.forEachIndexed { index, line -> value("note-$index", line) }
        lore(listOf("<value><current>") + field.comment.indices.map { "<muted><note-$it>" } + "<muted>${usage(field)}")
    }

    private fun material(field: ConfigField): Material = when (field.kind) {
        ConfigField.Kind.TOGGLE -> if (field.current == "true") Material.LIME_DYE else Material.GRAY_DYE
        ConfigField.Kind.CHOICE -> Material.COMPARATOR
        ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL -> Material.CLOCK
        ConfigField.Kind.TEXT -> Material.PAPER
    }

    private fun usage(field: ConfigField): String = when (field.kind) {
        ConfigField.Kind.TOGGLE -> "Click to switch."

        ConfigField.Kind.CHOICE -> "Click for the next choice."

        ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL ->
            if (field.range != null) "Left more, right less, shift for ten steps." else "Edit in the file."

        ConfigField.Kind.TEXT -> "Edit in the file."
    }

    /** The value one click makes, or null where a click cannot edit the field. */
    private fun step(field: ConfigField, down: Boolean, large: Boolean): String? = when (field.kind) {
        ConfigField.Kind.TOGGLE -> (field.current != "true").toString()

        ConfigField.Kind.CHOICE -> field.choices.getOrNull((field.choices.indexOf(field.current) + 1) % field.choices.size)

        ConfigField.Kind.WHOLE -> field.range?.let { range ->
            val change = (if (large) 10 else 1) * (if (down) -1 else 1)
            val next = ((field.current.toDoubleOrNull() ?: range.start) + change).coerceIn(range.start, range.endInclusive)
            Math.round(next).toString()
        }

        ConfigField.Kind.DECIMAL -> field.range?.let { range -> decimalStep(field.current, range, down, large) }

        ConfigField.Kind.TEXT -> null
    }

    private fun key(index: Int): String = "f$index"

    private companion object {
        const val TEXT_LIMIT = 256
        const val PREVIOUS_SLOT = 45
        const val NEXT_SLOT = 53
        val PAGE_SLOTS = (0 until 45).toList()
    }
}

/**
 * Whether a slider edits a [kind] setting bounded by [range] exactly: the range is not empty, and a
 * whole number never leaves the integers a Float holds exactly.
 */
internal fun slides(kind: ConfigField.Kind, range: ClosedFloatingPointRange<Double>): Boolean {
    if (range.start >= range.endInclusive) return false
    val limit = if (kind == ConfigField.Kind.WHOLE) EXACT_FLOAT_INTEGERS else Float.MAX_VALUE.toDouble()
    return range.start >= -limit && range.endInclusive <= limit
}

/** The text to save for a slider at [edited], or null when it equals [current] as a number, so an untouched slider saves nothing. */
internal fun editedNumber(kind: ConfigField.Kind, current: String, edited: Float): String? = if (kind == ConfigField.Kind.WHOLE) {
    Math.round(edited).toLong().takeUnless { it == current.toLongOrNull() }?.toString()
} else {
    edited.takeUnless { it == current.toFloatOrNull() }?.toString()
}

/** [current] moved by a hundredth of [range], ten of them when [large], kept in [range] and written as plain decimal text. */
internal fun decimalStep(current: String, range: ClosedFloatingPointRange<Double>, down: Boolean, large: Boolean): String {
    val unit = BigDecimal.valueOf((range.endInclusive - range.start) / DECIMAL_STEPS)
    val change = unit.multiply(BigDecimal.valueOf(if (large) 10L else 1L)).let { if (down) it.negate() else it }
    val start = BigDecimal.valueOf(range.start)
    val next = (current.toBigDecimalOrNull() ?: start).add(change).max(start).min(BigDecimal.valueOf(range.endInclusive))
    return next.stripTrailingZeros().toPlainString()
}

private const val DECIMAL_STEPS = 100.0

// 2^24: above it a Float skips integers, so a slider would round a whole setting to a neighbour.
private const val EXACT_FLOAT_INTEGERS = 16_777_216.0
