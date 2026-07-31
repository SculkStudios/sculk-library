package studio.sculk.example.hud

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import studio.sculk.command.command
import studio.sculk.hud.ActionBarPriority
import studio.sculk.hud.HudRow
import studio.sculk.platform.SculkPlugin
import studio.sculk.text.SculkTheme
import studio.sculk.text.ThemeStyle

/** Sidebar, action bar and placeholders, driven by the HUD's single task. */
class HudShowcasePlugin : SculkPlugin() {
    override val theme = SculkTheme(
        mapOf(
            "value" to ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")),
            "danger" to ThemeStyle.Solid("#ff5f5f"),
            "dim" to ThemeStyle.Solid("#6272a4"),
        ),
    )

    override fun setup() {
        // Registered once; resolved per viewer, and only for rows that mention them.
        sculk.hud.placeholders.register("ping") { it.ping.toString() }
        sculk.hud.placeholders.register("world") { it.world.name }
        sculk.hud.placeholders.register("online") { server.onlinePlayers.size.toString() }

        listen(
            object : Listener {
                @EventHandler
                fun onJoin(event: PlayerJoinEvent) {
                    sculk.hud.sidebar(event.player, "<value>Example</value>") {
                        listOf(
                            HudRow("<center><dim>— stats —</dim>"),
                            HudRow("Ping <value><ping>ms</value>"),
                            HudRow("World <value><world></value>"),
                            HudRow("Online <value><online></value>"),
                        )
                    }
                    sculk.hud.tabList(event.player, "<value>Example Server</value>", "<dim>have fun</dim>")
                }

                @EventHandler
                fun onQuit(event: PlayerQuitEvent) {
                    // Without this the sidebar and every action-bar message for a player who has
                    // ever joined is held for the server's uptime.
                    sculk.hud.forget(event.player)
                }
            },
        )

        +command("notify") {
            description = "Shows the action bar priority order."
            player {
                val viewer = player!!
                // The alert wins while it is live and expires back to the activity underneath it,
                // rather than the last writer simply overwriting.
                sculk.hud.actionBar(viewer, "<dim>mining…</dim>", ActionBarPriority.ACTIVITY, durationTicks = 200)
                sculk.hud.actionBar(viewer, "<danger>watch out</danger>", ActionBarPriority.ALERT, durationTicks = 40)
            }
        }
    }
}
