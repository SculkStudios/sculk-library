package studio.sculk.example

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import studio.sculk.SculkResult
import studio.sculk.adventure.reply
import studio.sculk.command.command
import studio.sculk.platform.SculkPlugin
import java.time.Duration
import java.util.UUID

public class PlayerProfilesPlugin :
    SculkPlugin({
        data()
        gui()
    }) {
    private lateinit var profiles: ProfileService

    override fun setup() {
        val repository = sculk.data.repository<PlayerProfile, UUID>()
        val cached =
            sculk.data.cached(repository, PlayerProfile::uuid) {
                ttl = Duration.ofMinutes(15)
                maxSize = 10_000
            }
        profiles =
            ProfileService(
                sculk.data.playerProfiles(cached) { uuid ->
                    val now = System.currentTimeMillis()
                    PlayerProfile(uuid, "", now, now, 0, 0, 0, 100)
                },
            )

        sculk.events.listen<PlayerJoinEvent> { event ->
            val player = event.player
            sculk.scope.launchAsync {
                val result = profiles.loadForJoin(player.uniqueId, player.name)
                sculk.scheduler.runSync(player) {
                    when (result) {
                        is SculkResult.Success -> player.reply("<green>Loaded profile for <yellow>${result.value.name}</yellow>.")
                        is SculkResult.Failure -> player.reply("<red>Could not load your profile: ${result.message}")
                    }
                }
            }
        }

        sculk.events.listen<PlayerQuitEvent> { event ->
            val uuid = event.player.uniqueId
            sculk.scope.launchAsync {
                profiles.saveAndUnload(uuid).logFailure("Failed to save profile $uuid")
            }
        }

        sculk.commands.register(profileCommand())
    }

    override fun shutdown() {
        if (::profiles.isInitialized) {
            kotlinx.coroutines.runBlocking { profiles.flushLoaded().logFailure("Failed to flush loaded profiles") }
        }
    }

    private fun profileCommand() =
        command("profile") {
            description = "Open a player profile."
            player("target", optional = true)
            executes {
                val target = argumentOrNull<Player>("target") ?: player
                if (target == null) {
                    reply("<red>Console must specify an online player.")
                    return@executes
                }
                openProfile(sender, target)
            }
            sub("reload") {
                permission = "profiles.reload"
                executes {
                    reply("<green>Profiles use live repository state; no config reload is required.")
                }
            }
        }

    private fun openProfile(
        sender: CommandSender,
        target: Player,
    ) {
        sculk.scope.launchAsync {
            val result = profiles.profile(target.uniqueId, target.name)
            sculk.scheduler.runSync(target) {
                when (result) {
                    is SculkResult.Success -> {
                        if (sender is Player) {
                            ProfileMenus.profile(result.value).openFor(sender)
                        } else {
                            sender.reply("<aqua>${result.value.name}</aqua> <gray>joins: <white>${result.value.joins}")
                        }
                    }
                    is SculkResult.Failure -> sender.reply("<red>${result.message}")
                }
            }
        }
    }

    private fun SculkResult<*>.logFailure(prefix: String) {
        if (this is SculkResult.Failure) logger.warning("$prefix: $message")
    }
}
