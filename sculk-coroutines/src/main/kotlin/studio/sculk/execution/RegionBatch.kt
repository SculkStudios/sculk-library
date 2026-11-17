package studio.sculk.execution

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import studio.sculk.annotation.SculkStable

/**
 * Runs [action] for every item on the thread owning its region and returns the results in input
 * order. Items are grouped by the platform's region section ([SculkExecutor.regionShift]); groups
 * run concurrently, items within a group run one after another in input order, within the owning
 * region.
 *
 * Nothing is atomic across groups: no ordering between groups, no common tick. On Paper every
 * group runs on the main thread, still in input order within each group.
 */
@SculkStable
public suspend fun <T, R> regionBatch(items: List<T>, locate: (T) -> ExecutionContext.Region, action: suspend (T) -> R): List<R> {
    if (items.isEmpty()) return emptyList()
    val execution = currentExecution()
    val shift = execution.executor.regionShift
    require(shift in 0..31) { "A section shift is between 0 and 31, but was $shift." }
    val groups = ArrayList<Group>()
    val byWorld = HashMap<WorldKey, HashMap<Long, Group>>()
    var last: Group? = null
    items.forEachIndexed { index, item ->
        val region = locate(item)
        val section = SectionPosition.pack(region.chunkX shr shift, region.chunkZ shr shift)
        // Consecutive items usually share a section; reusing the last group skips the lookup and its boxing.
        val group = last?.takeIf { it.section == section && it.region.world == region.world }
            ?: byWorld.getOrPut(region.world) { HashMap() }.getOrPut(section) { Group(region, section).also(groups::add) }
        group.add(index)
        last = group
    }
    val results = arrayOfNulls<Any?>(items.size)
    coroutineScope {
        groups.map { group ->
            async(execution.dispatcher(group.region)) {
                for (position in 0 until group.size) {
                    val index = group.indices[position]
                    results[index] = action(items[index])
                }
            }
        }.awaitAll()
    }
    @Suppress("UNCHECKED_CAST")
    return results.toList() as List<R>
}

private class Group(val region: ExecutionContext.Region, val section: Long) {
    var indices = IntArray(INITIAL_CAPACITY)
        private set
    var size = 0
        private set

    fun add(index: Int) {
        if (size == indices.size) indices = indices.copyOf(size * 2)
        indices[size++] = index
    }

    private companion object {
        const val INITIAL_CAPACITY = 8
    }
}
