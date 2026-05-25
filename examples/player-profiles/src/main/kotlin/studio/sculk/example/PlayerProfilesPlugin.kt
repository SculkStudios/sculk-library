package studio.sculk.example

import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.command.command
import studio.sculk.platform.SculkPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public class PlayerProfilesPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform
    private val profiles = ConcurrentHashMap<UUID, PlayerProfile>()

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                gui()
            }

        sculk.events.listen<PlayerJoinEvent> { event ->
            sculk.scheduler.runAsync {
                val profile = profiles.computeIfAbsent(event.player.uniqueId) { PlayerProfile(it, event.player.name) }
                sculk.scheduler.runSync(event.player) {
                    event.player.sendMessage("Loaded profile for ${profile.name}.")
                }
            }
        }

        sculk.events.listen<PlayerQuitEvent> { event ->
            profiles[event.player.uniqueId]?.lastSeen = System.currentTimeMillis()
        }

        sculk.commands.register(
            command("profile") {
                player {
                    val profile = profiles.computeIfAbsent(player!!.uniqueId) { PlayerProfile(it, player!!.name) }
                    reply("<aqua>${profile.name}</aqua> <gray>Kills: <white>${profile.kills}</white>")
                }
            },
        )
    }

    override fun onDisable() {
        profiles.values.forEach { it.lastSeen = System.currentTimeMillis() }
        sculk.close()
    }
}

public data class PlayerProfile(
    val uuid: UUID,
    val name: String,
    var kills: Int = 0,
    var deaths: Int = 0,
    var lastSeen: Long = System.currentTimeMillis(),
)
