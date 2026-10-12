package studio.sculk.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.testkit.FakeEntity
import studio.sculk.testkit.FakeExecutor
import studio.sculk.testkit.FakeExecutor.ThreadModel

@OptIn(ExperimentalCoroutinesApi::class)
class ExecutionTest {
    private val world = WorldKey("minecraft:overworld")
    private val zombie = FakeEntity("Zombie")

    @Test
    fun `entity work runs on the thread that owns the entity`() {
        val executor = FakeExecutor()
        val owned = executor.run {
            withEntity(zombie) { executor.owns(ExecutionContext.Entity(zombie)) }
        }
        assertEquals(EntityResult.Ran(true), owned)
        assertTrue(ExecutionContext.Entity(zombie) in executor.history)
    }

    @Test
    fun `on a regionized server global ownership does not grant entity ownership`() {
        val executor = FakeExecutor(ThreadModel.REGIONIZED)
        val ownsEntityFromGlobal = executor.run { withGlobal { executor.owns(ExecutionContext.Entity(zombie)) } }
        assertFalse(ownsEntityFromGlobal)
    }

    @Test
    fun `on Paper the main thread owns everything, so moving from global to an entity costs no tick`() {
        val executor = FakeExecutor(ThreadModel.SINGLE_THREAD)
        val ticks = executor.run {
            withGlobal {
                val before = executor.currentTick
                withEntity(zombie) { executor.currentTick - before }
            }
        }
        assertEquals(EntityResult.Ran(0L), ticks)
    }

    @Test
    fun `an entity removed before its work runs yields Gone, not an exception`() {
        val executor = FakeExecutor()
        executor.remove(zombie)
        val result = executor.run { withEntity(zombie) { "ran" } }
        assertEquals(EntityResult.Gone(zombie), result)
    }

    @Test
    fun `an entity removed while its work is waiting yields Gone and the rest of the block never runs`() {
        val executor = FakeExecutor()
        var afterDelay = false
        val scope = SculkScope.root("test", executor) {}
        val outcome = scope.async {
            withEntity(zombie) {
                delayTicks(20)
                afterDelay = true
            }
        }
        executor.tick(5)
        executor.remove(zombie)
        executor.tickUntil { outcome.isCompleted }
        assertEquals(EntityResult.Gone(zombie), outcome.getCompleted())
        assertFalse(afterDelay)
        scope.close()
    }

    @Test
    fun `delayTicks waits exactly that many ticks and resumes in the same context`() {
        val executor = FakeExecutor()
        val region = ExecutionContext.Region(world, 0, 0)
        val (waited, stillOwned) = executor.run {
            withRegion(world, 0, 0) {
                val start = executor.currentTick
                delayTicks(40)
                (executor.currentTick - start) to executor.owns(region)
            }
        }
        assertEquals(40L, waited)
        assertTrue(stillOwned)
    }

    @Test
    fun `asking a proxy for a region fails where it is written`() {
        val executor = FakeExecutor(ThreadModel.PROXY)
        val failure = assertThrows<UnsupportedContextException> {
            executor.run { withRegion(world, 0, 0) {} }
        }
        assertEquals("Fake Velocity cannot run work in Region(world=minecraft:overworld, chunkX=0, chunkZ=0).", failure.message)
    }

    @Test
    fun `work dispatched after shutdown is cancelled instead of hanging`() {
        val executor = FakeExecutor()
        assertThrows<CancellationException> {
            executor.run {
                withAsync { executor.shutdown() }
                withGlobal {}
            }
        }
    }

    @Test
    fun `closing a scope cancels its coroutines and refuses new ones`() {
        val executor = FakeExecutor()
        val scope = SculkScope.root("plugin", executor) {}
        val job = scope.launch { awaitCancellation() }
        executor.tick()
        scope.close()
        assertTrue(job.isCancelled)
        assertThrows<IllegalStateException> { scope.launch {} }
    }

    @Test
    fun `a failing coroutine is reported and does not cancel its siblings`() {
        val executor = FakeExecutor()
        val failures = mutableListOf<Throwable>()
        val scope = SculkScope.root("plugin", executor) { failures += it }
        val sibling = scope.launch { awaitCancellation() }
        scope.launch { error("boom") }
        executor.tick()
        assertEquals(listOf("boom"), failures.map { it.message })
        assertTrue(sibling.isActive)
        scope.close()
    }

    @Test
    fun `a child scope closes with its parent`() {
        val executor = FakeExecutor()
        val plugin = SculkScope.root("plugin", executor) {}
        val player = plugin.child("player")
        val job = player.launch { awaitCancellation() }
        executor.tick()
        plugin.close()
        assertTrue(job.isCancelled)
        assertTrue(player.isClosed)
    }

    @Test
    fun `a region batch runs every item in its owning region and keeps input order`() {
        val executor = FakeExecutor()
        val blocks = listOf(0 to 0, 300 to 0, 5 to 5, -1 to -1, 310 to 3)
        val results = executor.run {
            regionBatch(
                blocks,
                locate = { (x, z) -> ExecutionContext.Region.ofBlock(world, x, z) },
            ) { (x, z) ->
                val owned = executor.owns(ExecutionContext.Region.ofBlock(world, x, z))
                "$x,$z:$owned"
            }
        }
        assertEquals(listOf("0,0:true", "300,0:true", "5,5:true", "-1,-1:true", "310,3:true"), results)
        val sections = executor.history.filterIsInstance<ExecutionContext.Region>().map { it.section(executor.regionShift) }.toSet()
        assertEquals(setOf(SectionPosition(0, 0), SectionPosition(1, 0), SectionPosition(-1, -1)), sections)
    }

    @Test
    fun `a region batch groups by the platform's section width, not a default`() {
        val executor = FakeExecutor(regionShift = 2)
        val chunks = listOf(0, 3, 4, 7, 8)
        executor.run {
            regionBatch(chunks, locate = { ExecutionContext.Region(world, it, 0) }) { chunk ->
                check(executor.owns(ExecutionContext.Region(world, chunk, 0))) { "chunk $chunk ran off its region" }
            }
        }
        val groups = executor.history.filterIsInstance<ExecutionContext.Region>().map { it.section(2) }
        assertEquals(listOf(SectionPosition(0, 0), SectionPosition(1, 0), SectionPosition(2, 0)), groups)
    }

    @Test
    fun `a region context names its chunk, so any section width finds the same chunk`() {
        val region = ExecutionContext.Region.ofBlock(world, 1_600, -1)
        assertEquals(100, region.chunkX)
        assertEquals(-1, region.chunkZ)
        assertEquals(SectionPosition(6, -1), region.section(4))
        assertEquals(SectionPosition(3, -1), region.section(5))
    }

    @Test
    fun `sections floor towards negative infinity like chunk coordinates`() {
        assertEquals(SectionPosition(-1, -1), SectionPosition.ofChunk(-1, -16, 4))
        assertEquals(SectionPosition(0, 0), SectionPosition.ofChunk(15, 15, 4))
        assertEquals(SectionPosition(1, 0), SectionPosition.ofBlock(256, 0, 4))
        assertEquals((3L shl 32) or (5L and 0xFFFFFFFFL), SectionPosition(5, 3).packed)
        assertEquals(0xFFFFFFFFL, SectionPosition(-1, 0).packed)
    }

    @Test
    fun `a proxy scope starts its coroutines on the proxy executor`() {
        val executor = FakeExecutor(ThreadModel.PROXY)
        assertTrue(executor.run { executor.owns(ExecutionContext.Proxy) })
    }

    @Test
    fun `delayTicks of zero waits for the next tick instead of returning at once`() {
        val executor = FakeExecutor()
        val waited = executor.run {
            withGlobal {
                val start = executor.currentTick
                delayTicks(0)
                executor.currentTick - start
            }
        }
        assertEquals(1L, waited)
    }

    @Test
    fun `closing a scope cancels its coroutines before its resources close`() {
        val executor = FakeExecutor()
        val scope = SculkScope.root("plugin", executor) {}
        val job = scope.launch { awaitCancellation() }
        var cancelledFirst = false
        scope.resources.onClose { cancelledFirst = job.isCancelled }
        executor.tick()
        scope.close()
        assertTrue(cancelledFirst)
    }

    @Test
    fun `ownership belongs to the ticking thread, never to a thread that merely runs at the same time`() {
        val executor = FakeExecutor()
        var ownedElsewhere = true
        executor.run {
            withGlobal {
                val other = Thread { ownedElsewhere = executor.owns(ExecutionContext.Global) }
                other.start()
                other.join()
            }
        }
        assertFalse(ownedElsewhere)
    }

    @Test
    fun `world keys must be namespaced`() {
        assertThrows<IllegalArgumentException> { WorldKey("world") }
    }
}
