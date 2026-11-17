package studio.sculk.itest.dialogs

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import studio.sculk.SculkResult
import studio.sculk.dialog.DialogPlacement
import studio.sculk.dialog.dialog
import studio.sculk.dialog.registerDialog
import java.util.concurrent.LinkedBlockingQueue

/** A dialog registered at bootstrap and offered on the pause screen and the quick actions key. */
object RegisteredRules {
    val key: Key = Key.key("sculk_itest", "rules")
    val answers = LinkedBlockingQueue<String>()

    @Volatile var registered: SculkResult<Unit>? = null

    val spec = dialog("Server rules") {
        body("Be kind.")
        text("note", "Note", maxLength = 16)
        button("Agree") { answer -> answers.put("agree note=${answer.text("note")} owned=${Bukkit.isOwnedByCurrentRegion(answer.player)}") }
        button("Leave")
    }
}

class DialogsCheckBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        RegisteredRules.registered = context.registerDialog(
            RegisteredRules.key,
            RegisteredRules.spec,
            setOf(DialogPlacement.PAUSE_SCREEN, DialogPlacement.QUICK_ACTIONS),
        )
    }
}
