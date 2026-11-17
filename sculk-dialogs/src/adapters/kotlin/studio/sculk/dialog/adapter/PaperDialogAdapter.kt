@file:OptIn(SculkExperimental::class)

package studio.sculk.dialog.adapter

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.event.RegistryEvents
import io.papermc.paper.registry.keys.tags.DialogTagKeys
import io.papermc.paper.tag.TagEntry
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import studio.sculk.annotation.SculkExperimental
import studio.sculk.dialog.DialogAdapter
import studio.sculk.dialog.DialogInput
import studio.sculk.dialog.DialogPlacement
import studio.sculk.dialog.DialogSpec
import studio.sculk.registry.composeRegistry
import studio.sculk.text.Text
import java.time.Duration
import io.papermc.paper.registry.data.dialog.input.DialogInput as PaperInput

/** Paper 1.21.7 and later. Instantiated by name only, after a probe found the dialog API. */
internal class PaperDialogAdapter : DialogAdapter {
    override fun show(player: Player, spec: DialogSpec, text: Text, answer: (button: Int, values: Map<String, Any?>) -> Unit) {
        // One use per showing; an unanswered dialog's callbacks expire with the lifetime.
        val type = type(spec, text) { index -> DialogAction.customClick({ view, _ -> answer(index, read(spec, view)) }, OPTIONS) }
        player.showDialog(Dialog.create { factory -> factory.empty().base(base(spec, text)).type(type) })
    }

    override fun close(player: Player) {
        player.closeDialog()
    }

    override fun register(context: BootstrapContext, key: Key, spec: DialogSpec, text: Text, placements: Set<DialogPlacement>) {
        val typed = TypedKey.create(RegistryKey.DIALOG, key)
        // A registered dialog outlives any callback, so its buttons send a key the listener routes.
        val type = type(spec, text) { index -> DialogAction.customClick(Key.key(key.namespace(), "${key.value()}$BUTTON$index"), null) }
        val base = base(spec, text)
        context.composeRegistry(RegistryEvents.DIALOG) { registry -> registry.register(typed) { builder -> builder.base(base).type(type) } }
        if (placements.isEmpty()) return
        context.lifecycleManager.registerEventHandler(LifecycleEvents.TAGS.preFlatten(RegistryKey.DIALOG)) { event ->
            placements.forEach { placement ->
                val tag = when (placement) {
                    DialogPlacement.PAUSE_SCREEN -> DialogTagKeys.PAUSE_SCREEN_ADDITIONS
                    DialogPlacement.QUICK_ACTIONS -> DialogTagKeys.QUICK_ACTIONS
                }
                event.registrar().addToTag(tag, listOf(TagEntry.valueEntry(typed)))
            }
        }
    }

    override fun showRegistered(player: Player, key: Key): Boolean {
        val dialog = RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG).get(key) ?: return false
        player.showDialog(dialog)
        return true
    }

    override fun listen(
        plugin: Plugin,
        lookup: (Key) -> DialogSpec?,
        answer: (player: Player, spec: DialogSpec, button: Int, values: Map<String, Any?>) -> Unit,
    ) {
        val listener = object : Listener {
            @EventHandler
            fun onClick(event: PlayerCustomClickEvent) {
                // Clicks during configuration have no player to answer for.
                val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
                val id = event.identifier
                val at = id.value().lastIndexOf(BUTTON)
                if (at < 0) return
                val button = id.value().substring(at + BUTTON.length).toIntOrNull() ?: return
                val spec = lookup(Key.key(id.namespace(), id.value().substring(0, at))) ?: return
                answer(player, spec, button, event.dialogResponseView?.let { read(spec, it) } ?: emptyMap())
            }
        }
        plugin.server.pluginManager.registerEvents(listener, plugin)
    }

    private fun base(spec: DialogSpec, text: Text): DialogBase = DialogBase.builder(text.render(spec.title))
        .canCloseWithEscape(spec.canCloseWithEscape)
        .body(spec.body.map { DialogBody.plainMessage(text.render(it)) })
        .inputs(spec.inputs.map { input(it, text) })
        .build()

    private fun type(spec: DialogSpec, text: Text, action: (index: Int) -> DialogAction): DialogType {
        val buttons = spec.buttons.mapIndexed { index, button ->
            ActionButton.builder(text.render(button.label))
                .apply { button.tooltip?.let { tooltip(text.render(it)) } }
                .action(action(index))
                .build()
        }
        return when (buttons.size) {
            1 -> DialogType.notice(buttons.single())
            2 -> DialogType.confirmation(buttons[0], buttons[1])
            else -> DialogType.multiAction(buttons).columns(2).build()
        }
    }

    private fun input(input: DialogInput, text: Text): PaperInput = when (input) {
        is DialogInput.Text -> PaperInput.text(input.key, text.render(input.label))
            .initial(input.initial)
            .maxLength(input.maxLength)
            .apply { if (input.lines > 1) multiline(TextDialogInput.MultilineOptions.create(input.lines, null)) }
            .build()

        is DialogInput.Toggle -> PaperInput.bool(input.key, text.render(input.label)).initial(input.initial).build()

        is DialogInput.Number -> PaperInput.numberRange(input.key, text.render(input.label), input.range.start, input.range.endInclusive)
            .apply { input.step?.let { step(it) } }
            .apply { input.initial?.let { initial(it) } }
            .build()

        is DialogInput.Amount -> PaperInput.text(input.key, text.render(input.label))
            .initial(input.initial?.let(input.amounts::compact).orEmpty())
            .maxLength(input.maxLength)
            .build()

        is DialogInput.Choice -> PaperInput.singleOption(
            input.key,
            text.render(input.label),
            input.options.map { (id, label) -> SingleOptionDialogInput.OptionEntry.create(id, text.render(label), id == input.initial) },
        ).build()
    }

    // The client sends whatever it likes; values are read by declared kind and checked by DialogResponse.
    private fun read(spec: DialogSpec, view: DialogResponseView): Map<String, Any?> = spec.inputs.associate { input ->
        input.key to when (input) {
            is DialogInput.Text, is DialogInput.Amount, is DialogInput.Choice -> view.getText(input.key)
            is DialogInput.Toggle -> view.getBoolean(input.key)
            is DialogInput.Number -> view.getFloat(input.key)
        }
    }

    private companion object {
        const val BUTTON = "/button/"

        val OPTIONS: ClickCallback.Options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()
    }
}
