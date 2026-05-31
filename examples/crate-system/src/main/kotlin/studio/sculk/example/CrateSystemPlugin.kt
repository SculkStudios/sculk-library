package studio.sculk.example

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import studio.sculk.SculkResult
import studio.sculk.adventure.broadcast
import studio.sculk.adventure.parseMessage
import studio.sculk.command.command
import studio.sculk.effects.sound
import studio.sculk.items.toItemStack
import studio.sculk.platform.SculkPlugin

public class CrateSystemPlugin :
    SculkPlugin({
        config()
        gui()
    }) {
    private lateinit var settings: CrateSettings
    private lateinit var crates: CrateService
    private lateinit var menus: CrateMenus

    override fun setup() {
        reloadCrates()
        sculk.commands.register(crateCommand())
    }

    private fun reloadCrates() {
        settings = sculk.config.load()
        crates = CrateService(settings = { settings })
        menus = CrateMenus(crates)
        crates.validate().forEach { logger.warning("[Crates] $it") }
    }

    private fun crateCommand() =
        command("crate") {
            description = "Preview crates, give keys, and open rewards."

            sub("preview") {
                string("crate")
                player {
                    val crateId = argument<String>("crate")
                    menus.preview(crateId).openFor(player ?: return@player)
                }
            }

            sub("open") {
                string("crate")
                player {
                    openCrate(player ?: return@player, argument("crate"))
                }
            }

            sub("key") {
                sub("give") {
                    permission = "crate.admin"
                    player("target")
                    string("crate")
                    int("amount", min = 1, max = 64)
                    executes {
                        val target = argument<Player>("target")
                        val crateId = argument<String>("crate")
                        val amount = argument<Int>("amount")
                        when (val key = crates.keyItem(crateId)) {
                            is SculkResult.Success -> {
                                key.value.amount = amount
                                giveOrDrop(target, key.value)
                                reply("<green>Gave <yellow>$amount</yellow> keys to <aqua>${target.name}</aqua>.")
                            }
                            is SculkResult.Failure -> reply("<red>${key.message}")
                        }
                    }
                }
            }

            sub("reload") {
                permission = "crate.admin"
                executes {
                    reloadCrates()
                    reply("<green>Crates reloaded.")
                }
            }
        }

    private fun openCrate(
        player: Player,
        crateId: String,
    ) {
        if (!consumeKey(player, crateId)) {
            player.sendMessage(parseMessage("<red>You need a key for this crate."))
            return
        }
        when (val rolled = crates.roll(crateId)) {
            is SculkResult.Success -> {
                val item = rolled.value.item.toItemStack()
                if (item == null) {
                    player.sendMessage(
                        parseMessage("<red>Reward '${rolled.value.id}' has an invalid item."),
                    )
                    return
                }
                giveOrDrop(player, item)
                sound(Sound.ENTITY_PLAYER_LEVELUP) {
                    volume = 0.8f
                    pitch = 1.2f
                }.playAt(
                    player.location,
                )
                player.sendMessage(parseMessage("<green>You won <yellow>${rolled.value.id}</yellow>."))
                if (rolled.value.broadcast) {
                    broadcast(
                        "<gold>${player.name}</gold> <gray>won <yellow>${rolled.value.id}</yellow> from a crate.",
                    )
                }
            }
            is SculkResult.Failure ->
                player.sendMessage(
                    parseMessage("<red>${rolled.message}"),
                )
        }
    }

    private fun consumeKey(
        player: Player,
        crateId: String,
    ): Boolean {
        val inventory = player.inventory
        for (index in 0 until inventory.size) {
            val stack = inventory.getItem(index)
            if (!crates.isCrateKey(stack, crateId)) continue
            if (stack!!.amount > 1) {
                stack.amount -= 1
            } else {
                inventory.setItem(index, null)
            }
            return true
        }
        return false
    }

    private fun giveOrDrop(
        player: Player,
        stack: ItemStack,
    ): List<ItemStack> {
        val leftovers =
            player.inventory
                .addItem(stack)
                .values
                .toList()
        leftovers.forEach { player.world.dropItemNaturally(player.location, it) }
        return leftovers
    }
}
