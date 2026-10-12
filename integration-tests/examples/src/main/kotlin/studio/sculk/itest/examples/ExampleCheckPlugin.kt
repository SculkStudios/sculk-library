package studio.sculk.itest.examples

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandException
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs the economy example the way an operator would: reads its generated files, its registered
 * permissions, and the replies to its commands through a sender that captures feedback. Writes
 * `results.properties` and stops the server. There are no players in CI, so player-only paths are
 * covered by the example's own unit tests.
 */
class ExampleCheckPlugin : JavaPlugin() {
    private val results = ConcurrentHashMap<String, String>()

    override fun onEnable() {
        // The first global tick runs after every plugin has enabled and the command tree is built.
        server.globalRegionScheduler.runDelayed(this, { check() }, 20)
    }

    private fun check() {
        val economy = server.pluginManager.getPlugin("SculkEconomy")
        verdict("economy-enabled") { (economy?.isEnabled == true) to "SculkEconomy enabled: ${economy?.isEnabled}" }
        val folder = economy?.dataFolder ?: File("plugins/SculkEconomy")
        verdict("config-generated-with-comments") {
            val text = File(folder, "config.yml").takeIf(File::isFile)?.readText().orEmpty()
            ("currency: coins" in text && "# What a new account starts with." in text) to "config.yml was\n$text"
        }
        verdict("permissions-registered-with-defaults") {
            val pay = server.pluginManager.getPermission("economy.pay")
            val admin = server.pluginManager.getPermission("economy.admin")
            (pay?.default == PermissionDefault.TRUE && admin?.default == PermissionDefault.OP) to "pay ${pay?.default}, admin ${admin?.default}"
        }
        verdict("help-lists-the-admin-forms") {
            val replies = run("eco help")
            (replies.firstOrNull() == "/economy can be used as:" && "/economy give <player> <amount> [--silent]" in replies) to "replies $replies"
        }
        verdict("console-is-asked-to-name-a-player") {
            val replies = run("balance")
            (replies == listOf("Name a player: /balance [player].")) to "replies $replies"
        }
        verdict("payments-are-for-players-only") {
            val replies = run("pay Someone 5")
            (replies == listOf("Only players can run this command.")) to "replies $replies"
        }
        verdict("an-empty-economy-has-no-top-list") {
            val replies = run("eco top --size 3")
            (replies == listOf("Nobody has an account yet.")) to "replies $replies"
        }
        verdict("reset-asks-for-confirmation") {
            val first = run("eco reset")
            val second = run("eco reset")
            (first == listOf("Run the command again within 10s to confirm.") && second == listOf("Every balance was reset.")) to
                "first $first, then $second"
        }
        write()
        server.shutdown()
    }

    /**
     * Runs [command] as a sender that records its feedback. Executors start on the sender's
     * context, which for this console-like sender is the global region, already ours here, so the
     * replies arrive before dispatch returns; a short wait covers an executor that suspends.
     */
    private fun run(command: String): List<String> {
        val replies = CopyOnWriteArrayList<String>()
        val sender = server.createCommandSender { replies += PlainTextComponentSerializer.plainText().serialize(it.asComponent()) }
        try {
            server.dispatchCommand(sender, command)
        } catch (failure: CommandException) {
            replies += "threw ${failure.cause ?: failure}"
        }
        val deadline = System.nanoTime() + WAIT_NANOS
        while (replies.isEmpty() && System.nanoTime() < deadline) Thread.onSpinWait()
        return replies.toList()
    }

    private fun verdict(name: String, check: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = check()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Exception) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    private fun write() {
        val file = File(dataFolder, "results.properties").also { it.parentFile.mkdirs() }
        file.writeText(results.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=${value.replace("\n", " | ")}" })
    }

    private companion object {
        const val WAIT_NANOS = 200_000_000L
    }
}
