package studio.sculk.itest.threading

import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import studio.sculk.canvas.CanvasSupport
import studio.sculk.capability.Capability
import studio.sculk.execution.EntityResult
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.delayTicks
import studio.sculk.execution.withAsync
import studio.sculk.execution.withGlobal
import studio.sculk.paper.PaperCapabilities
import studio.sculk.paper.PaperPlatformExtension
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
    private val lateTasksRan = AtomicBoolean(false)

    override suspend fun start() {
        sculk.scope.launch {
            delayTicks(20)
            runScenarios()
            scheduleTasksThatShutdownMustCancel()
            write()
            withGlobal { server.shutdown() }
        }
    }

    override fun stop() {
        results["shutdown-cancels-pending-work"] = if (lateTasksRan.get()) "FAIL: a task scheduled before shutdown ran" else "PASS"
        write()
    }

    private suspend fun runScenarios() {
        val world = server.worlds.first()
        scenario("platform-detected") { platformDetected() }
        scenario("entity-work-follows-teleport") { entityWorkFollowsTeleport(world) }
        scenario("teleport-continues-at-destination") { teleportContinuesAtDestination(world) }
        scenario("entity-removed-while-waiting") { entityRemovedWhileWaiting(world) }
        scenario("region-batch-owns-every-item") { regionBatchOwnsEveryItem(world) }
        scenario("ownership-matches-platform") { ownershipMatchesPlatform(world) }
        scenario("zero-delays-run-next-tick") { zeroDelaysRunNextTick() }
        scenario("canvas-region-data-merges") { canvasRegionDataMerges(world) }
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
        val expectsPostEffects = minecraft >= requireNotNull(MinecraftVersion.parse("26.3"))
        return check(kind == expected && postEffects == expectsPostEffects && canvasData == (kind == PlatformInfo.Kind.CANVAS)) {
            "detected $kind on $minecraft, post effects $postEffects, canvas region data $canvasData"
        }
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

    private suspend fun scheduleTasksThatShutdownMustCancel() {
        val executor = sculk.scope.execution.executor
        val world = server.worlds.first()
        val anchor = Location(world, 40.0, 100.0, 40.0)
        val stand = spawnStand(anchor)
        val mark = work { lateTasksRan.set(true) }
        withGlobal {
            executor.schedule(ExecutionContext.Global, 400, mark)
            executor.schedule(ExecutionContext.Async, 400, mark)
            executor.schedule(ExecutionContext.Entity(stand.ref), 400, mark)
            executor.schedule(anchor.regionContext(sculk.sectionShift), 400, mark)
        }
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
