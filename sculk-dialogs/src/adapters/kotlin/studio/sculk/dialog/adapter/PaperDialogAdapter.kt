package studio.sculk.dialog.adapter

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import studio.sculk.dialog.DialogAdapter
import studio.sculk.dialog.DialogInput
import studio.sculk.dialog.DialogSpec
import studio.sculk.text.Text
import java.time.Duration
import io.papermc.paper.registry.data.dialog.input.DialogInput as PaperInput

/** Paper 1.21.7 and later. Instantiated by name only, after a probe found the dialog API. */
internal class PaperDialogAdapter : DialogAdapter {
    override fun show(player: Player, spec: DialogSpec, text: Text, answer: (button: Int, values: Map<String, Any?>) -> Unit) {
        val buttons = spec.buttons.mapIndexed { index, button ->
            ActionButton.builder(text.render(button.label))
                .apply { button.tooltip?.let { tooltip(text.render(it)) } }
                // One use per showing; an unanswered dialog's callbacks expire with the lifetime.
                .action(DialogAction.customClick({ view, _ -> answer(index, read(spec, view)) }, OPTIONS))
                .build()
        }
        val base = DialogBase.builder(text.render(spec.title))
            .canCloseWithEscape(spec.canCloseWithEscape)
            .body(spec.body.map { DialogBody.plainMessage(text.render(it)) })
            .inputs(spec.inputs.map { input(it, text) })
            .build()
        val type = when (buttons.size) {
            1 -> DialogType.notice(buttons.single())
            2 -> DialogType.confirmation(buttons[0], buttons[1])
            else -> DialogType.multiAction(buttons).columns(2).build()
        }
        player.showDialog(Dialog.create { factory -> factory.empty().base(base).type(type) })
    }

    override fun close(player: Player) {
        player.closeDialog()
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

        is DialogInput.Choice -> PaperInput.singleOption(
            input.key,
            text.render(input.label),
            input.options.map { (id, label) -> SingleOptionDialogInput.OptionEntry.create(id, text.render(label), id == input.initial) },
        ).build()
    }

    // The client sends whatever it likes; values are read by declared kind and checked by DialogResponse.
    private fun read(spec: DialogSpec, view: DialogResponseView): Map<String, Any?> = spec.inputs.associate { input ->
        input.key to when (input) {
            is DialogInput.Text, is DialogInput.Choice -> view.getText(input.key)
            is DialogInput.Toggle -> view.getBoolean(input.key)
            is DialogInput.Number -> view.getFloat(input.key)
        }
    }

    private companion object {
        val OPTIONS: ClickCallback.Options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()
    }
}
