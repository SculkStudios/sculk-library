package studio.sculk.execution

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import studio.sculk.annotation.SculkStable

/**
 * Runs [action] for every item on the thread owning its region and returns the results in input
 * order. Items are grouped by region section; groups run concurrently, items within a group run
 * one after another in input order, within the owning region.
 *
 * Nothing is atomic across groups: no ordering between groups, no common tick. On Paper every
 * group runs on the main thread, still in input order within each group.
 */
@SculkStable
public suspend fun <T, R> regionBatch(items: List<T>, locate: (T) -> ExecutionContext.Region, action: suspend (T) -> R): List<R> {
    if (items.isEmpty()) return emptyList()
    val execution = currentExecution()
    val groups = LinkedHashMap<ExecutionContext.Region, MutableList<Int>>()
    items.forEachIndexed { index, item -> groups.getOrPut(locate(item)) { mutableListOf() } += index }
    val results = arrayOfNulls<Any?>(items.size)
    coroutineScope {
        groups.map { (region, indices) ->
            async(execution.dispatcher(region)) {
                for (index in indices) results[index] = action(items[index])
            }
        }.awaitAll()
    }
    @Suppress("UNCHECKED_CAST")
    return results.toList() as List<R>
}
