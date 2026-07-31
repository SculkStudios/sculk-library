package studio.sculk.packets

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/** How a display entity turns to face the viewer. */
@SculkStable
public enum class Billboard {
    FIXED,
    VERTICAL,
    HORIZONTAL,
    CENTER,
}

/** What a virtual text display looks like. */
@SculkStable
public data class TextDisplayStyle(
    public val billboard: Billboard = Billboard.CENTER,
    public val scale: Float = 1.0f,
    /** ARGB. A fully transparent value is the only way to say "no plate" — see [background]. */
    public val backgroundArgb: Int = 0,
    public val shadowed: Boolean = false,
    public val seeThrough: Boolean = false,
    public val lineWidth: Int = 200,
    public val textOpacity: Byte = -1,
) {
    /**
     * Whether a background plate is drawn.
     *
     * Turning the default background off is not enough on its own: the client still draws a plate
     * unless the colour is *also* fully transparent. Both are set together here so a caller cannot
     * get the half-configured version that looks fine in a screenshot and wrong in game.
     */
    public val background: Boolean get() = backgroundArgb ushr 24 != 0
}

/**
 * Entities that exist only in a client's view.
 *
 * The point of a virtual entity is that the server has none: nothing is ticked, nothing is tracked,
 * nothing is saved, and a crash cannot leave orphaned armour stands floating over spawn. The cost
 * is that everything — spawning, metadata, movement, removal — is a packet the caller must send.
 *
 * This lives in `sculk-packets-api` rather than beside the hologram code so that `sculk-visual`
 * stays backend-neutral. Without it, the hologram service imported PacketEvents directly, which
 * meant the ProtocolLib backend could never serve holograms at all.
 *
 * Entity ids must not collide with real ones. Use [reserveEntityId].
 */
@SculkStable
public interface VirtualEntityService {
    /** True when the active backend can do any of this. */
    public val available: Boolean

    /**
     * An entity id no real entity will use.
     *
     * Counts down from [Int.MAX_VALUE] because the server counts up; a shared counter is the only
     * way two callers can avoid picking the same id.
     */
    public fun reserveEntityId(): Int

    /** Shows a text display at [location] to [player]. */
    public fun spawnTextDisplay(
        player: Player,
        entityId: Int,
        location: Location,
        text: Component,
        style: TextDisplayStyle = TextDisplayStyle(),
    ): SculkResult<Unit>

    /** Replaces the text and style of an already-spawned display. */
    public fun updateTextDisplay(
        player: Player,
        entityId: Int,
        text: Component,
        style: TextDisplayStyle = TextDisplayStyle(),
    ): SculkResult<Unit>

    /** Moves an entity. Cheaper than despawning and respawning, and the client interpolates it. */
    public fun teleport(player: Player, entityId: Int, location: Location): SculkResult<Unit>

    /**
     * Makes [passengerIds] ride [vehicleId] in [player]'s view.
     *
     * The reason nametags ride their wearer rather than being teleported each tick: a mounted
     * entity is interpolated by the client, where a teleported one visibly swims behind the player
     * at anything above a walk.
     */
    public fun mount(player: Player, vehicleId: Int, passengerIds: List<Int>): SculkResult<Unit>

    /** Removes entities from [player]'s view. */
    public fun despawn(player: Player, entityIds: List<Int>): SculkResult<Unit>
}

/**
 * The service when no backend is loaded.
 *
 * Every call fails by name rather than throwing, so a plugin that wants holograms still enables on
 * a server without PacketEvents — it simply has no holograms, and says so once.
 */
@SculkStable
public object UnavailableVirtualEntityService : VirtualEntityService {
    private const val REASON = "No packet backend is loaded, so virtual entities are unavailable."

    override val available: Boolean get() = false

    override fun reserveEntityId(): Int = 0

    override fun spawnTextDisplay(
        player: Player,
        entityId: Int,
        location: Location,
        text: Component,
        style: TextDisplayStyle,
    ): SculkResult<Unit> = SculkResult.failure(REASON)

    override fun updateTextDisplay(player: Player, entityId: Int, text: Component, style: TextDisplayStyle): SculkResult<Unit> =
        SculkResult.failure(REASON)

    override fun teleport(player: Player, entityId: Int, location: Location): SculkResult<Unit> = SculkResult.failure(REASON)

    override fun mount(player: Player, vehicleId: Int, passengerIds: List<Int>): SculkResult<Unit> = SculkResult.failure(REASON)

    override fun despawn(player: Player, entityIds: List<Int>): SculkResult<Unit> = SculkResult.failure(REASON)
}
