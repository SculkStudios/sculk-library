package studio.sculk.example.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.sculk.SculkResult
import studio.sculk.isSuccess

/**
 * Whether the network admits only players with the bypass permission. A change is saved before it
 * takes effect, so a failed save leaves the network as it was. Logins read [enabled] from other
 * threads while a command changes it.
 */
class Maintenance(enabled: Boolean, private val save: suspend (Boolean) -> SculkResult<Unit>) {
    private val changing = Mutex()

    @Volatile
    var enabled: Boolean = enabled
        private set

    /** Saves [on], then applies it. */
    suspend fun set(on: Boolean): SculkResult<Unit> = changing.withLock {
        save(on).also { if (it.isSuccess) enabled = on }
    }

    /** Whether a player may join now. */
    fun admits(hasBypass: Boolean): Boolean = !enabled || hasBypass
}
