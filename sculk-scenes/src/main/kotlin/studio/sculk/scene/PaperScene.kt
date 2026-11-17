package studio.sculk.scene

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.IllegalPluginAccessException
import studio.sculk.SculkResult
import studio.sculk.execution.EntityGoneException
import studio.sculk.execution.EntityResult
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkScope
import studio.sculk.execution.delayTicks
import studio.sculk.failureOrNull
import studio.sculk.paper.ref
import studio.sculk.paper.withEntity
import studio.sculk.paper.withRegion
import studio.sculk.placeholder.PlaceholderChange
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.coroutines.cancellation.CancellationException

/**
 * One scene on Paper, Folia or Canvas. Public calls only record what is wanted and post a command;
 * one coroutine, the driver, applies commands in order and hops to whichever thread owns the chunk,
 * entity or viewer each step touches, so the state below the driver line is never shared.
 */
internal class PaperScene(private val scenes: PaperScenes, spec: SceneSpec, anchor: Anchor, viewers: Viewers, private val number: Long) :
    Scene {
    private val scope: SculkScope = scenes.scope.child("scene-$number")
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val lock = Any()

    @Volatile override var spec: SceneSpec = spec
        private set

    @Volatile override var anchor: Anchor = anchor
        private set

    @Volatile private var viewers: Viewers = viewers

    @Volatile override var isOpen: Boolean = true
        private set

    @Volatile override var isSpawned: Boolean = false
        private set

    // Where the displays stand or are about to: written by the driver, and by the target's thread while following.
    @Volatile private var position: Location? = (anchor as? Anchor.At)?.location

    // Every tag a render has asked for, so a change to any other costs no command.
    @Volatile private var watched: Set<String> = emptySet()

    // The chunk this scene waits for, read when it closes.
    @Volatile private var awaited: ChunkKey? = null

    // Every live entity: written by the driver, read by close and by the follow loop.
    private val live = ConcurrentHashMap<UUID, Display>()

    // Guarded by lock.
    private var follow: Job? = null

    // Driver only.
    private var shown: SceneSpec = SceneSpec.EMPTY
    private var applied: Viewers = viewers
    private val slots = LinkedHashMap<SlotKey, Slot>()

    private class Slot(val key: SlotKey, var node: SceneNode) {
        var entity: Display? = null
        var sent: Component? = null
        var tags: Set<String> = emptySet()
    }

    private sealed interface Command {
        data object Reconcile : Command

        data object Refresh : Command

        data object ChunkLoaded : Command

        data object Relocate : Command

        class Dirty(val change: PlaceholderChange) : Command

        class Removed(val entity: UUID, val unloaded: Boolean) : Command

        class Joined(val viewer: UUID) : Command

        class MoveTo(val location: Location) : Command
    }

    override val visibility: Visibility get() = viewers.asVisibility()

    override fun update(spec: SceneSpec): SculkResult<Unit> {
        SceneRules.check(spec, viewers).failureOrNull()?.let { return it }
        if (!isOpen) return SculkResult.ok()
        this.spec = spec
        send(Command.Reconcile)
        return SculkResult.ok()
    }

    override fun move(anchor: Anchor) {
        synchronized(lock) {
            if (!isOpen) return
            this.anchor = anchor
            follow?.cancel()
            follow = null
            when (anchor) {
                is Anchor.At -> send(Command.MoveTo(anchor.location))
                is Anchor.Following -> follow = following(anchor)
            }
        }
    }

    override fun show(player: Player) {
        synchronized(lock) { viewers = viewers.show(player.uniqueId) }
        send(Command.Reconcile)
    }

    override fun hide(player: Player) {
        synchronized(lock) { viewers = viewers.hide(player.uniqueId) }
        send(Command.Reconcile)
    }

    override fun isShownTo(player: Player): Boolean = viewers.sees(player.uniqueId)

    override fun refresh() {
        send(Command.Refresh)
    }

    override fun close() {
        scope.close()
    }

    override fun toString(): String = "Scene($number, ${spec.nodes.size} nodes, $anchor)"

    fun start() {
        scope.resources.onClose(::shutDown)
        scope.launch(scope.execution.dispatcher(ExecutionContext.Async)) {
            for (command in commands) handle(command)
        }
        when (val at = anchor) {
            is Anchor.At -> send(Command.Reconcile)
            is Anchor.Following -> synchronized(lock) { follow = following(at) }
        }
    }

    fun signal(change: PlaceholderChange, tag: String) {
        if (isOpen && tag in watched) send(Command.Dirty(change))
    }

    fun removed(entity: UUID, unloaded: Boolean) {
        send(Command.Removed(entity, unloaded))
    }

    fun chunkLoaded() {
        send(Command.ChunkLoaded)
    }

    // Chosen viewers lose Paper's per-plugin show when they leave; hidden ones lose the hide.
    fun joined(viewer: UUID) {
        if (viewer in viewers.listed) send(Command.Joined(viewer))
    }

    fun left(viewer: UUID) {
        if (viewer in viewers.listed) send(Command.Reconcile)
    }

    private fun send(command: Command) {
        commands.trySend(command)
    }

    private suspend fun handle(command: Command) {
        try {
            when (command) {
                Command.Reconcile, Command.ChunkLoaded -> reconcile()
                Command.Refresh -> rerender { true }
                Command.Relocate -> relocate()
                is Command.Dirty -> rerender { command.change.key.tag in it.tags && SceneRules.affects(command.change, it.key, targetId()) }
                is Command.Removed -> removed(command)
                is Command.Joined -> rejoined(command.viewer)
                is Command.MoveTo -> moveTo(command.location)
            }
        } catch (cancelled: CancellationException) {
            // A hop refused because its region or entity went away; the scene goes on unless it was closed itself.
            currentCoroutineContext().ensureActive()
        } catch (failure: Exception) {
            scenes.plugin.logger.log(Level.SEVERE, "$this could not apply ${command::class.simpleName}", failure)
        }
    }

    private suspend fun reconcile() {
        val target = spec
        val wanted = viewers
        for (change in SceneDiff.between(shown, target)) {
            when (change) {
                is NodeChange.Removed, is NodeChange.Replaced -> slotsOf(change.id).forEach { despawn(it) }

                is NodeChange.Changed -> slotsOf(change.id).forEach { slot ->
                    slot.node = change.node
                    redraw(slot, change.parts)
                }

                is NodeChange.Added -> {}
            }
        }
        if (shown.teleportTicks != target.teleportTicks) {
            for (entity in live.values) {
                withEntity(entity) { entity.teleportDuration = target.teleportTicks }
            }
        }
        shown = target
        val keys = SceneRules.slots(target, wanted) { scenes.player(it) != null }
        val keep = keys.toSet()
        slots.values.filter { it.key !in keep }.forEach { despawn(it) }
        keys.forEach { key -> slots.getOrPut(key) { Slot(key, target.requireNode(key.node)) } }
        val changed = applied.changed(wanted)
        applied = wanted
        applyViews(views(changed, slots.values.filter { it.key.viewer == null }))
        spawnMissing()
    }

    private fun slotsOf(node: String): List<Slot> = slots.values.filter { it.key.node == node }

    private suspend fun rejoined(viewer: UUID) {
        reconcile()
        applyViews(views(setOf(viewer), slots.values.filter { it.key.viewer == null }))
    }

    private suspend fun removed(command: Command.Removed) {
        val slot = slots.values.firstOrNull { it.entity?.uniqueId == command.entity } ?: return
        forget(slot)
        isSpawned = false
        // An unloading chunk refuses new entities; the scene comes back when the chunk does.
        if (command.unloaded) chunkOf(position)?.let(::awaitChunk) else spawnMissing()
    }

    private suspend fun relocate() {
        slots.values.forEach { despawn(it, keepSlot = true) }
        reconcile()
    }

    private suspend fun moveTo(location: Location) {
        val last = position
        position = location
        go(last, location)
    }

    private suspend fun go(from: Location?, to: Location) {
        when (stepFrom(from, to)) {
            SceneRules.Step.STAY -> {}
            SceneRules.Step.GLIDE -> live.values.forEach { entity -> withEntity(entity) { entity.teleportAsync(to) } }
            SceneRules.Step.RESPAWN -> relocate()
        }
    }

    private suspend fun spawnMissing() {
        val missing = slots.values.filter { it.entity == null }
        if (missing.isEmpty()) {
            isSpawned = true
            return
        }
        isSpawned = false
        val at = position ?: return
        val chunk = chunkOf(at) ?: return
        val world = at.world ?: return
        // Registered before the check, so a load between the two is never missed.
        awaitChunk(chunk)
        val loaded = withRegion(at) {
            if (!world.isChunkLoaded(chunk.x, chunk.z)) return@withRegion false
            missing.forEach { spawn(it, world, at) }
            true
        }
        if (!loaded) return
        stopAwaiting()
        val owners = missing.mapNotNull { it.key.viewer }
        applyViews(views(applied.listed + owners, missing))
        isSpawned = slots.values.all { it.entity != null }
        // The followed entity may have moved on while the displays were being made.
        position?.let { now -> if (now != at) go(at, now) }
    }

    /** Spawns [slot]'s entity, fully drawn before it joins the world. Region thread only. */
    private fun spawn(slot: Slot, world: World, at: Location) {
        slot.sent = null
        val node = slot.node
        val content = content(slot, Part.entries.toSet())
        val visible = slot.key.viewer == null && applied.everyone
        val ticks = shown.teleportTicks
        val entity: Display = when (node) {
            is TextNode -> world.spawn(at, TextDisplay::class.java) { DisplayWriter.configure(it, node, content, ticks, visible) }
            is ItemNode -> world.spawn(at, ItemDisplay::class.java) { DisplayWriter.configure(it, node, content, ticks, visible) }
            is BlockNode -> world.spawn(at, BlockDisplay::class.java) { DisplayWriter.configure(it, node, content, ticks, visible) }
        }
        slot.entity = entity
        live[entity.uniqueId] = entity
        scenes.own(entity.uniqueId, this)
        // Paired with shutDown: either it sees this entity in live, or this sees the scene closed.
        if (!isOpen) {
            forget(slot)
            entity.remove()
        }
    }

    private suspend fun redraw(slot: Slot, parts: Set<Part>) {
        val entity = slot.entity ?: return
        val result = withEntity(entity) { DisplayWriter.write(entity, slot.node, parts, content(slot, parts)) }
        if (result is EntityResult.Gone) forget(slot)
    }

    private suspend fun rerender(which: (Slot) -> Boolean) {
        slots.values.filter { it.node is TextNode && it.entity != null && which(it) }.forEach { redraw(it, setOf(Part.TEXT)) }
    }

    private fun content(slot: Slot, parts: Set<Part>): Content? = when (val node = slot.node) {
        is TextNode -> if (Part.TEXT in parts) render(slot, node)?.let { Content.Text(it) } else null
        is ItemNode -> if (Part.ITEM in parts) Content.Item(node.item.create(scenes.text)) else null
        is BlockNode -> if (Part.BLOCK in parts) Content.Block(Bukkit.createBlockData(node.state)) else null
    }

    /** The text for [slot], or null when it renders as what was last sent. */
    private fun render(slot: Slot, node: TextNode): Component? {
        val used = HashSet<String>()
        val resolver = resolver(slot.key.viewer)?.let { Recording(it, used, ::watch) } ?: TagResolver.empty()
        val rendered = scenes.text.render(node.text, resolver)
        slot.tags = used
        if (rendered == slot.sent) return null
        slot.sent = rendered
        return rendered
    }

    private fun resolver(viewer: UUID?): TagResolver? {
        val registry = scenes.placeholders ?: return null
        val target = (anchor as? Anchor.Following)?.entity
        val player = viewer?.let(scenes::player)
        return when {
            player != null && target != null -> registry.resolver(player, target)
            player != null -> registry.resolver(player)
            target != null -> registry.resolver(target)
            else -> registry.resolver()
        }
    }

    private fun watch(tag: String) {
        if (tag !in watched) watched = watched + tag
    }

    private fun targetId(): UUID? = (anchor as? Anchor.Following)?.entity?.uniqueId

    /** For each viewer, the entities among [among] to show or hide for them now. */
    private fun views(players: Collection<UUID>, among: Collection<Slot>): Map<UUID, List<Pair<Display, Boolean>>> {
        val views = HashMap<UUID, MutableList<Pair<Display, Boolean>>>()
        for (slot in among) {
            val entity = slot.entity ?: continue
            val owner = slot.key.viewer
            if (owner != null) {
                if (owner in players) views.getOrPut(owner) { ArrayList() } += entity to true
                continue
            }
            players.forEach { views.getOrPut(it) { ArrayList() } += entity to applied.sees(it) }
        }
        return views
    }

    // Paper keeps who sees what on the viewer, so the viewer's own thread changes it.
    private suspend fun applyViews(views: Map<UUID, List<Pair<Display, Boolean>>>) {
        for ((viewer, entities) in views) {
            val player = scenes.player(viewer) ?: continue
            withEntity(player) {
                entities.forEach { (entity, show) ->
                    if (show) player.showEntity(scenes.plugin, entity) else player.hideEntity(scenes.plugin, entity)
                }
            }
        }
    }

    private suspend fun despawn(slot: Slot, keepSlot: Boolean = false) {
        val entity = slot.entity
        forget(slot)
        if (!keepSlot) slots.remove(slot.key)
        if (entity != null) withEntity(entity) { entity.remove() }
    }

    private fun forget(slot: Slot) {
        slot.entity?.let { entity ->
            live.remove(entity.uniqueId)
            scenes.disown(entity.uniqueId)
        }
        slot.entity = null
        slot.sent = null
    }

    private fun awaitChunk(chunk: ChunkKey) {
        if (awaited == chunk) return
        stopAwaiting()
        scenes.await(chunk, this)
        awaited = chunk
    }

    private fun stopAwaiting() {
        awaited?.let { scenes.stopAwaiting(it, this) }
        awaited = null
    }

    private fun chunkOf(location: Location?): ChunkKey? {
        if (location == null || !location.isWorldLoaded) return null
        val world = location.world ?: return null
        return ChunkKey(world.uid, location.blockX shr 4, location.blockZ shr 4)
    }

    private fun stepFrom(last: Location?, next: Location): SceneRules.Step {
        if (last == null || !last.isWorldLoaded || !next.isWorldLoaded) return SceneRules.Step.RESPAWN
        val sameWorld = last.world == next.world
        return SceneRules.step(sameWorld, if (sameWorld) last.distanceSquared(next) else 0.0)
    }

    /** Follows [anchor]'s entity on its own thread; the scene closes when the entity leaves the world. */
    private fun following(anchor: Anchor.Following): Job? {
        val offset = anchor.offset
        val job = try {
            scope.launch(scope.execution.dispatcher(ExecutionContext.Entity(anchor.entity.ref))) {
                while (true) {
                    val next = anchor.entity.location.add(offset.x.toDouble(), offset.y.toDouble(), offset.z.toDouble())
                    next.yaw = 0f
                    next.pitch = 0f
                    track(next)
                    delayTicks(anchor.periodTicks)
                }
            }
        } catch (_: IllegalStateException) {
            // The scope closed in the meantime; there is nothing left to follow for.
            return null
        }
        job.invokeOnCompletion { cause -> if (cause is EntityGoneException) close() }
        return job
    }

    /** One observed position of the followed entity, on its thread. */
    private fun CoroutineScope.track(next: Location) {
        when (stepFrom(position, next)) {
            SceneRules.Step.STAY -> {}

            SceneRules.Step.RESPAWN -> {
                position = next
                send(Command.Relocate)
            }

            SceneRules.Step.GLIDE -> {
                position = next
                for (entity in live.values) {
                    if (Bukkit.isOwnedByCurrentRegion(entity)) {
                        entity.teleportAsync(next)
                    } else {
                        launch { withEntity(entity) { entity.teleportAsync(next) } }
                    }
                }
            }
        }
    }

    private fun shutDown() {
        isOpen = false
        scenes.ended(this)
        commands.close()
        stopAwaiting()
        for (entity in live.values.toList()) {
            live.remove(entity.uniqueId)
            scenes.disown(entity.uniqueId)
            try {
                remove(entity)
            } catch (failure: RuntimeException) {
                scenes.plugin.logger.log(Level.WARNING, "$this could not remove ${entity.uniqueId}", failure)
            }
        }
    }

    // At shutdown every ownership check passes and nothing scheduled would run (THREADING.md rule 8),
    // so the owner removes at once; anyone else hands the removal to the entity's own scheduler.
    private fun remove(entity: Display) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            entity.remove()
            return
        }
        try {
            entity.scheduler.run(scenes.plugin, { entity.remove() }, null)
        } catch (_: IllegalPluginAccessException) {
            // The plugin is already disabled. The entity is never saved, so it goes with its chunk.
        }
    }

    /** Records which tags a render resolved, watching each before its value is read so no change races past. */
    private class Recording(private val delegate: TagResolver, private val used: MutableSet<String>, private val watch: (String) -> Unit) :
        TagResolver {
        override fun resolve(name: String, arguments: ArgumentQueue, ctx: Context): Tag? {
            if (delegate.has(name)) {
                watch(name)
                used += name
            }
            return delegate.resolve(name, arguments, ctx)
        }

        override fun has(name: String): Boolean = delegate.has(name)
    }
}
