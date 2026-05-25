package studio.sculk.example

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.command.command
import studio.sculk.effects.particle
import studio.sculk.effects.sound
import studio.sculk.platform.SculkPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public class StaffToolsPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform
    private val staffMode = ConcurrentHashMap.newKeySet<UUID>()

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {}

        sculk.commands.register(
            command("staff") {
                permission = "staff.mode"
                player {
                    val enabled =
                        if (staffMode.remove(player!!.uniqueId)) {
                            false
                        } else {
                            staffMode += player!!.uniqueId
                            true
                        }
                    val state = if (enabled) "<green>enabled" else "<red>disabled"
                    reply("<gray>Staff mode $state<gray>.")
                    sound(Sound.BLOCK_NOTE_BLOCK_PLING) {
                        volume = 0.8f
                        pitch = if (enabled) 1.4f else 0.7f
                    }.playAt(player!!.location)
                    particle(Particle.HAPPY_VILLAGER) {
                        location = player!!.location.add(0.0, 1.0, 0.0)
                        count = 8
                        offset(0.3, 0.5, 0.3)
                    }.spawn()
                }
            },
        )
    }

    override fun onDisable() {
        staffMode.clear()
        sculk.close()
    }
}
