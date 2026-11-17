package studio.sculk.scene

import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.placeholder.PlaceholderChange
import java.util.UUID

/** One entity of a scene: node [node]'s shared display when [viewer] is null, or [viewer]'s own copy. */
internal data class SlotKey(val node: String, val viewer: UUID?)

/** The decisions a scene makes that need no server, kept apart so they are tested without one. */
internal object SceneRules {
    /** Whether [spec] can be shown to [viewers]; the one rule is that per-viewer text needs chosen viewers. */
    fun check(spec: SceneSpec, viewers: Viewers): SculkResult<Unit> {
        val perViewer = spec.nodes.firstOrNull { it is TextNode && it.perViewer } ?: return SculkResult.ok()
        if (!viewers.everyone) return SculkResult.ok()
        return SculkFailure.UnsupportedCapability(
            "Per-viewer text for everyone in range (node '${perViewer.id}')",
            "a display entity carries one text for all its viewers, and Paper has no API to vary it per player",
            "Show the scene to chosen players with Visibility.only, where each gets their own copy, or render shared text; " +
                "per-viewer text for everyone needs the packet backend planned with sculk-packets-api.",
        )
    }

    /**
     * The entities [spec] needs: one per shared node, and one per node and online chosen viewer for
     * per-viewer text. Offline viewers get none until they join.
     */
    fun slots(spec: SceneSpec, viewers: Viewers, online: (UUID) -> Boolean): List<SlotKey> = spec.nodes.flatMap { node ->
        if (node is TextNode && node.perViewer) {
            viewers.listed.filter(online).sorted().map { SlotKey(node.id, it) }
        } else {
            listOf(SlotKey(node.id, null))
        }
    }

    /**
     * Whether [change] can alter what the entity for [slot] shows. Shared text renders for the
     * followed [target] if any; per-viewer text renders for its viewer, and the pair with [target].
     */
    fun affects(change: PlaceholderChange, slot: SlotKey, target: UUID?): Boolean = when (change) {
        is PlaceholderChange.Everyone -> true
        is PlaceholderChange.Audience -> change.uniqueId == target || change.uniqueId == slot.viewer
        is PlaceholderChange.Relation -> slot.viewer != null && change.viewer == slot.viewer && change.target == target
    }

    /** What following does with one observed position: [STAY] within a hair, [GLIDE] nearby, [RESPAWN] after a jump. */
    enum class Step { STAY, GLIDE, RESPAWN }

    fun step(sameWorld: Boolean, distanceSquared: Double): Step = when {
        !sameWorld || distanceSquared > Scene.JUMP_BLOCKS * Scene.JUMP_BLOCKS -> Step.RESPAWN
        distanceSquared < STILL * STILL -> Step.STAY
        else -> Step.GLIDE
    }

    // A thousandth of a block: below a pixel, and above float noise from a standing entity's position.
    private const val STILL = 0.001
}
