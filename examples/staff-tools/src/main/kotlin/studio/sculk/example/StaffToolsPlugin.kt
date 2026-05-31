package studio.sculk.example

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import studio.sculk.adventure.parseMessage
import studio.sculk.adventure.reply
import studio.sculk.command.command
import studio.sculk.effects.particle
import studio.sculk.effects.sound
import studio.sculk.platform.SculkPlugin

public class StaffToolsPlugin : SculkPlugin({ gui() }) {
    private val staff = StaffToolsService()

    override fun setup() {
        sculk.events.listen<PlayerMoveEvent> { event ->
            if (!staff.shouldBlockMovement(event.player.uniqueId)) return@listen
            val from = event.from
            val to = event.to
            if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
                event.isCancelled = true
                event.player.sendActionBar(parseMessage("<red>You are frozen."))
            }
        }

        sculk.events.listen<PlayerQuitEvent> { event ->
            staff.cleanup(event.player.uniqueId)
        }

        sculk.commands.registerAll(staffCommand(), freezeCommand(), inspectCommand(), staffChatCommand())
    }

    private fun staffCommand() =
        command("staff") {
            permission = "staff.mode"
            description = "Toggle staff mode."
            player {
                val actor = player ?: return@player
                val session = staff.toggleStaffMode(actor.uniqueId)
                val state = if (session.enabled) "<green>enabled" else "<red>disabled"
                reply("<gray>Staff mode $state<gray>.")
                sound(Sound.BLOCK_NOTE_BLOCK_PLING) {
                    volume = 0.8f
                    pitch = if (session.enabled) 1.4f else 0.7f
                }.playAt(actor.location)
                particle(Particle.HAPPY_VILLAGER) {
                    location = actor.location.clone().add(0.0, 1.0, 0.0)
                    count = 8
                    offset(0.3, 0.5, 0.3)
                }.spawn()
            }
        }

    private fun freezeCommand() =
        command("freeze") {
            permission = "staff.freeze"
            description = "Freeze or unfreeze a player."
            player("target")
            string("reason", optional = true)
            player {
                val actor = player ?: return@player
                val target = argument<Player>("target")
                val reason = argumentOrNull<String>("reason") ?: "No reason provided."
                val removed = staff.unfreeze(target.uniqueId)
                if (removed) {
                    reply("<green>Unfroze <yellow>${target.name}</yellow>.")
                    target.reply("<green>You have been unfrozen.")
                } else {
                    staff.freeze(actor.uniqueId, target.uniqueId, reason)
                    reply("<red>Froze <yellow>${target.name}</yellow>: <white>$reason")
                    target.reply("<red>You have been frozen. Reason: <white>$reason")
                    logger.info("${actor.uniqueId} froze ${target.uniqueId}: $reason")
                }
            }
        }

    private fun inspectCommand() =
        command("inspect") {
            permission = "staff.inspect"
            description = "Open a read-only player inspection menu."
            player("target")
            player {
                StaffMenus.inspect(argument("target")).openFor(player ?: return@player)
            }
        }

    private fun staffChatCommand() =
        command("staffchat") {
            permission = "staff.chat"
            description = "Send a message to online staff."
            greedy("message")
            player {
                val actor = player ?: return@player
                val message = argument<String>("message")
                val viewers =
                    Bukkit.getOnlinePlayers().map {
                        StaffViewer(it.uniqueId, it.name, it.hasPermission("staff.chat"))
                    }
                val recipients =
                    staff
                        .staffChatRecipients(viewers, actor.uniqueId)
                        .mapNotNull { Bukkit.getPlayer(it.uuid) }
                recipients.forEach {
                    it.reply(
                        "<dark_gray>[<aqua>Staff</aqua>]</dark_gray> " +
                            "<yellow>${actor.name}</yellow>: <white>$message",
                    )
                }
            }
        }
}
