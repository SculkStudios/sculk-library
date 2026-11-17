package studio.sculk.example.staff

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import studio.sculk.command.command
import studio.sculk.command.paper.commands
import studio.sculk.config.ConfigFile
import studio.sculk.config.SculkConfig
import studio.sculk.config.admin.ConfigAdmin
import studio.sculk.dialog.Dialogs
import studio.sculk.dialog.dialog
import studio.sculk.event.listen
import studio.sculk.getOrThrow
import studio.sculk.inventory.MenuLayout
import studio.sculk.inventory.MenuSpec
import studio.sculk.inventory.Menus
import studio.sculk.inventory.menu
import studio.sculk.inventory.textPrompt
import studio.sculk.item.item
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.text.Text
import java.util.UUID

/**
 * Staff tools on Sculk: `/staff` lists online players in a menu, and each has actions: freeze,
 * teleport, a note typed in an anvil, and removal through a form, which is a dialog where the
 * server has them and a menu of reasons where it does not. `/staff config` edits the settings in
 * game. Everything runs on the thread that owns the player it touches, so it works on Folia.
 */
class StaffPlugin : SculkPaperPlugin() {
    private lateinit var settings: ConfigFile<StaffSettings>
    private lateinit var menus: Menus
    private lateinit var dialogs: Dialogs
    private val staff = Staff()
    private val messages = Text()

    private data class Page(val number: Int = 0)

    private data class Target(val id: UUID)

    override suspend fun start() {
        settings = SculkConfig.load<StaffSettings>(dataPath.resolve("config.yml")) {
            header = listOf("Staff tools example for Sculk Studio.")
        }.getOrThrow()
        sculk.permissions.register(StaffPermissions)
        menus = Menus(this, sculk.scope.resources)
        dialogs = Dialogs(this, messages)
        val admin = ConfigAdmin(this, dialogs, menus, messages)

        sculk.events.listen<PlayerMoveEvent> { event ->
            if (event.hasChangedPosition() && staff.isFrozen(event.player.uniqueId)) event.isCancelled = true
        }

        sculk.commands().register(
            command("staff") {
                description = "Open the staff menu"
                permission(StaffPermissions.use)
                player {
                    val player = source.uniqueId?.let(Bukkit::getPlayer) ?: fail("<danger>Only players can use the staff menu.")
                    menus.open(player, players, Page())
                }
                literal("config") {
                    description = "Change the staff settings in game"
                    permission(StaffPermissions.config)
                    player {
                        val player = source.uniqueId?.let(Bukkit::getPlayer) ?: fail("<danger>Only players can edit settings.")
                        admin.open(player, settings, "Staff settings").getOrThrow()
                    }
                }
            },
        )
    }

    private val back = item(Material.ARROW) { name = "<muted>Back" }

    private val players: MenuSpec<Page> = menu(MenuLayout.chest(6)) {
        text = messages
        title("<primary>Online players")
        render { state ->
            val online = Bukkit.getOnlinePlayers().sortedBy { it.name.lowercase() }
            val page = paginate((0 until PAGE_SIZE).toList(), online, state.number, icon = { target ->
                item(Material.PLAYER_HEAD) {
                    name = "<primary><player>"
                    value("player", target.name)
                    lore(if (staff.isFrozen(target.uniqueId)) "<danger>Frozen" else "<muted>Click for actions")
                }
            }) { target -> open(actions, Target(target.uniqueId)) }
            if (page.hasPrevious) {
                slot(PREVIOUS, item(Material.ARROW) { name = "<muted>Previous page" }) {
                    update {
                        it.copy(
                            number =
                            it.number - 1,
                        )
                    }
                }
            }
            if (page.hasNext) slot(NEXT, item(Material.ARROW) { name = "<muted>Next page" }) { update { it.copy(number = it.number + 1) } }
        }
    }

    private val actions: MenuSpec<Target> = menu(MenuLayout.chest(3)) {
        text = messages
        title("<primary>Player actions")
        render { state ->
            val target = Bukkit.getPlayer(state.id)
            if (target == null) {
                slot(CENTRE, item(Material.BARRIER) { name = "<muted>That player has left" })
                slot(BACK, back) { open(players, Page()) }
                return@render
            }
            val frozen = staff.isFrozen(target.uniqueId)
            slot(
                FREEZE,
                item(if (frozen) Material.BLUE_ICE else Material.ICE) {
                    name =
                        if (frozen) "<warning>Unfreeze" else "<primary>Freeze"
                },
            ) {
                val now = staff.toggleFreeze(target.uniqueId)
                if (now) {
                    target.sendMessage(
                        messages.render(settings.value.freezeMessage),
                    )
                } else {
                    target.sendMessage(messages.render("<success>You can move again."))
                }
                update { it }
            }
            slot(TELEPORT, item(Material.ENDER_PEARL) { name = "<primary>Teleport to them" }) {
                val staffMember = player
                close()
                // Their location belongs to their region; the teleport to the staff member's.
                target.scheduler.run(this@StaffPlugin, { _ ->
                    val there = target.location
                    staffMember.scheduler.run(this@StaffPlugin, { _ -> staffMember.teleportAsync(there) }, null)
                }, null)
            }
            val last = staff.notes(target.uniqueId).lastOrNull()
            slot(
                NOTE,
                item(Material.WRITABLE_BOOK) {
                    name = "<primary>Add a note"
                    lore(if (last == null) "<muted>No notes yet" else "<muted>Last: <note>")
                    last?.let { value("note", "${it.text} (${it.author})") }
                },
            ) { menus.prompt(player, notePrompt(target.uniqueId)) }
            slot(REMOVE, item(Material.IRON_DOOR) { name = "<danger>Remove from the server" }) {
                close()
                dialogs.show(player, removal(target.uniqueId)).getOrThrow()
            }
            slot(BACK, back) { open(players, Page()) }
        }
    }

    private fun notePrompt(target: UUID) = textPrompt("Note about this player") {
        text = messages
        check { note -> if (note.isBlank()) "<danger>Write something first" else null }
        onAnswer { player, note ->
            staff.note(target, Note(player.name, note))
            player.sendMessage(messages.render("<success>Note saved."))
        }
    }

    private fun removal(target: UUID) = dialog("<danger>Remove a player") {
        body("Pick a reason. The player is disconnected with it at once.")
        choice("reason", "Reason", settings.value.reasons.map { it to it }, initial = settings.value.reasons.firstOrNull())
        toggle("announce", "Tell everyone", initial = settings.value.announce)
        button("<danger>Remove") { answer ->
            val reason = answer.choice("reason") ?: return@button
            remove(answer.player, target, reason, answer.toggle("announce") ?: settings.value.announce)
        }
        button("Cancel")
        fallback { player -> menus.open(player, reasons(target), Unit) }
    }

    private fun reasons(target: UUID): MenuSpec<Unit> = menu(MenuLayout.chest(3)) {
        text = messages
        title("<danger>Remove for…")
        render {
            paginate((0 until 18).toList(), settings.value.reasons, 0, icon = { reason ->
                item(Material.PAPER) {
                    name = "<danger><reason>"
                    value("reason", reason)
                }
            }) { reason ->
                close()
                remove(player, target, reason, settings.value.announce)
            }
        }
    }

    // The kick runs on the target's own thread; the announcement goes to every player.
    private fun remove(by: Player, target: UUID, reason: String, announce: Boolean) {
        val player = Bukkit.getPlayer(target) ?: return by.sendMessage(messages.render("<muted>That player has already left."))
        player.scheduler.run(this, { _ -> player.kick(messages.render(settings.value.kickMessage, "reason" to reason)) }, null)
        if (announce) {
            Bukkit.getServer().sendMessage(
                messages.render(
                    "<warning><player> was removed: <reason>",
                    "player" to player.name,
                    "reason" to reason,
                ),
            )
        }
        by.sendMessage(messages.render("<success>Removed <player>.", "player" to player.name))
    }

    private companion object {
        const val PAGE_SIZE = 45
        const val PREVIOUS = 45
        const val NEXT = 53
        const val FREEZE = 10
        const val TELEPORT = 12
        const val CENTRE = 13
        const val NOTE = 14
        const val REMOVE = 16
        const val BACK = 22
    }
}
