package studio.sculk.dialog

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.text.Text
import java.util.logging.Level

/** Shows a dialog on a server that has them (ADR-0028). Not API. */
@SculkInternal
public interface DialogAdapter {
    /**
     * Shows [spec] to [player] rendered with [text] and [values]; [answer] receives the pressed button's
     * index and each input's raw value (String, Boolean or Float) on whatever thread Paper reports it.
     */
    public fun show(
        player: Player,
        spec: DialogSpec,
        text: Text,
        values: TagResolver,
        answer: (button: Int, values: Map<String, Any?>) -> Unit,
    )

    public fun close(player: Player)

    /** Registers [spec] as [key] at bootstrap; button `n` sends the custom click `<key>/button/<n>`. */
    public fun register(context: BootstrapContext, key: Key, spec: DialogSpec, text: Text, placements: Set<DialogPlacement>)

    /** Shows the registered dialog [key]; false when the server has none by that key. */
    public fun showRegistered(player: Player, key: Key): Boolean

    /**
     * Reports each press of a registered dialog's button that [lookup] knows to [answer], with each
     * input's raw value, on whatever thread Paper reports it, for as long as [plugin] is enabled.
     */
    public fun listen(
        plugin: Plugin,
        lookup: (Key) -> DialogSpec?,
        answer: (player: Player, spec: DialogSpec, button: Int, values: Map<String, Any?>) -> Unit,
    )
}

/**
 * Shows [DialogSpec]s. Answers run on the player's own thread: the main thread on Paper, the
 * player's region thread on Folia and Canvas.
 */
@SculkStable
public class Dialogs(private val plugin: Plugin, private val text: Text = Text()) {
    private val adapter = DialogAdapters.adapter

    init {
        adapter?.let { RegisteredDialogs.listen(plugin, it, ::onPlayer) }
    }

    /** Whether this server can show dialogs (Paper 1.21.7 and later). */
    public val isAvailable: Boolean get() = adapter != null

    /**
     * Shows [spec] to [player], from any thread. Where dialogs do not exist it shows the spec's
     * fallback instead, and without one returns [SculkFailure.UnsupportedCapability].
     */
    public fun show(player: Player, spec: DialogSpec): SculkResult<Unit> = show(player, spec, TagResolver.empty())

    /**
     * Shows [spec] as [show] above does, with [values] as placeholders in every title, paragraph,
     * label and tooltip, never parsed as markup: a price or a player's name enters the dialog this way.
     */
    @SculkExperimental
    public fun show(player: Player, spec: DialogSpec, values: Map<String, Any?>): SculkResult<Unit> =
        show(player, spec, text.placeholders(values))

    private fun show(player: Player, spec: DialogSpec, values: TagResolver): SculkResult<Unit> {
        val shown = adapter
        if (shown == null) {
            val fallback = spec.fallback ?: return SculkFailure.UnsupportedCapability("Dialogs", "added in Paper 1.21.7")
            onPlayer(player) { fallback(player) }
            return SculkResult.ok()
        }
        onPlayer(player) {
            shown.show(player, spec, text, values) { button, answers ->
                val onPress = spec.buttons.getOrNull(button)?.onPress ?: return@show
                onPlayer(player) {
                    try {
                        onPress(DialogResponse(player, spec, answers))
                    } catch (failure: Exception) {
                        plugin.logger.log(
                            Level.SEVERE,
                            "A dialog button '${spec.buttons[button].label}' failed for ${player.name}",
                            failure,
                        )
                    }
                }
            }
        }
        return SculkResult.ok()
    }

    /**
     * Shows the dialog registered as [key] (see [registerDialog]), from any thread. Returns
     * [SculkFailure.UnsupportedCapability] where dialogs do not exist and [SculkFailure.InvalidInput]
     * when this plugin registered no dialog by that key.
     */
    public fun show(player: Player, key: Key): SculkResult<Unit> {
        val shown = adapter ?: return SculkFailure.UnsupportedCapability("Dialogs", "added in Paper 1.21.7")
        if (!RegisteredDialogs.specs.containsKey(
                key,
            )
        ) {
            return SculkFailure.InvalidInput("dialog", "a dialog this plugin registered", key.asString())
        }
        onPlayer(player) { shown.showRegistered(player, key) }
        return SculkResult.ok()
    }

    /** Closes whatever dialog [player] has open. */
    public fun close(player: Player) {
        adapter?.let { shown -> onPlayer(player) { shown.close(player) } }
    }

    private fun onPlayer(player: Player, action: () -> Unit) {
        if (Bukkit.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }
}

internal object DialogAdapters {
    private const val MODERN_ADAPTER = "studio.sculk.dialog.adapter.PaperDialogAdapter"

    // Probed once: the dialog API exists from Paper 1.21.7, and the adapter is compiled against the
    // newest anchor, so it is never linked where the API is missing.
    val adapter: DialogAdapter? by lazy {
        val loader = DialogAdapters::class.java.classLoader
        if (runCatching { Class.forName("io.papermc.paper.dialog.Dialog", false, loader) }.isFailure) {
            null
        } else {
            Class.forName(MODERN_ADAPTER, true, loader).getDeclaredConstructor().newInstance() as DialogAdapter
        }
    }
}
