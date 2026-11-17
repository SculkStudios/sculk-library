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
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket
import org.geysermc.mcprotocollib.protocol.data.game.BossBarAction
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ObjectiveAction
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamAction
import org.geysermc.mcprotocollib.protocol.data.game.chat.numbers.FixedFormat
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTabListPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundResetScorePacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundUpdateTagsPacket
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket
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
 *   `CLOSED id`, `CHAT text`, `DIALOG title buttons inputs` (labels and `key:type`, each `|`-separated),
 *   `DIALOGCLOSED`, `DIALOGTAG tag dialogs` (`,`-separated keys), `ACTIONBAR text`, `BOSSBAR uuid ADD|REMOVE|UPDATE_TITLE|UPDATE_HEALTH title progress`,
 *   `TITLE title subtitle`, `TITLECLEARED`, `NAMETAG player prefix suffix`, `BELOWNAME player text`, `TAB header footer`, `SIDEBAR title lines` (lines top to bottom, `|`-separated, empty when none shows),
 *   `DISCONNECTED reason`;
 * - commands: `CLICK slot LEFT|RIGHT|SHIFT|DROP|HOTBAR n`, `CLOSE`, `COMMAND text`,
 *   `DIALOGCLICK label key=value...` (each value typed as the dialog declares its input), `QUIT`.
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

    @Volatile private var dialog: NbtMap? = null

    // The dialog registry as the server sent it while configuring: a dialog shown by id is the entry
    // at that index, and the server's own entries come with their data.
    private val dialogRegistry = ArrayList<Pair<String, NbtMap?>>()

    // The scoreboard as the client keeps it: objective titles, scores per objective, and which shows.
    private val objectives = HashMap<String, String>()
    private val scores = HashMap<String, HashMap<String, Pair<Int, String>>>()

    // What a client draws around each name: its team's prefix and suffix, and the below-name text,
    // reported only when it changes.
    private class Team(var prefix: String = "", var suffix: String = "", val members: MutableSet<String> = HashSet())

    private val teams = HashMap<String, Team>()
    private val nametags = HashMap<String, String>()
    private var belowName: String? = null
    private val fixed = HashMap<String, HashMap<String, String>>()
    private val belowNames = HashMap<String, String>()
    private var sidebar: String? = null

    // The server sends a title's subtitle first; the title packet is what makes it show.
    private var subtitle = ""
    private var lastSidebar: String? = null

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
            is ClientboundShowDialogGamePacket -> {
                val shown = if (packet.dialog.isCustom) packet.dialog.custom() else dialogRegistry.getOrNull(packet.dialog.id())?.second
                if (shown == null) {
                    emit("ERROR", "a dialog by id ${packet.dialog.id()} the registry did not send")
                } else {
                    dialog = shown
                    val inputs = shown.getList("inputs", NbtType.COMPOUND, emptyList()).joinToString("|") { "${it.getString("key")}:${it.getString("type").substringAfter(':')}" }
                    emit("DIALOG", text(shown["title"]), buttons(shown).joinToString("|") { text(it["label"]) }, inputs)
                }
            }
            is ClientboundRegistryDataPacket -> if (packet.registry.asString() == "minecraft:dialog") {
                dialogRegistry.clear()
                packet.entries.forEach { dialogRegistry += it.id.asString() to (it.data as? NbtMap) }
            }
            is ClientboundUpdateTagsPacket -> packet.tags.entries.firstOrNull { it.key.asString() == "minecraft:dialog" }?.value?.forEach { (tag, ids) ->
                emit("DIALOGTAG", tag.asString(), ids.joinToString(",") { dialogRegistry.getOrNull(it)?.first ?: "#$it" })
            }
            is ClientboundSetActionBarTextPacket -> emit("ACTIONBAR", plain.serialize(packet.text))
            is ClientboundSetSubtitleTextPacket -> subtitle = plain.serialize(packet.text)
            is ClientboundSetTitleTextPacket -> emit("TITLE", plain.serialize(packet.text), subtitle)
            is ClientboundClearTitlesPacket -> {
                subtitle = ""
                emit("TITLECLEARED")
            }
            is ClientboundBossEventPacket -> emit(
                "BOSSBAR",
                packet.uuid.toString(),
                packet.action.name,
                packet.title?.let { plain.serialize(it) } ?: "",
                if (packet.action == BossBarAction.ADD || packet.action == BossBarAction.UPDATE_HEALTH) packet.health.toString() else "",
            )
            is ClientboundTabListPacket -> emit("TAB", plain.serialize(packet.header), plain.serialize(packet.footer))
            is ClientboundSetObjectivePacket -> {
                when (packet.action) {
                    ObjectiveAction.REMOVE -> {
                        objectives.remove(packet.name)
                        scores.remove(packet.name)
                        fixed.remove(packet.name)
                        if (belowName == packet.name) belowName = null
                    }
                    else -> objectives[packet.name] = packet.displayName?.let { plain.serialize(it) }.orEmpty()
                }
                sidebarChanged()
                belowNamesChanged()
            }
            is ClientboundSetDisplayObjectivePacket -> when (packet.position) {
                ScoreboardPosition.SIDEBAR -> {
                    sidebar = packet.name.ifEmpty { null }
                    sidebarChanged()
                }
                ScoreboardPosition.BELOW_NAME -> {
                    belowName = packet.name.ifEmpty { null }
                    belowNamesChanged()
                }
                else -> {}
            }
            is ClientboundSetScorePacket -> {
                scores.getOrPut(packet.objective) { HashMap() }[packet.owner] = packet.value to (packet.display?.let { plain.serialize(it) } ?: packet.owner)
                val format = packet.numberFormat
                val byOwner = fixed.getOrPut(packet.objective) { HashMap() }
                if (format is FixedFormat) byOwner[packet.owner] = text(format.value) else byOwner.remove(packet.owner)
                sidebarChanged()
                belowNamesChanged()
            }
            is ClientboundResetScorePacket -> {
                if (packet.objective == null) scores.values.forEach { it.remove(packet.owner) } else scores[packet.objective]?.remove(packet.owner)
                if (packet.objective == null) fixed.values.forEach { it.remove(packet.owner) } else fixed[packet.objective]?.remove(packet.owner)
                sidebarChanged()
                belowNamesChanged()
            }
            is ClientboundSetPlayerTeamPacket -> {
                val team = teams.getOrPut(packet.teamName) { Team() }
                val touched = HashSet(team.members)
                when (packet.action) {
                    TeamAction.CREATE, TeamAction.UPDATE -> {
                        team.prefix = text(packet.playerPrefix)
                        team.suffix = text(packet.playerSuffix)
                    }
                    TeamAction.REMOVE -> {
                        teams.remove(packet.teamName)
                        team.members.clear()
                    }
                    else -> {}
                }
                val players = packet.players?.filterNotNull().orEmpty()
                when (packet.action) {
                    TeamAction.CREATE, TeamAction.ADD_PLAYER -> team.members.addAll(players)
                    TeamAction.REMOVE_PLAYER -> team.members.removeAll(players.toSet())
                    else -> {}
                }
                (touched + players).forEach(::nametagChanged)
            }
            is ClientboundClearDialogPacket -> {
                dialog = null
                emit("DIALOGCLOSED")
            }
        }
    }

    /** Runs one command; false means quit. */
    fun command(line: String): Boolean {
        val parts = line.split('\t')
        when (parts[0]) {
            "CLICK" -> click(parts[1].toInt(), parts[2], parts.getOrNull(3)?.toInt())
            "CLOSE" -> session.send(ServerboundContainerClosePacket(window))
            "COMMAND" -> session.send(ServerboundChatCommandPacket(parts[1].removePrefix("/")))
            "DIALOGCLICK" -> answer(parts[1], parts.drop(2).associate { it.substringBefore('=') to it.substringAfter('=') })
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

    // Reports the sidebar the client would draw, highest score first, only when it differs.
    private fun text(component: Component?): String = component?.let(plain::serialize).orEmpty()

    private fun nametagChanged(owner: String) {
        val team = teams.values.firstOrNull { owner in it.members }
        val shown = "${team?.prefix.orEmpty()}	${team?.suffix.orEmpty()}"
        if (nametags.put(owner, shown) != shown) emit("NAMETAG", owner, team?.prefix.orEmpty(), team?.suffix.orEmpty())
    }

    private fun belowNamesChanged() {
        val now = belowName?.let { fixed[it] }.orEmpty()
        for (owner in now.keys + belowNames.keys) {
            val shown = now[owner].orEmpty()
            if ((belowNames[owner] ?: "") == shown) continue
            if (shown.isEmpty()) belowNames.remove(owner) else belowNames[owner] = shown
            emit("BELOWNAME", owner, shown)
        }
    }

    private fun sidebarChanged() {
        val shown = sidebar?.let { name ->
            val lines = scores[name].orEmpty().values.sortedByDescending { it.first }.joinToString("|") { it.second }
            "${objectives[name].orEmpty()}\t$lines"
        } ?: "\t"
        if (shown == lastSidebar) return
        lastSidebar = shown
        val (title, lines) = shown.split('\t', limit = 2)
        emit("SIDEBAR", title, lines)
    }

    /** The buttons of a notice, confirmation or multi-action dialog, in the order the client shows them. */
    private fun buttons(dialog: NbtMap): List<NbtMap> =
        listOfNotNull(dialog.getCompound("action", null), dialog.getCompound("yes", null), dialog.getCompound("no", null)) +
            dialog.getList("actions", NbtType.COMPOUND, emptyList())

    // As the client does: the button's custom click carries its additions plus every input's value.
    private fun answer(label: String, values: Map<String, String>) {
        val shown = dialog ?: return emit("ERROR", "no dialog is open")
        val button = buttons(shown).firstOrNull { text(it["label"]) == label } ?: return emit("ERROR", "no button '$label'")
        val action = button.getCompound("action")
        val payload = action.getCompound("additions", NbtMap.EMPTY).toBuilder()
        shown.getList("inputs", NbtType.COMPOUND, emptyList()).forEach { input ->
            val key = input.getString("key")
            val value = values[key] ?: return@forEach
            when (input.getString("type").substringAfter(':')) {
                "boolean" -> payload.putBoolean(key, value.toBoolean())
                "number_range" -> payload.putFloat(key, value.toFloat())
                else -> payload.putString(key, value)
            }
        }
        session.send(ServerboundCustomClickActionPacket(net.kyori.adventure.key.Key.key(action.getString("id")), payload.build()))
    }

    /** Plain text of a text component as NBT: a string, or a compound with `text` and `extra`. */
    private fun text(component: Any?): String = when (component) {
        is String -> component
        is NbtMap -> component.getString("text", "") + component.getList("extra", NbtType.COMPOUND, emptyList()).joinToString("") { text(it) }
        else -> ""
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
