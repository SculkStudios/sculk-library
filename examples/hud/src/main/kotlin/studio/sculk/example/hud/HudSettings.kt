package studio.sculk.example.hud

import kotlinx.serialization.Serializable
import studio.sculk.config.Comment
import studio.sculk.config.NotBlank
import studio.sculk.config.Range
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup
import java.time.Duration
import java.util.Locale

@Serializable
data class HudSettings(
    @Comment("The sidebar's title and the tab list's header.")
    @NotBlank
    val title: String = "<gradient:#7f5af0:#2cb67d><b>Sculk Network</b></gradient>",
    @Comment("Shown at the bottom of the sidebar and the tab list.")
    val address: String = "play.sculk.studio",
    @Comment(
        "Ranks, highest first. A player has the first rank whose permission (hud.rank.<name>) they hold,",
        "and the last one otherwise.",
    )
    val ranks: List<Rank> = listOf(Rank("admin", "<red>Admin"), Rank("vip", "<gold>VIP"), Rank("member", "<gray>Member")),
    @Comment("How long the event boss bar counts down, in minutes, from when the server starts.")
    @Range(min = 1, max = 1440)
    val eventMinutes: Int = 30,
    @Comment("The title a player sees when they join; <player_name> is their name.")
    val welcome: String = "<primary>Welcome, <player_name>",
)

@Serializable
data class Rank(val name: String, val display: String)

object HudPermissions : PermissionGroup("hud") {
    val toggle = permission("toggle", "Turn your own HUD on and off", PermissionDefault.EVERYONE)
}

/** The first rank whose permission [has] says the player holds, or the last rank. */
fun rankOf(ranks: List<Rank>, has: (String) -> Boolean): Rank? = ranks.firstOrNull { has("hud.rank.${it.name}") } ?: ranks.lastOrNull()

/** What is left of the event as `m:ss`, or `ended`. */
fun countdown(left: Duration): String =
    if (left.isNegative || left.isZero) "ended" else String.format(Locale.ROOT, "%d:%02d", left.toMinutes(), left.toSecondsPart())
