package studio.sculk.scene

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.execution.SculkScope
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.text.Text

/**
 * Shows [SceneSpec]s as display entities (ADR-0032). Every scene it spawns ends when its scope
 * closes, and its entities are never saved with the world, so none outlives the plugin.
 *
 * Thread-safe: call it from any thread. Entities are only ever touched on the thread that owns
 * them, the main thread on Paper and the owning region on Folia and Canvas.
 */
@SculkExperimental
public interface Scenes {
    /** The scenes spawned here and not yet closed. */
    public val open: Collection<Scene>

    /**
     * Shows [spec] at [anchor] to [visibility] and returns at once. The entities appear on the next
     * tick of the region owning the anchor, or when its chunk next loads; nothing loads a chunk.
     *
     * Fails with [SculkFailure.UnsupportedCapability], showing nothing, when a node has per-viewer
     * text and [visibility] is everyone: one entity carries one text for all its viewers, and
     * different text per viewer for everyone in range needs packets (ADR-0032).
     */
    public fun spawn(spec: SceneSpec, anchor: Anchor, visibility: Visibility = Visibility.Everyone): SculkResult<Scene>
}

/**
 * One spawned scene. Every member is thread-safe and returns at once; the change reaches the
 * entities on their own threads within a tick or two.
 */
@SculkExperimental
public interface Scene {
    /** The spec most recently asked for, which the entities show or are about to. */
    public val spec: SceneSpec

    /** Where the scene stands now. */
    public val anchor: Anchor

    /** Who the scene was spawned for, with later shows and hides of chosen players applied. */
    public val visibility: Visibility

    /** False once [close] ran, the scope closed or a followed entity left the world. */
    public val isOpen: Boolean

    /**
     * Whether every display of the current spec exists in the world: false until the anchor's chunk
     * is loaded and the region has spawned them, and again while a chunk unload has removed them.
     */
    public val isSpawned: Boolean

    /**
     * Shows [spec] instead, changing only what differs, by node id: a changed transform slides over
     * its node's `interpolationTicks`, changed text is sent alone, a node whose kind changed is
     * replaced, and nodes no longer in [spec] are removed. Fails as [Scenes.spawn] does. Does nothing
     * once the scene is closed.
     */
    public fun update(spec: SceneSpec): SculkResult<Unit>

    /**
     * Moves the scene to [anchor]. A short move glides over the spec's `teleportTicks`; a move to
     * another world or further than [JUMP_BLOCKS] spawns the displays afresh where they arrive.
     */
    public fun move(anchor: Anchor)

    /** Lets [player] see the scene: adds them to the chosen players, or stops hiding it from them. */
    public fun show(player: Player)

    /** Hides the scene from [player]: removes them from the chosen players, or hides it from them alone. */
    public fun hide(player: Player)

    /** Whether the scene is meant to be shown to [player], whether or not they are in range. */
    public fun isShownTo(player: Player): Boolean

    /** Renders every text again, for values that changed without a placeholder signalling it. */
    public fun refresh()

    /** Removes the scene's entities and ends it. Closing twice does nothing. */
    public fun close()

    @SculkExperimental
    public companion object {
        /** How far a scene moves in one step before it is spawned afresh rather than glided. */
        public const val JUMP_BLOCKS: Double = 16.0
    }
}

/**
 * Scenes over Paper's display entities, on Paper, Folia and Canvas. Templates render with [text]'s
 * theme and [placeholders]: shared text with global placeholders, or the followed entity's own
 * when it is a player; per-viewer text for each viewer, and for the pair when following a player.
 * A text redraws when a placeholder it used signals a change, and only when its output differs.
 */
@SculkExperimental
public fun Scenes(plugin: Plugin, scope: SculkScope, placeholders: PlaceholderRegistry? = null, text: Text = Text()): Scenes =
    PaperScenes(plugin, scope, placeholders, text)
