package studio.sculk.itest.threading

import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import studio.sculk.annotation.SculkInternal
import studio.sculk.canvas.CanvasSupport
import studio.sculk.capability.Capability
import studio.sculk.event.EventPriority
import studio.sculk.event.listen
import studio.sculk.event.observe
import studio.sculk.execution.EntityResult
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.delayTicks
import studio.sculk.execution.withAsync
import studio.sculk.execution.withGlobal
import studio.sculk.getOrThrow
import studio.sculk.paper.PaperCapabilities
import studio.sculk.paper.PaperExecutor
import studio.sculk.paper.PaperPlatformExtension
import studio.sculk.paper.Sculk
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.paper.ref
import studio.sculk.paper.regionBatch
import studio.sculk.paper.regionContext
import studio.sculk.paper.withEntity
import studio.sculk.paper.withRegion
import studio.sculk.runtime.PlatformInfo
import studio.sculk.version.MinecraftVersion
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the THREADING.md scenarios on a real server, writes one PASS, SKIP or FAIL line per
 * scenario to `results.properties`, and stops the server. The build's verify task reads the file.
 */
class ThreadingPlugin : SculkPaperPlugin() {
    override val platformExtensions: List<PaperPlatformExtension> = listOf(CanvasSupport)

    private val results = ConcurrentHashMap<String, String>()

    override suspend fun start() {
        sculk.scope.launch {
            delayTicks(20)
            runScenarios()
            write()
            withGlobal { server.shutdown() }
        }
    }

    private suspend fun runScenarios() {
        val world = server.worlds.first()
        scenario("platform-detected") { platformDetected() }
        scenario("region-shift-matches-server") { regionShiftMatchesServer() }
        scenario("entity-work-follows-teleport") { entityWorkFollowsTeleport(world) }
        scenario("teleport-continues-at-destination") { teleportContinuesAtDestination(world) }
        scenario("entity-removed-while-waiting") { entityRemovedWhileWaiting(world) }
        scenario("region-batch-owns-every-item") { regionBatchOwnsEveryItem(world) }
        scenario("ownership-matches-platform") { ownershipMatchesPlatform(world) }
        scenario("zero-delays-run-next-tick") { zeroDelaysRunNextTick() }
        scenario("canvas-region-data-merges") { canvasRegionDataMerges(world) }
        scenario("shutdown-cancels-pending-work") { closingRetiresPendingWork(world) }
        scenario("events-cancel-and-observe-the-outcome") { eventsCancelAndObserve() }
        scenario("events-observers-resume-on-the-entity") { observersResumeOnTheEntity(world) }
    }

    private suspend fun scenario(name: String, block: suspend () -> String) {
        results[name] = try {
            withTimeout(60.seconds) { block() }
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    private fun check(condition: Boolean, detail: () -> String): String = if (condition) "PASS" else "FAIL: ${detail()}"

    private fun platformDetected(): String {
        val expected = System.getProperty("sculk.itest.platform")?.let(PlatformInfo.Kind::valueOf) ?: return "FAIL: no expected platform given"
        val kind = sculk.platform.kind
        val minecraft = sculk.platform.minecraft ?: return "FAIL: no Minecraft version detected"
        val postEffects = sculk.capabilities[PaperCapabilities.POST_EFFECTS].isAvailable
        val canvasData = sculk.capabilities[CanvasSupport.REGION_DATA].isAvailable
        val expectsPostEffects = minecraft >= MinecraftVersion.parse("26.3").getOrThrow()
        return check(kind == expected && postEffects == expectsPostEffects && canvasData == (kind == PlatformInfo.Kind.CANVAS)) {
            "detected $kind on $minecraft, post effects $postEffects, canvas region data $canvasData"
        }
    }

    private fun regionShiftMatchesServer(): String {
        val expected = System.getProperty("sculk.itest.gridExponent")?.toInt() ?: return "SKIP: not a regionized run"
        return check(sculk.regionShift == expected) { "runtime uses ${sculk.regionShift}, server is configured with $expected" }
    }

    private suspend fun spawnStand(at: Location): ArmorStand {
        loadChunk(at)
        return withRegion(at) {
            at.world.spawn(at, ArmorStand::class.java) { stand ->
                stand.isPersistent = false
                stand.setGravity(false)
            }
        }
    }

    private suspend fun loadChunk(at: Location) {
        at.world.getChunkAtAsync(at.blockX shr 4, at.blockZ shr 4, true).await()
        withRegion(at) { at.world.addPluginChunkTicket(at.blockX shr 4, at.blockZ shr 4, this@ThreadingPlugin) }
    }

    /** Far enough that Folia and Canvas place source and destination in different regions. */
    private fun far(world: World, x: Int) = Location(world, x.toDouble(), 100.0, 0.5)

    private suspend fun entityWorkFollowsTeleport(world: World): String = withGlobal {
        val stand = spawnStand(far(world, 8))
        val destination = far(world, 2_048)
        loadChunk(destination)
        val waiting = async {
            withEntity(stand) {
                delayTicks(40)
                Bukkit.isOwnedByCurrentRegion(stand) to stand.location.blockX
            }
        }
        delayTicks(10)
        withEntity(stand) { stand.teleportAsync(destination).await() }
        val outcome = waiting.await()
        withEntity(stand) { stand.remove() }
        check(outcome == EntityResult.Ran(true to 2_048)) { "outcome $outcome" }
    }

    private suspend fun teleportContinuesAtDestination(world: World): String = withGlobal {
        val stand = spawnStand(far(world, 16))
        val destination = far(world, -2_048)
        loadChunk(destination)
        val outcome = withEntity(stand) {
            val moved = stand.teleportAsync(destination).await()
            moved && Bukkit.isOwnedByCurrentRegion(stand) && Bukkit.isOwnedByCurrentRegion(destination)
        }
        withEntity(stand) { stand.remove() }
        check(outcome == EntityResult.Ran(true)) { "outcome $outcome" }
    }

    private suspend fun entityRemovedWhileWaiting(world: World): String = withGlobal {
        val stand = spawnStand(far(world, 24))
        val bodyRan = AtomicBoolean(false)
        val waiting = async {
            withEntity(stand) {
                delayTicks(100)
                bodyRan.set(true)
            }
        }
        delayTicks(20)
        withEntity(stand) { stand.remove() }
        val outcome = waiting.await()
        check(outcome is EntityResult.Gone && !bodyRan.get()) { "outcome $outcome, body ran ${bodyRan.get()}" }
    }

    private suspend fun regionBatchOwnsEveryItem(world: World): String {
        val targets = listOf(10 to 10, 300 to 10, 10 to 300, -300 to -300, 12 to 12, 1_100 to -900)
            .map { (x, z) -> Location(world, x.toDouble(), 120.0, z.toDouble()) }
        targets.forEach { loadChunk(it) }
        val outcomes = regionBatch(targets, { it }) { target ->
            val owned = Bukkit.isOwnedByCurrentRegion(target)
            target.block.type = Material.STONE
            "${target.blockX},${target.blockZ}:${owned && target.block.type == Material.STONE}"
        }
        val expected = targets.map { "${it.blockX},${it.blockZ}:true" }
        return check(outcomes == expected) { "outcomes $outcomes" }
    }

    private suspend fun ownershipMatchesPlatform(world: World): String {
        val stand = spawnStand(far(world, 4_096))
        val executor = sculk.scope.execution.executor
        val fromGlobal = withGlobal { executor.owns(ExecutionContext.Entity(stand.ref)) }
        val fromAsync = withAsync { executor.owns(ExecutionContext.Entity(stand.ref)) }
        withEntity(stand) { stand.remove() }
        val expectedFromGlobal = !sculk.platform.kind.isRegionized
        return check(fromGlobal == expectedFromGlobal && !fromAsync) { "global owns entity: $fromGlobal, async owns entity: $fromAsync" }
    }

    private suspend fun zeroDelaysRunNextTick(): String {
        val executor = sculk.scope.execution.executor
        val once = AtomicInteger()
        val repeated = AtomicInteger()
        val handle = withGlobal {
            executor.schedule(ExecutionContext.Global, 0, work { once.incrementAndGet() })
            executor.scheduleRepeating(ExecutionContext.Global, 0, 0, work { repeated.incrementAndGet() })
        }
        withGlobal { delayTicks(10) }
        handle.close()
        return check(once.get() == 1 && repeated.get() >= 8) { "once ${once.get()}, repeated ${repeated.get()}" }
    }

    private suspend fun canvasRegionDataMerges(world: World): String {
        val capability = sculk.capabilities[CanvasSupport.REGION_DATA]
        if (capability !is Capability.Native) return "SKIP: ${(capability as? Capability.Unavailable)?.reason}"
        val merges = AtomicInteger()
        val data = capability.service.create(create = { _ -> AtomicInteger() }, merge = { from, into, _ ->
            into.addAndGet(from.get())
            merges.incrementAndGet()
        }, split = { _, _ -> })
        val chunkZ = 400
        fun at(chunkX: Int) = Location(world, chunkX * 16.0, 100.0, chunkZ * 16.0)
        loadChunk(at(0))
        loadChunk(at(96))
        withGlobal { delayTicks(60) }
        // Canvas creates region data lazily on first read and merges only values that exist.
        withRegion(at(0)) { data.local().incrementAndGet() }
        withRegion(at(96)) { data.local().incrementAndGet() }
        for (chunkX in 4..92 step 4) loadChunk(at(chunkX))
        withGlobal { delayTicks(100) }
        return check(merges.get() > 0) { "no merge observed while connecting two regions" }
    }

    /**
     * A second runtime for this plugin schedules work of every kind a few ticks out and is closed at
     * once, while the server keeps ticking. None of the work may run, all of it must be retired, and
     * nothing may stay tracked.
     */
    @OptIn(SculkInternal::class)
    private suspend fun closingRetiresPendingWork(world: World): String {
        val anchor = Location(world, 40.0, 100.0, 40.0)
        val stand = spawnStand(anchor)
        val ran = AtomicInteger()
        val retired = AtomicInteger()
        val tracked = object : SculkExecutor.Work {
            override fun run() {
                ran.incrementAndGet()
            }

            override fun retired() {
                retired.incrementAndGet()
            }
        }
        val second = Sculk.attach(this, developmentMode = true)
        val executor = second.scope.execution.executor as PaperExecutor
        withGlobal {
            executor.schedule(ExecutionContext.Global, 5, tracked)
            executor.schedule(ExecutionContext.Async, 5, tracked)
            executor.schedule(ExecutionContext.Entity(stand.ref), 5, tracked)
            executor.schedule(anchor.regionContext(), 5, tracked)
            second.close()
        }
        withGlobal { delayTicks(40) }
        withEntity(stand) { stand.remove() }
        return check(ran.get() == 0 && retired.get() == 4 && executor.liveTasks == 0) {
            "ran ${ran.get()}, retired ${retired.get()}, still tracked ${executor.liveTasks}"
        }
    }

    /**
     * A low-priority listener cancels expensive purchases; a later listener that skips cancelled
     * events never sees them; a failing listener does not stop the others; an observer sees the
     * final outcome on the global region, where the event was fired.
     */
    private suspend fun eventsCancelAndObserve(): String {
        val laterSaw = AtomicInteger()
        val observed = ConcurrentHashMap<Int, String>()
        val handles = listOf(
            sculk.events.listen<PurchaseEvent>(EventPriority.LOW) { if (it.price > 100) it.isCancelled = true },
            sculk.events.listen<PurchaseEvent>(EventPriority.NORMAL) { error("a broken handler") },
            sculk.events.listen<PurchaseEvent>(EventPriority.HIGH) { laterSaw.incrementAndGet() },
            sculk.events.observe<PurchaseEvent>(includeCancelled = true) { event ->
                delayTicks(1)
                observed[event.price] = "${event.isCancelled}:${Bukkit.isGlobalTickThread()}"
            },
        )
        val outcomes = withGlobal { listOf(5, 500).map { price -> PurchaseEvent(price).also(server.pluginManager::callEvent).isCancelled } }
        withGlobal { delayTicks(5) }
        handles.forEach { it.close() }
        return check(outcomes == listOf(false, true) && laterSaw.get() == 1 && observed == mapOf(5 to "false:true", 500 to "true:true")) {
            "cancelled $outcomes, later handler saw ${laterSaw.get()}, observed $observed"
        }
    }

    private suspend fun observersResumeOnTheEntity(world: World): String {
        val stand = spawnStand(far(world, 6_144))
        val owned = AtomicBoolean(false)
        val ran = AtomicBoolean(false)
        val handle = sculk.events.observe<InspectEvent> { event ->
            delayTicks(2)
            owned.set(Bukkit.isOwnedByCurrentRegion(event.entity))
            ran.set(true)
        }
        withEntity(stand) { server.pluginManager.callEvent(InspectEvent(stand)) }
        withGlobal { delayTicks(10) }
        handle.close()
        withEntity(stand) { stand.remove() }
        return check(ran.get() && owned.get()) { "observer ran ${ran.get()}, owned the entity ${owned.get()}" }
    }

    private fun work(action: () -> Unit) = object : SculkExecutor.Work {
        override fun run() = action()
    }

    @Synchronized
    private fun write() {
        val file = File(dataFolder, "results.properties").also { it.parentFile.mkdirs() }
        file.writeText(results.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
    }
}
