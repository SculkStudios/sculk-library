package studio.sculk.example.hud

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import studio.sculk.command.command
import studio.sculk.command.paper.commands
import studio.sculk.config.ConfigFile
import studio.sculk.config.SculkConfig
import studio.sculk.event.listen
import studio.sculk.getOrThrow
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.placeholder.Refresh
import studio.sculk.presentation.HudLayout
import studio.sculk.presentation.Huds
import studio.sculk.presentation.Update
import studio.sculk.presentation.hud
import studio.sculk.text.Text
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

/**
 * A network HUD on Sculk: a sidebar, rank nametags, health under every name, a tab list and an
 * event boss bar, all drawn from placeholders and redrawn only when a value they show changes. On
 * Folia, which has no scoreboards, it keeps the boss bar and tab list and leaves the rest out.
 */
class HudPlugin : SculkPaperPlugin() {
    private lateinit var settings: ConfigFile<HudSettings>
    private lateinit var huds: Huds
    private lateinit var layout: HudLayout
    private val hidden by lazy { NamespacedKey(this, "hidden") }
    private val started = Instant.now()

    override suspend fun start() {
        settings = SculkConfig.load<HudSettings>(dataPath.resolve("config.yml")) {
            header = listOf("HUD example for Sculk Studio.")
        }.getOrThrow()
        sculk.permissions.register(HudPermissions)
        val text = Text()

        val placeholders = PlaceholderRegistry(sculk.scope)
        val online = placeholders.global("server:online", Refresh.UntilInvalidated) { Bukkit.getOnlinePlayers().size }
        val health = placeholders.audience("player:health", Refresh.UntilInvalidated) {
            (audience as? Player)?.let { ceil(it.health).toInt() }
        }
        placeholders.audience("player:name") { (audience as? Player)?.name }
        // Read when the HUD is shown. Paper has no event for a permission change; a permissions
        // plugin's own change event would call rank.invalidate(player).
        val rank = placeholders.audience("player:rank", Refresh.UntilInvalidated) {
            (audience as? Player)?.let { player -> rankOf(settings.value.ranks, player::hasPermission)?.let { text.render(it.display) } }
        }
        placeholders.global("event:left", Refresh.Every(1.seconds)) { countdown(eventLeft()) }
        // A value from the config enters templates as a placeholder, never pasted into them.
        placeholders.global("server:address") { settings.value.address }

        huds = Huds(this, sculk.scope.resources, placeholders, text)
        layout = layout(settings.value, huds.sidebarAvailable)

        sculk.events.listen<PlayerJoinEvent> { event ->
            online.invalidate()
            val player = event.player
            huds.title(player, settings.value.welcome, "<muted><server_address>")
            if (player.persistentDataContainer.get(hidden, PersistentDataType.BOOLEAN) != true) huds.show(player, layout).getOrThrow()
        }
        sculk.events.listen<PlayerQuitEvent> { online.invalidate() }
        sculk.events.listen<EntityDamageEvent> { event -> (event.entity as? Player)?.let(health::invalidate) }
        sculk.events.listen<EntityRegainHealthEvent> { event -> (event.entity as? Player)?.let(health::invalidate) }

        sculk.commands().register(
            command("hud") {
                description = "Turn your HUD on or off"
                permission(HudPermissions.toggle)
                player {
                    val player = source.uniqueId?.let(Bukkit::getPlayer) ?: fail("<danger>Only players have a HUD.")
                    val data = player.persistentDataContainer
                    if (huds.hud(player) == null) {
                        data.remove(hidden)
                        rank.invalidate(player)
                        huds.show(player, layout).getOrThrow()
                        reply("<success>Your HUD is on.")
                    } else {
                        data.set(hidden, PersistentDataType.BOOLEAN, true)
                        huds.hud(player)?.close()
                        reply("<muted>Your HUD is off. <primary>/hud</primary> brings it back.")
                    }
                }
            },
        )
    }

    private fun eventLeft(): Duration =
        Duration.between(Instant.now(), started.plus(Duration.ofMinutes(settings.value.eventMinutes.toLong())))

    private fun eventProgress(): Float =
        eventLeft().toMillis().toFloat() / Duration.ofMinutes(settings.value.eventMinutes.toLong()).toMillis()

    // Folia cannot draw scoreboards, so the layout there keeps only what works everywhere.
    private fun layout(settings: HudSettings, scoreboards: Boolean): HudLayout = hud {
        tabList(header = settings.title, footer = "<muted>Online: <value><server_online></value> · <server_address>")
        bar("event", "<warning>Event ends in <event_left>", progress = { eventProgress() }, update = Update.Every(20))
        if (scoreboards) {
            sidebar(
                settings.title,
                "",
                "<muted>Rank: <player_rank>",
                "<muted>Online: <value><server_online>",
                "",
                "<muted>Event: <warning><event_left>",
                "",
                "<primary><server_address>",
            )
            nametag(prefix = "<player_rank> <gray>")
            belowName("<red><player_health> ❤")
        }
    }
}
