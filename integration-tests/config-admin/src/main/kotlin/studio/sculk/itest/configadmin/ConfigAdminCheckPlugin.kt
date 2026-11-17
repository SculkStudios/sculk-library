package studio.sculk.itest.configadmin

import kotlinx.serialization.Serializable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.ResourceScope
import studio.sculk.config.ConfigFile
import studio.sculk.config.NotBlank
import studio.sculk.config.Range
import studio.sculk.config.SculkConfig
import studio.sculk.config.admin.ConfigAdmin
import studio.sculk.dialog.Dialogs
import studio.sculk.getOrThrow
import studio.sculk.inventory.Menus
import studio.sculk.itest.bot.BotDriver
import studio.sculk.itest.bot.slots
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class Mode { SURVIVAL, HARD_CORE }

@Serializable
data class ShopSettings(
    @NotBlank val title: String = "Shop",
    @Range(min = 1, max = 54) val slots: Int = 27,
    val open: Boolean = true,
    val mode: Mode = Mode.SURVIVAL,
)

/**
 * Opens the configuration editor for the test client and has it change and break settings, through
 * the dialog where the server has one and the menu where it does not. Writes `results.properties`
 * and stops the server.
 */
class ConfigAdminCheckPlugin : JavaPlugin(), Listener {
    private val results = sortedMapOf<String, String>()
    private val joined = CompletableFuture<Player>()
    private val scope = ResourceScope.root("config-admin-check")
    private lateinit var settings: ConfigFile<ShopSettings>
    private lateinit var dialogs: Dialogs
    private lateinit var admin: ConfigAdmin

    override fun onEnable() {
        // Each run edits the file, so it starts from the defaults or a rerun would see the last run's values.
        val file = dataFolder.toPath().resolve("shop.yml")
        Files.deleteIfExists(file)
        settings = SculkConfig.load<ShopSettings>(file).getOrThrow()
        dialogs = Dialogs(this)
        admin = ConfigAdmin(this, dialogs, Menus(this, scope))
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runDelayed(this, { thread(name = "config-admin-check") { check() } }, 20)
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
                if (dialogs.isAvailable) dialogPath(bot, player) else menuPath(bot, player)
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("editor-saves-changes", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
        scope.close()
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.globalRegionScheduler.run(this) { server.shutdown() }
    }

    private fun dialogPath(bot: BotDriver, player: Player) {
        verdict("editor-saves-changes") {
            admin.open(player, settings).getOrThrow()
            val form = bot.await("DIALOG")
            check(form.fields[2] == "f0:text|f1:number_range|f2:boolean|f3:single_option") { "inputs ${form.fields[2]}" }
            bot.answerDialog("Save", "f0" to "Shop", "f1" to 45, "f2" to false, "f3" to "hard-core")
            val said = bot.await("CHAT") { it.fields[0].startsWith("Saved") || it.fields[0].startsWith("Not saved") }
            val value = settings.value
            (said.fields[0] == "Saved 3 setting(s)." && value == ShopSettings("Shop", 45, false, Mode.HARD_CORE)) to "said '${said.fields[0]}', value $value"
        }
        verdict("invalid-changes-are-refused") {
            admin.open(player, settings).getOrThrow()
            bot.await("DIALOG")
            bot.answerDialog("Save", "f0" to "", "f1" to 45, "f2" to false, "f3" to "hard-core")
            val said = bot.await("CHAT") { it.fields[0].startsWith("Saved") || it.fields[0].startsWith("Not saved") }
            (said.fields[0].startsWith("Not saved: shop.yml: title") && settings.value.title == "Shop") to "said '${said.fields[0]}'"
        }
    }

    private fun menuPath(bot: BotDriver, player: Player) {
        verdict("editor-saves-changes") {
            admin.open(player, settings).getOrThrow()
            bot.await("WINDOW")
            val slots = bot.await("CONTENT").slots()
            check(slots[2] == "open") { "the menu shows $slots" }
            bot.click(2)
            val said = bot.await("CHAT") { it.fields[0].startsWith("Saved") || it.fields[0].startsWith("Not saved") }
            (said.fields[0] == "Saved 1 setting(s)." && !settings.value.open) to "said '${said.fields[0]}', value ${settings.value}"
        }
        verdict("invalid-changes-are-refused") {
            bot.click(0) // text cannot be edited from the menu: the operator is sent to the file
            val said = bot.await("CHAT") { it.fields[0].startsWith("Edit") }
            (said.fields[0] == "Edit title in shop.yml." && settings.value.title == "Shop") to "said '${said.fields[0]}'"
        }
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
