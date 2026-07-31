package studio.sculk.example.visual

import org.bukkit.Particle
import studio.sculk.command.command
import studio.sculk.fold
import studio.sculk.platform.SculkPlugin
import studio.sculk.text.SculkTheme
import studio.sculk.text.ThemeStyle
import studio.sculk.visual.HologramOptions
import studio.sculk.visual.particle

/** Holograms and particles, both packet-only: nothing here is a server entity. */
class VisualShowcasePlugin : SculkPlugin() {
    override val theme = SculkTheme(
        mapOf(
            "value" to ThemeStyle.Gradient(listOf("#50fa7b", "#8be9fd")),
            "danger" to ThemeStyle.Solid("#ff5f5f"),
            "dim" to ThemeStyle.Solid("#6272a4"),
        ),
    )

    private var counter = 0

    override fun setup() {
        +command("hologram") {
            description = "Places a hologram that counts up."
            player {
                if (!sculk.holograms.available) {
                    reply("<danger>No packet backend is loaded.</danger>")
                    return@player
                }
                val viewer = player!!
                val hologram = sculk.holograms.create(
                    viewer.location,
                    listOf("<value>Example</value>", "<dim>seen 0 times</dim>"),
                    HologramOptions(viewRangeBlocks = 32.0, yOffset = 1.5),
                )
                // Only re-sends when the text actually changes, so a ticking hologram is not a
                // metadata packet per tick per viewer.
                sculk.tasks.repeating(intervalTicks = 20) {
                    counter++
                    hologram.setLines(listOf("<value>Example</value>", "<dim>seen <count> times</dim>".replace("<count>", "$counter")))
                }
                reply("<value>Placed.</value>")
            }
        }

        +command("sparkle") {
            description = "Spawns particles at your feet."
            player {
                val viewer = player!!
                particle(Particle.HAPPY_VILLAGER) {
                    location = viewer.location
                    count = 30
                    offset(0.5, 0.5, 0.5)
                }.spawn()
                reply("<value>Sparkled.</value>")
            }
        }
    }
}
