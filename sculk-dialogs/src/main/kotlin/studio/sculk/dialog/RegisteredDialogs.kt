package studio.sculk.dialog

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import net.kyori.adventure.key.Key
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.text.Text
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/** Where the client offers a registered dialog of its own accord, besides when a plugin shows it. */
@SculkStable
public enum class DialogPlacement {
    /** A button on the pause screen, which opens the dialog. */
    PAUSE_SCREEN,

    /** The dialog the client's quick actions key opens. */
    QUICK_ACTIONS,
}

/**
 * Registers [spec] as the dialog [key] while the server starts, so it is part of the server's data
 * and the client can offer it where [placements] say. Call it from a `PluginBootstrap`; the dialog
 * is the same for every player, so its templates are rendered once with [text] and no placeholders.
 *
 * The spec's buttons run once a [Dialogs] of the same plugin exists, on the answering player's
 * thread, as for a dialog it shows itself. Servers without dialogs return
 * [SculkFailure.UnsupportedCapability]; a spec's fallback has no meaning here.
 */
@SculkStable
public fun BootstrapContext.registerDialog(
    key: Key,
    spec: DialogSpec,
    placements: Set<DialogPlacement> = emptySet(),
    text: Text = Text(),
): SculkResult<Unit> {
    val adapter = DialogAdapters.adapter ?: return SculkFailure.UnsupportedCapability("Registered dialogs", "added in Paper 1.21.7")
    require(RegisteredDialogs.specs.putIfAbsent(key, spec) == null) { "the dialog $key is already registered" }
    adapter.register(this, key, spec, text, placements)
    return SculkResult.ok()
}

/** The dialogs this plugin registered at bootstrap, which shares its class loader with the plugin. */
internal object RegisteredDialogs {
    val specs = ConcurrentHashMap<Key, DialogSpec>()
    private val listening: MutableSet<Plugin> = ConcurrentHashMap.newKeySet()

    // One listener per plugin, however many Dialogs it makes, so a button runs once per press.
    fun listen(plugin: Plugin, adapter: DialogAdapter, onPlayer: (Player, () -> Unit) -> Unit) {
        if (!listening.add(plugin)) return
        adapter.listen(plugin, specs::get) { player, spec, button, values ->
            val onPress = spec.buttons.getOrNull(button)?.onPress ?: return@listen
            onPlayer(player) {
                try {
                    onPress(DialogResponse(player, spec, values))
                } catch (failure: Exception) {
                    plugin.logger.log(Level.SEVERE, "A dialog button '${spec.buttons[button].label}' failed for ${player.name}", failure)
                }
            }
        }
    }
}
