package studio.sculk.example.network

import kotlinx.serialization.Serializable
import studio.sculk.config.Comment
import studio.sculk.config.NotBlank
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup

@Serializable
data class Settings(
    @Comment("The server /hub sends players to. It must be one of the servers in velocity.toml.")
    @NotBlank
    val hub: String = "lobby",
    @Comment("While maintenance is on, only players with network.maintenance.bypass can join.")
    val maintenance: MaintenanceSettings = MaintenanceSettings(),
)

@Serializable
data class MaintenanceSettings(
    @Comment("Whether maintenance is on. /maintenance changes this and saves it.")
    val enabled: Boolean = false,
    @Comment("Shown to players who are turned away or disconnected, in MiniMessage.")
    @NotBlank
    val message: String = "<warning>The network is down for maintenance. Back soon!",
    @Comment("The server list description while maintenance is on, in MiniMessage.")
    @NotBlank
    val motd: String = "<warning>Down for maintenance",
)

object NetworkPermissions : PermissionGroup("network") {
    val hub = permission("hub", "Go to the hub server", PermissionDefault.EVERYONE)
    val find = permission("find", "See which server a player is on", PermissionDefault.EVERYONE)
    val maintenance = permission("maintenance", "Turn maintenance mode on and off")
    val bypass = permission("maintenance.bypass", "Join and stay while maintenance is on")
}
