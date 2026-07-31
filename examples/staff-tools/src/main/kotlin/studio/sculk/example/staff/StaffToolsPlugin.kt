package studio.sculk.example.staff

import org.bukkit.Material
import studio.sculk.command.command
import studio.sculk.fold
import studio.sculk.platform.SculkPlugin
import studio.sculk.text.SculkTheme
import studio.sculk.text.ThemeStyle
import java.time.Duration

/** Subcommands, permissions, cooldowns, middleware, generated help and client-side blocks. */
class StaffToolsPlugin : SculkPlugin() {
    override val theme = SculkTheme(
        mapOf(
            "value" to ThemeStyle.Solid("#8be9fd"),
            "danger" to ThemeStyle.Solid("#ff5f5f"),
            "dim" to ThemeStyle.Solid("#6272a4"),
        ),
    )

    private val modes = mutableSetOf<String>()

    override fun setup() {
        +command("staff") {
            description = "Staff tools."
            permission = "example.staff"
            aliases = listOf("st")

            sub("vanish") {
                description = "Toggles vanish."
                // Both are declared: a console sender reaches the console branch rather than being
                // told the command is for players only.
                player {
                    val viewer = player!!
                    val nowVanished = modes.add(viewer.name) || run {
                        modes.remove(viewer.name)
                        false
                    }
                    reply(if (nowVanished) "<value>Vanished.</value>" else "<dim>Visible.</dim>")
                }
                console {
                    reply("<dim>Vanish is a player command; nothing to hide from here.</dim>")
                }
            }

            sub("highlight") {
                description = "Shows you a block only you can see."
                cooldown(Duration.ofSeconds(3))
                player {
                    val viewer = player!!
                    val target = viewer.getTargetBlockExact(20)
                    if (target == null) {
                        reply("<danger>Look at a block first.</danger>")
                        return@player
                    }
                    // Degrades by name rather than failing: a server with no packet backend still
                    // runs this command and says why it did nothing.
                    sculk.packets.fold(
                        { service ->
                            service.clientBlocks.preview(viewer, target.location, Material.GLOWSTONE, durationTicks = 60)
                            reply("<value>Highlighted for three seconds.</value>")
                        },
                        { message, _ -> reply("<danger><why></danger>", "why" to message) },
                    )
                }
            }

            sub("say") {
                description = "Broadcasts a message."
                permission = "example.staff.say"
                greedy("message")
                // Runs before the executor; returning false aborts and is responsible for saying why.
                middleware { context ->
                    val text: String = context.argument("message")
                    if (text.length <= 200) {
                        true
                    } else {
                        context.reply("<danger>That is too long.</danger>")
                        false
                    }
                }
                executes {
                    // The message is a placeholder value, so a staff member cannot inject markup
                    // into everyone's chat through it.
                    val text: String = argument("message")
                    server.onlinePlayers.forEach { sculk.messages.send(it, "<dim>[staff]</dim> <text>", "text" to text) }
                }
            }
        }
    }
}
