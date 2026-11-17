package studio.sculk.itest.dialogs

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.dialog.Dialogs
import studio.sculk.dialog.dialog
import studio.sculk.getOrThrow
import studio.sculk.itest.bot.BotDriver
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Shows a form to the test client and checks the answers it sends, valid and not, arrive typed on
 * the player's thread, and does the same for a dialog registered at bootstrap; where dialogs do not
 * exist, checks the fallback. Writes `results.properties`
 * and stops the server.
 */
class DialogsCheckPlugin : JavaPlugin(), Listener {
    private val results = sortedMapOf<String, String>()
    private val joined = CompletableFuture<Player>()
    private val answers = LinkedBlockingQueue<String>()
    private lateinit var dialogs: Dialogs

    private val form = dialog("Shop setup") {
        body("Fill in your shop.")
        text("name", "Name", maxLength = 8)
        toggle("open", "Open now")
        number("price", "Price", 1f..100f, step = 1f, initial = 10f)
        choice("colour", "Colour", listOf("red" to "Red", "blue" to "Blue"), initial = "red")
        button("Save") { answer ->
            answers.put(
                "name=${answer.text("name")} open=${answer.toggle("open")} price=${answer.number("price")} " +
                    "colour=${answer.choice("colour")} owned=${Bukkit.isOwnedByCurrentRegion(answer.player)}",
            )
        }
        button("Cancel")
        fallback { player -> player.sendMessage(Component.text("Fallback for ${player.name}")) }
    }

    override fun onEnable() {
        dialogs = Dialogs(this)
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runDelayed(this, { thread(name = "dialogs-check") { check() } }, 20)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        joined.complete(event.player)
    }

    private fun check() {
        try {
            BotDriver("SculkBot", server.port).use { bot ->
                bot.await("JOINED", 60)
                val player = joined.get(30, TimeUnit.SECONDS)
                if (!dialogs.isAvailable) {
                    verdict("dialog-or-fallback-shows") {
                        dialogs.show(player, form)
                        bot.await("CHAT") { it.fields[0] == "Fallback for SculkBot" }
                        val bare = dialogs.show(player, dialog("No fallback") { button("OK") })
                        (bare is SculkFailure.UnsupportedCapability) to "without a fallback show returned $bare"
                    }
                    verdict("registered-dialogs-show-and-answer") {
                        val registered = RegisteredRules.registered
                        (registered is SculkFailure.UnsupportedCapability) to "registering at bootstrap gave $registered"
                    }
                    results["typed-answers-on-the-players-thread"] = "SKIP: no dialogs before Paper 1.21.7"
                    results["invalid-answers-read-as-null"] = "SKIP: no dialogs before Paper 1.21.7"
                    return@use
                }
                verdict("dialog-or-fallback-shows") {
                    dialogs.show(player, form)
                    val shown = bot.await("DIALOG")
                    val ok = shown.fields[0] == "Shop setup" && shown.fields[1] == "Save|Cancel" &&
                        shown.fields[2] == "name:text|open:boolean|price:number_range|colour:single_option"
                    ok to "dialog $shown"
                }
                verdict("typed-answers-on-the-players-thread") {
                    bot.answerDialog("Save", "name" to "Daisy", "open" to true, "price" to 42, "colour" to "blue")
                    val seen = answers.poll(15, TimeUnit.SECONDS)
                    (seen == "name=Daisy open=true price=42.0 colour=blue owned=true") to "handler saw '$seen'"
                }
                verdict("invalid-answers-read-as-null") {
                    dialogs.show(player, form)
                    bot.await("DIALOG")
                    bot.answerDialog("Save", "name" to "far too long", "open" to false, "price" to 1000, "colour" to "green")
                    val seen = answers.poll(15, TimeUnit.SECONDS)
                    (seen == "name=null open=false price=null colour=null owned=true") to "handler saw '$seen'"
                }
                verdict("registered-dialogs-show-and-answer") {
                    check(RegisteredRules.registered is SculkResult.Success) { "registering at bootstrap gave ${RegisteredRules.registered}" }
                    for (tag in listOf("minecraft:pause_screen_additions", "minecraft:quick_actions")) {
                        bot.awaitState("DIALOGTAG", tag) { "sculk_itest:rules" in it.fields[1].split(',') }
                    }
                    val unknown = dialogs.show(player, Key.key("sculk_itest", "missing"))
                    check(unknown is SculkFailure.InvalidInput) { "an unknown key gave $unknown" }
                    dialogs.show(player, RegisteredRules.key).getOrThrow()
                    val shown = bot.await("DIALOG")
                    check(shown.fields[0] == "Server rules" && shown.fields[1] == "Agree|Leave" && shown.fields[2] == "note:text") { "dialog $shown" }
                    bot.answerDialog("Agree", "note" to "hi")
                    val seen = RegisteredRules.answers.poll(15, TimeUnit.SECONDS)
                    (seen == "agree note=hi owned=true") to "handler saw '$seen'"
                }
                verdict("a-button-without-a-handler-only-closes") {
                    dialogs.show(player, form)
                    bot.await("DIALOG")
                    bot.answerDialog("Cancel")
                    val seen = answers.poll(2, TimeUnit.SECONDS)
                    (seen == null) to "a handler ran: '$seen'"
                }
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("dialog-or-fallback-shows", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.globalRegionScheduler.run(this) { server.shutdown() }
    }

    private fun verdict(name: String, block: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }
}
