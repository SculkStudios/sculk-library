package studio.sculk.itest.bot

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.geysermc.mcprotocollib.network.Session
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/**
 * A headless client for the server suites (ADR-0029). It joins `host port` as `name`, prints what
 * it sees as one tab-separated event per line on stdout, and acts on one command per line on stdin:
 *
 * - events: `JOINED`, `WINDOW id type title`, `CONTENT id state slot:name...`, `SLOT id state slot name`,
 *   `CLOSED id`, `CHAT text`, `DISCONNECTED reason`;
 * - commands: `CLICK slot LEFT|RIGHT|SHIFT|DROP|HOTBAR n`, `CLOSE`, `COMMAND text`, `QUIT`.
 *
 * A slot's name is its item name or custom name in plain text, `#id` for an unnamed item, and the
 * slot is left out when it is empty. Only the open window's state is tracked.
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "usage: host port name" }
    val out = PrintStream(System.out, true, Charsets.UTF_8)
    val bot = Bot(out)
    bot.connect(args[0], args[1].toInt(), args[2])
    System.`in`.bufferedReader(Charsets.UTF_8).lineSequence().forEach { line ->
        if (!bot.command(line)) {
            bot.quit()
            exitProcess(0)
        }
    }
    bot.quit()
    exitProcess(0)
}

private class Bot(private val out: PrintStream) {
    private val plain = PlainTextComponentSerializer.plainText()
    private lateinit var session: ClientNetworkSession
    private val gone = CountDownLatch(1)

    @Volatile private var window = 0

    @Volatile private var stateId = 0

    fun connect(host: String, port: Int, name: String) {
        session = ClientNetworkSessionFactory.factory().setAddress(host, port).setProtocol(MinecraftProtocol(name)).create()
        session.addListener(
            object : SessionAdapter() {
                override fun packetReceived(session: Session, packet: Packet) = received(packet)

                override fun disconnected(event: DisconnectedEvent) {
                    emit("DISCONNECTED", plain.serialize(event.reason))
                    gone.countDown()
                }
            },
        )
        session.connect(true)
    }

    private fun received(packet: Packet) {
        when (packet) {
            is ClientboundLoginPacket -> emit("JOINED")
            is ClientboundPlayerPositionPacket -> {
                val at = packet.position
                session.send(ServerboundAcceptTeleportationPacket(packet.id, at.x, at.y, at.z, packet.yRot, packet.xRot))
                session.send(ServerboundPlayerLoadedPacket.INSTANCE)
            }
            is ClientboundOpenScreenPacket -> {
                window = packet.containerId
                emit("WINDOW", packet.containerId.toString(), packet.type.name, plain.serialize(packet.title))
            }
            is ClientboundContainerSetContentPacket -> if (packet.containerId == window) {
                stateId = packet.stateId
                val slots = packet.items.withIndex().mapNotNull { (slot, item) -> name(item)?.let { "$slot:$it" } }
                emit("CONTENT", packet.containerId.toString(), packet.stateId.toString(), *slots.toTypedArray())
            }
            is ClientboundContainerSetSlotPacket -> if (packet.containerId == window) {
                stateId = packet.stateId
                emit("SLOT", packet.containerId.toString(), packet.stateId.toString(), packet.slot.toString(), name(packet.item) ?: "")
            }
            is ClientboundContainerClosePacket -> emit("CLOSED", packet.containerId.toString())
            is ClientboundSystemChatPacket -> if (!packet.isOverlay) emit("CHAT", plain.serialize(packet.content))
        }
    }

    /** Runs one command; false means quit. */
    fun command(line: String): Boolean {
        val parts = line.split('\t')
        when (parts[0]) {
            "CLICK" -> click(parts[1].toInt(), parts[2], parts.getOrNull(3)?.toInt())
            "CLOSE" -> session.send(ServerboundContainerClosePacket(window))
            "COMMAND" -> session.send(ServerboundChatCommandPacket(parts[1].removePrefix("/")))
            "QUIT" -> return false
            else -> emit("ERROR", "unknown command '${parts[0]}'")
        }
        return true
    }

    // The carried item and changed slots are the client's prediction; sending none makes the
    // server resynchronise the window after applying the click, which is what a test wants to see.
    private fun click(slot: Int, kind: String, hotbar: Int?) {
        val (type, action) = when (kind) {
            "LEFT" -> ContainerActionType.CLICK_ITEM to ClickItemAction.LEFT_CLICK
            "RIGHT" -> ContainerActionType.CLICK_ITEM to ClickItemAction.RIGHT_CLICK
            "SHIFT" -> ContainerActionType.SHIFT_CLICK_ITEM to ShiftClickItemAction.LEFT_CLICK
            "DROP" -> ContainerActionType.DROP_ITEM to DropItemAction.DROP_FROM_SELECTED
            "HOTBAR" -> ContainerActionType.MOVE_TO_HOTBAR_SLOT to MoveToHotbarAction.from(requireNotNull(hotbar) { "HOTBAR needs a slot" })
            else -> error("unknown click '$kind'")
        }
        session.send(ServerboundContainerClickPacket(window, stateId, slot, type, action, null as HashedStack?, emptyMap<Int, HashedStack>()))
    }

    private fun name(item: ItemStack?): String? {
        if (item == null || item.amount <= 0) return null
        val components = item.dataComponentsPatch
        val named = components?.get(DataComponentTypes.ITEM_NAME) ?: components?.get(DataComponentTypes.CUSTOM_NAME)
        return named?.let { plain.serialize(it) } ?: "#${item.id}"
    }

    fun quit() {
        if (gone.count > 0) session.disconnect(Component.text("done"))
    }

    private fun emit(vararg fields: String) {
        out.println(fields.joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') })
    }
}
