package gg.sculk.core

import gg.sculk.core.annotation.SculkStable

/**
 * A closeable handle representing a registered resource within Sculk Studio.
 *
 * All resources registered via [SculkRuntime.register] return a [SculkHandle].
 * Closing the handle deregisters and cleans up the associated resource.
 *
 * Handles are automatically closed when [SculkRuntime.close] is called.
 *
 * Example:
 * ```kotlin
 * val handle = sculk.register(myResource)
 * // Later:
 * handle.close() // deregisters just this resource
 * ```
 */
@SculkStable
public fun interface SculkHandle : AutoCloseable {
    override fun close()
}
