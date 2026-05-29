package studio.sculk.example

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.SculkResult
import studio.sculk.core.command.command
import studio.sculk.items.toItemStack
import studio.sculk.platform.SculkPlatform
import java.time.Duration

public class KitsPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform
    private lateinit var settings: KitSettings
    private lateinit var service: KitService
    private lateinit var menus: KitMenus

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                config()
                data()
                gui()
            }
        reloadKits()
        sculk.commands.register(kitCommand())
    }

    override fun onDisable() {
        if (::sculk.isInitialized) sculk.close()
    }

    private fun reloadKits() {
        settings = sculk.config.load()
        val repository = sculk.data.repository<KitCooldown, String>()
        val cached =
            sculk.data.cached(repository, KitCooldown::id) {
                ttl = Duration.ofMinutes(30)
                maxSize = 25_000
            }
        service = KitService({ settings }, cached)
        menus = KitMenus({ settings }, service) { kitId, context -> claimKit(context.player, kitId, bypassCooldown = false) }
        service.validate().forEach { logger.warning("[Kits] $it") }
    }

    private fun kitCommand() =
        command("kit") {
            description = "Claim and preview server kits."
            string("name", optional = true)
            player {
                val actor = player ?: return@player
                val kitId = argumentOrNull<String>("name")
                if (kitId == null) {
                    menus.list().openFor(actor)
                } else {
                    claimKit(actor, kitId, bypassCooldown = false)
                }
            }

            sub("preview") {
                string("name")
                player {
                    menus.preview(argument("name")).openFor(player ?: return@player)
                }
            }

            sub("give") {
                permission = "kits.admin"
                player("target")
                string("name")
                executes {
                    claimKit(argument("target"), argument("name"), bypassCooldown = true)
                    reply(
                        "<green>Gave kit <yellow>${argument<String>("name")}</yellow> to <aqua>${argument<Player>("target").name}</aqua>.",
                    )
                }
            }

            sub("reload") {
                permission = "kits.reload"
                executes {
                    reloadKits()
                    reply("<green>Kits reloaded.")
                }
            }
        }

    private fun claimKit(
        player: Player,
        kitId: String,
        bypassCooldown: Boolean,
    ) {
        val kitResult = service.kit(kitId)
        if (kitResult is SculkResult.Failure) {
            player.sendMessage(
                studio.sculk.core.adventure
                    .parseMessage("<red>${kitResult.message}"),
            )
            return
        }
        val kit = (kitResult as SculkResult.Success).value
        val permission = service.permissionFor(kitId, kit)
        if (!bypassCooldown && !player.hasPermission(permission)) {
            player.sendMessage(
                studio.sculk.core.adventure
                    .parseMessage("<red>You need <yellow>$permission</yellow>."),
            )
            return
        }

        sculk.scope.launchAsync {
            val status = if (bypassCooldown) SculkResult.success(KitClaimStatus(true, 0)) else service.claimStatus(player.uniqueId, kitId)
            sculk.scheduler.runSync(player) {
                when (status) {
                    is SculkResult.Failure ->
                        player.sendMessage(
                            studio.sculk.core.adventure
                                .parseMessage("<red>${status.message}"),
                        )
                    is SculkResult.Success -> {
                        if (!status.value.allowed) {
                            player.sendMessage(
                                studio.sculk.core.adventure.parseMessage(
                                    "<red>You can claim this kit in <yellow>${service.formatRemaining(
                                        status.value.remainingMillis,
                                    )}</yellow>.",
                                ),
                            )
                            return@runSync
                        }
                        val descriptors = service.kitItems(kitId)
                        if (descriptors is SculkResult.Failure) {
                            player.sendMessage(
                                studio.sculk.core.adventure
                                    .parseMessage("<red>${descriptors.message}"),
                            )
                            return@runSync
                        }
                        val stacks = (descriptors as SculkResult.Success).value.mapNotNull { it.toItemStack() }
                        val leftovers = stacks.flatMap { giveOrDrop(player, it) }
                        player.sendMessage(
                            studio.sculk.core.adventure
                                .parseMessage("<green>Claimed ${kit.displayName}<green>."),
                        )
                        if (leftovers.isNotEmpty()) {
                            player.sendMessage(
                                studio.sculk.core.adventure
                                    .parseMessage("<yellow>Your inventory was full, so leftovers were dropped."),
                            )
                        }
                        if (!bypassCooldown) {
                            sculk.scope.launchAsync {
                                service.recordClaim(player.uniqueId, kitId)
                            }
                        }
                    }
                }
            }
        }
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
