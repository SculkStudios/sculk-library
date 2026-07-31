package studio.sculk.packets

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler

/**
 * What a client reported doing to a block, normalised across packet backends.
 */
@SculkStable
public enum class BlockDigAction {
    /** The player started breaking the block. Instant-break blocks report only this. */
    Start,

    /** The player stopped breaking before the block gave way. */
    Abort,

    /** The player finished breaking the block. */
    Finish,

    /** Any other digging-channel action (dropping items, swapping hands, releasing a bow). */
    Other,
}

/**
 * Shared machinery for a client block action the server can take over.
 *
 * Handlers run on the packet thread, off the main/region thread. Use [runSync] — or the
 * [intercept] helper, which schedules for you — before touching Bukkit APIs.
 *
 * The [player]'s client predicts the outcome of its own action and refuses to render *any*
 * server block update at [location] until the matching [sequence] is acknowledged. Cancelling
 * the packet suppresses vanilla's acknowledgement, so a handler that cancels **must** call
 * [acknowledge] once it has pushed the state it wants the client to settle on; [intercept]
 * does both in the right order.
 */
@SculkStable
public abstract class ClientBlockContext(
    public val player: Player,
    public val world: World,
    public val x: Int,
    public val y: Int,
    public val z: Int,
    public val sequence: Int,
    private val scheduler: SculkScheduler,
    private val cancelAction: () -> Unit,
    private val acknowledgeAction: () -> Unit,
) {
    /** The block's position. Allocates; prefer [x]/[y]/[z] on the packet thread. */
    public val location: Location
        get() = Location(world, x.toDouble(), y.toDouble(), z.toDouble())

    public var cancelled: Boolean = false
        private set

    public var acknowledged: Boolean = false
        private set

    /** Stops the server from applying this action to the real world. */
    public fun cancel() {
        if (cancelled) return
        cancelled = true
        cancelAction()
    }

    /**
     * Closes the client's block prediction at this position, letting it render whatever the
     * server has most recently sent there. Safe to call more than once; only the first call
     * sends a packet.
     */
    public fun acknowledge() {
        if (acknowledged || sequence < 0) return
        acknowledged = true
        acknowledgeAction()
    }

    /**
     * Cancels the action, runs [block] on the safe sync context for [player], then acknowledges
     * — the ordering the client needs, so [block] can send its own block changes and have them
     * render immediately.
     */
    public fun intercept(block: () -> Unit): SculkHandle {
        cancel()
        return runSync {
            block()
            acknowledge()
        }
    }

    @SculkStable
    public fun intercept(block: Runnable): SculkHandle = intercept { block.run() }

    /** Runs [block] on the safe sync context for [player]. */
    public fun runSync(block: () -> Unit): SculkHandle = scheduler.runSync(player, Runnable(block))
}

/** A single client dig report. See [ClientBlockContext] for the cancel/acknowledge contract. */
@SculkStable
public class BlockDigContext(
    player: Player,
    world: World,
    x: Int,
    y: Int,
    z: Int,
    public val action: BlockDigAction,
    sequence: Int,
    scheduler: SculkScheduler,
    cancelAction: () -> Unit,
    acknowledgeAction: () -> Unit,
) : ClientBlockContext(player, world, x, y, z, sequence, scheduler, cancelAction, acknowledgeAction)

/**
 * A client right-clicking a block — the other half of taking a block over.
 *
 * Interacting opens the same kind of prediction a dig does, so a virtual block that ignores
 * this will snap back to the real one the moment a player right-clicks it.
 */
@SculkStable
public class BlockUseContext(
    player: Player,
    world: World,
    x: Int,
    y: Int,
    z: Int,
    /** The clicked face, as a Bukkit [org.bukkit.block.BlockFace] name, or null if unknown. */
    public val face: String?,
    /** True when the off hand was used. */
    public val offHand: Boolean,
    sequence: Int,
    scheduler: SculkScheduler,
    cancelAction: () -> Unit,
    acknowledgeAction: () -> Unit,
) : ClientBlockContext(player, world, x, y, z, sequence, scheduler, cancelAction, acknowledgeAction)

/**
 * Backend-supplied client-block capabilities that cannot be expressed with `sendBlockChange`
 * alone. Packet backends implement this; plugins reach it through [ClientBlockService].
 */
@SculkStable
public interface ClientBlockBackend {
    /** Sends a block-change acknowledgement for [sequence] to [player]. */
    public fun acknowledge(player: Player, sequence: Int): SculkResult<Unit>

    /** Registers a dig listener. The handler runs on the packet thread. */
    public fun listenDig(priority: PacketPriority, handler: BlockDigContext.() -> Unit): SculkResult<SculkHandle>

    /** Registers a block-use (right click) listener. The handler runs on the packet thread. */
    public fun listenUse(priority: PacketPriority, handler: BlockUseContext.() -> Unit): SculkResult<SculkHandle>
}
