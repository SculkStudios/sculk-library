package studio.sculk.config.admin

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
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
import studio.sculk.inventory.MenuLayout
import studio.sculk.inventory.Menus
import studio.sculk.inventory.menu
import studio.sculk.item.ItemSpec
import studio.sculk.item.item
import studio.sculk.text.Text
import java.util.Locale

/**
 * An in-game editor for a [ConfigFile], built from its schema ([ConfigFile.fields]): a dialog where
 * the server has them, and otherwise a menu, which edits toggles, choices and bounded numbers and
 * sends the operator to the file for text. Every change goes through [ConfigFile.update], so it is
 * checked as a load would check it and keeps the file's comments.
 *
 * Saving writes the file off the player's thread; the operator is told the outcome in chat. Who
 * may open the editor is the plugin's decision: call [open] behind a permission.
 */
@SculkStable
public class ConfigAdmin(
    private val plugin: Plugin,
    private val dialogs: Dialogs,
    private val menus: Menus,
    private val text: Text = Text(),
) {
    /** Opens the editor for [file] to [player], from any thread. */
    public fun open(player: Player, file: ConfigFile<*>, title: String = file.path.fileName.toString()): SculkResult<Unit> {
        val fields = file.fields()
        if (fields.isEmpty()) return SculkFailure.InvalidInput(file.path.toString(), "a configuration with editable settings", "none")
        return dialogs.show(player, form(file, fields, title))
    }

    private fun form(file: ConfigFile<*>, fields: List<ConfigField>, title: String) = dialog("<primary>$title") {
        fields.forEachIndexed { index, field -> input(input(field, key(index))) }
        button("Save") { answer -> save(answer.player, file, changes(fields, answer)) }
        button("Cancel")
        fallback { player -> menus.open(player, fallbackMenu(file, title), Unit) }
    }

    private fun input(field: ConfigField, key: String): DialogInput {
        val label = field.path + if (field.secret) " (leave empty to keep)" else ""
        val range = field.range
        return when (field.kind) {
            ConfigField.Kind.TOGGLE -> DialogInput.Toggle(key, label, field.current == "true")

            ConfigField.Kind.CHOICE -> DialogInput.Choice(
                key,
                label,
                field.choices.map { it to it },
                field.current.takeIf {
                    it in
                        field.choices
                },
            )

            ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL ->
                if (range != null) {
                    val whole = field.kind == ConfigField.Kind.WHOLE
                    DialogInput.Number(
                        key,
                        label,
                        range.start.toFloat()..range.endInclusive.toFloat(),
                        step = if (whole) 1f else ((range.endInclusive - range.start) / DECIMAL_STEPS).toFloat(),
                        initial = field.current.toFloatOrNull()?.coerceIn(range.start.toFloat(), range.endInclusive.toFloat()),
                    )
                } else {
                    DialogInput.Text(key, label, field.current, maxLength = TEXT_LIMIT)
                }

            ConfigField.Kind.TEXT -> DialogInput.Text(key, label, if (field.secret) "" else field.current, maxLength = TEXT_LIMIT)
        }
    }

    /** Only what differs from the file, so an untouched secret or rounding never rewrites a value. */
    private fun changes(fields: List<ConfigField>, answer: DialogResponse): Map<String, String> = buildMap {
        fields.forEachIndexed { index, field ->
            val key = key(index)
            val value = when (field.kind) {
                ConfigField.Kind.TOGGLE -> answer.toggle(key)?.toString()

                ConfigField.Kind.CHOICE -> answer.choice(key)

                ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL -> if (field.range !=
                    null
                ) {
                    number(field, answer.number(key))
                } else {
                    answer.text(key)
                }

                ConfigField.Kind.TEXT -> answer.text(key)?.takeUnless { field.secret && it.isEmpty() }
            } ?: return@forEachIndexed
            if (value != field.current || field.secret) put(field.path, value)
        }
    }

    private fun number(field: ConfigField, value: Float?): String? = value?.let {
        if (field.kind == ConfigField.Kind.WHOLE) Math.round(it).toString() else String.format(Locale.ROOT, "%s", it.toDouble())
    }

    // Writing the file is blocking I/O, which never runs on a player's or the main thread.
    private fun save(player: Player, file: ConfigFile<*>, changes: Map<String, String>, then: () -> Unit = {}) {
        if (changes.isEmpty()) {
            player.sendMessage(text.render("<muted>Nothing changed."))
            return
        }
        Bukkit.getAsyncScheduler().runNow(plugin) {
            when (val result = file.update(changes)) {
                is SculkResult.Success -> player.sendMessage(text.render("<success>Saved <count> setting(s).", "count" to changes.size))
                is SculkFailure -> player.sendMessage(text.render("<danger>Not saved: <reason>", "reason" to result.message))
            }
            then()
        }
    }

    // The fallback edits what a click can: toggles flip, choices cycle, bounded numbers step.
    private fun fallbackMenu(file: ConfigFile<*>, title: String) = menu<Unit>(MenuLayout.chest(6)) {
        this.text = this@ConfigAdmin.text
        title("<primary>$title")
        render {
            file.fields().take(MAX_MENU_FIELDS).forEachIndexed { slot, field ->
                slot(slot, icon(field)) {
                    val next = step(field, click.isRightClick, click.isShiftClick)
                    if (next == null) {
                        player.sendMessage(
                            this@ConfigAdmin.text.render(
                                "<muted>Edit <path> in <file>.",
                                "path" to field.path,
                                "file" to file.path.fileName.toString(),
                            ),
                        )
                    } else {
                        // The menu draws from the file, so redrawing after the save shows the new value.
                        save(player, file, mapOf(field.path to next)) { update {} }
                    }
                }
            }
        }
    }

    private fun icon(field: ConfigField): ItemSpec = item(
        when (field.kind) {
            ConfigField.Kind.TOGGLE -> if (field.current == "true") Material.LIME_DYE else Material.GRAY_DYE
            ConfigField.Kind.CHOICE -> Material.COMPARATOR
            ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL -> Material.CLOCK
            ConfigField.Kind.TEXT -> Material.PAPER
        },
    ) {
        name = "<primary>${field.path}"
        lore(
            buildList {
                add("<value>${if (field.secret) "(secret)" else field.current.ifEmpty { "(empty)" }}")
                field.comment.forEach { add("<muted>$it") }
                add(
                    when (field.kind) {
                        ConfigField.Kind.TOGGLE -> "<muted>Click to switch."

                        ConfigField.Kind.CHOICE -> "<muted>Click for the next choice."

                        ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL ->
                            if (field.range != null) "<muted>Left more, right less, shift for ten steps." else "<muted>Edit in the file."

                        ConfigField.Kind.TEXT -> "<muted>Edit in the file."
                    },
                )
            },
        )
    }

    /** The value one click makes, or null where a click cannot edit the field. */
    private fun step(field: ConfigField, down: Boolean, large: Boolean): String? = when (field.kind) {
        ConfigField.Kind.TOGGLE -> (field.current != "true").toString()

        ConfigField.Kind.CHOICE -> field.choices.getOrNull((field.choices.indexOf(field.current) + 1) % field.choices.size)

        ConfigField.Kind.WHOLE, ConfigField.Kind.DECIMAL -> field.range?.let { range ->
            val unit = if (field.kind == ConfigField.Kind.WHOLE) 1.0 else (range.endInclusive - range.start) / DECIMAL_STEPS
            val change = unit * (if (large) 10 else 1) * (if (down) -1 else 1)
            val next = ((field.current.toDoubleOrNull() ?: range.start) + change).coerceIn(range.start, range.endInclusive)
            if (field.kind == ConfigField.Kind.WHOLE) Math.round(next).toString() else String.format(Locale.ROOT, "%s", next)
        }

        ConfigField.Kind.TEXT -> null
    }

    private fun key(index: Int): String = "f$index"

    private companion object {
        const val TEXT_LIMIT = 256
        const val DECIMAL_STEPS = 100.0
        const val MAX_MENU_FIELDS = 54
    }
}
