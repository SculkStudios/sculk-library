package studio.sculk.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkHandle

class TaskTrackerTest {
    private val events = mutableListOf<String>()
    private val tracker = TaskTracker("Test executor")

    private class Submitted(val task: TaskTracker.Task, val handle: SculkHandle)

    private fun submit(name: String, repeating: Boolean = false, onCancel: () -> Unit = {}): Submitted {
        var task: TaskTracker.Task? = null
        val work = object : SculkExecutor.Work {
            override fun run() {
                events += "$name ran"
            }

            override fun retired() {
                events += "$name retired"
            }
        }
        val handle = tracker.submit(work, repeating) { started ->
            task = started
            onCancel
        }
        return Submitted(checkNotNull(task), handle)
    }

    @Test
    fun `a one-shot task runs once and is then forgotten`() {
        val save = submit("save")
        save.task.run()
        save.task.run()
        save.task.retire()
        assertEquals(listOf("save ran"), events)
        assertEquals(0, tracker.size)
    }

    @Test
    fun `a task the platform refuses is retired at once`() {
        val work = object : SculkExecutor.Work {
            override fun run() {
                events += "follow ran"
            }

            override fun retired() {
                events += "follow retired"
            }
        }
        tracker.submit(work, repeating = false) { null }
        assertEquals(listOf("follow retired"), events)
        assertEquals(0, tracker.size)
    }

    @Test
    fun `cancelling through the handle neither runs nor retires`() {
        var platformCancelled = false
        val tick = submit("tick") { platformCancelled = true }
        tick.handle.close()
        tick.task.run()
        assertEquals(emptyList<String>(), events)
        assertTrue(platformCancelled)
    }

    @Test
    fun `shutdown retires pending work instead of dropping it, and refuses new work`() {
        var cancelled = 0
        submit("a") { cancelled++ }
        submit("b", repeating = true) { cancelled++ }
        tracker.shutdown()
        assertEquals(listOf("a retired", "b retired"), events.sorted())
        assertEquals(2, cancelled)
        assertThrows<IllegalStateException> { submit("c") }
    }

    @Test
    fun `a repeating task keeps running until it is claimed`() {
        val pulse = submit("pulse", repeating = true)
        pulse.task.run()
        pulse.task.run()
        pulse.handle.close()
        pulse.task.run()
        assertEquals(listOf("pulse ran", "pulse ran"), events)
    }
}
