package studio.sculk.dialog

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.text.Text
import java.util.logging.Level

/** Shows a dialog on a server that has them (ADR-0028). Not API. */
@SculkInternal
public interface DialogAdapter {
    /**
     * Shows [spec] to [player] rendered with [text]; [answer] receives the pressed button's index and
     * each input's raw value (String, Boolean or Float) on whatever thread Paper reports it.
     */
    public fun show(player: Player, spec: DialogSpec, text: Text, answer: (button: Int, values: Map<String, Any?>) -> Unit)

    public fun close(player: Player)
}

/**
 * Shows [DialogSpec]s. Answers run on the player's own thread: the main thread on Paper, the
 * player's region thread on Folia and Canvas.
 */
@SculkStable
public class Dialogs(private val plugin: Plugin, private val text: Text = Text()) {
    /** Whether this server can show dialogs (Paper 1.21.7 and later). */
    public val isAvailable: Boolean get() = adapter != null

    /**
     * Shows [spec] to [player], from any thread. Where dialogs do not exist it shows the spec's
     * fallback instead, and without one returns [SculkFailure.UnsupportedCapability].
     */
    public fun show(player: Player, spec: DialogSpec): SculkResult<Unit> {
        val shown = adapter
        if (shown == null) {
            val fallback = spec.fallback ?: return SculkFailure.UnsupportedCapability("Dialogs", "added in Paper 1.21.7")
            onPlayer(player) { fallback(player) }
            return SculkResult.ok()
        }
        onPlayer(player) {
            shown.show(player, spec, text) { button, values ->
                val onPress = spec.buttons.getOrNull(button)?.onPress ?: return@show
                onPlayer(player) {
                    try {
                        onPress(DialogResponse(player, spec, values))
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

    /** Closes whatever dialog [player] has open. */
    public fun close(player: Player) {
        adapter?.let { shown -> onPlayer(player) { shown.close(player) } }
    }

    private fun onPlayer(player: Player, action: () -> Unit) {
        if (Bukkit.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    private companion object {
        const val MODERN_ADAPTER = "studio.sculk.dialog.adapter.PaperDialogAdapter"

        // Probed once: the dialog API exists from Paper 1.21.7, and the adapter is compiled against
        // the newest anchor, so it is never linked where the API is missing.
        val adapter: DialogAdapter? by lazy {
            val loader = Dialogs::class.java.classLoader
            if (runCatching { Class.forName("io.papermc.paper.dialog.Dialog", false, loader) }.isFailure) {
                null
            } else {
                Class.forName(MODERN_ADAPTER, true, loader).getDeclaredConstructor().newInstance() as DialogAdapter
            }
        }
    }
}
