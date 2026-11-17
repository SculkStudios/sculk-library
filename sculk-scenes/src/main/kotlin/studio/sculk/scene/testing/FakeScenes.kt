package studio.sculk.scene.testing

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.failureOrNull
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.scene.Anchor
import studio.sculk.scene.Scene
import studio.sculk.scene.SceneRules
import studio.sculk.scene.SceneSpec
import studio.sculk.scene.Scenes
import studio.sculk.scene.TextNode
import studio.sculk.scene.Viewers
import studio.sculk.scene.Visibility
import studio.sculk.text.Text
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [Scenes] for tests of code that spawns and updates scenes, without a server. It keeps every
 * scene, spec, show and hide, refuses what the real one refuses with the same failures, and renders
 * text exactly as the real one would with [placeholders] and [text].
 *
 * It has no world, so it cannot model chunks or entities: [FakeScene.isSpawned] throws. Test what
 * a client sees on a server, as `integration-tests/scenes` does. Thread-safe.
 */
@SculkExperimental
public class FakeScenes(private val placeholders: PlaceholderRegistry? = null, private val text: Text = Text()) : Scenes {
    private val all = CopyOnWriteArrayList<FakeScene>()

    /** Every scene spawned here, closed or not, in spawn order. */
    public val spawned: List<FakeScene> get() = all.toList()

    override val open: Collection<Scene> get() = all.filter { it.isOpen }

    override fun spawn(spec: SceneSpec, anchor: Anchor, visibility: Visibility): SculkResult<Scene> {
        val viewers = Viewers.of(visibility)
        SceneRules.check(spec, viewers).failureOrNull()?.let { return it }
        val scene = FakeScene(spec, anchor, viewers, placeholders, text)
        all += scene
        return SculkResult.success(scene)
    }
}

/** A scene of [FakeScenes]: what was asked of it, and the text it would show. */
@SculkExperimental
public class FakeScene internal constructor(
    spec: SceneSpec,
    anchor: Anchor,
    viewers: Viewers,
    private val placeholders: PlaceholderRegistry?,
    private val text: Text,
) : Scene {
    private val lock = Any()
    private val specs = CopyOnWriteArrayList(listOf(spec))
    private val moves = CopyOnWriteArrayList(listOf(anchor))

    @Volatile private var viewers: Viewers = viewers

    @Volatile override var isOpen: Boolean = true
        private set

    /** How many times [refresh] was called. */
    @Volatile public var refreshes: Int = 0
        private set

    override val spec: SceneSpec get() = specs.last()

    override val anchor: Anchor get() = moves.last()

    override val visibility: Visibility get() = viewers.asVisibility()

    /** Every spec this scene showed, the spawned one first; an update while closed adds none. */
    public val history: List<SceneSpec> get() = specs.toList()

    /** Every anchor this scene stood at, the spawned one first. */
    public val anchors: List<Anchor> get() = moves.toList()

    override val isSpawned: Boolean
        get() = throw UnsupportedOperationException("FakeScenes has no world, so nothing spawns; test spawning on a server.")

    override fun update(spec: SceneSpec): SculkResult<Unit> {
        SceneRules.check(spec, viewers).failureOrNull()?.let { return it }
        if (isOpen) specs += spec
        return SculkResult.ok()
    }

    override fun move(anchor: Anchor) {
        if (isOpen) moves += anchor
    }

    override fun show(player: Player) {
        synchronized(lock) { viewers = viewers.show(player.uniqueId) }
    }

    override fun hide(player: Player) {
        synchronized(lock) { viewers = viewers.hide(player.uniqueId) }
    }

    override fun isShownTo(player: Player): Boolean = viewers.sees(player.uniqueId)

    override fun refresh() {
        synchronized(lock) { refreshes++ }
    }

    override fun close() {
        isOpen = false
    }

    /**
     * The text node [id] as [viewer] would see it now, or as everyone sees shared text when [viewer]
     * is null: rendered with the placeholders a server render would use. Throws when the current
     * spec has no text node [id], or when per-viewer text is asked for without a viewer.
     */
    public fun textOf(id: String, viewer: Audience? = null): Component {
        val node = spec.requireNode(id)
        require(node is TextNode) { "node '$id' is not a text node" }
        require(viewer != null || !node.perViewer) { "node '$id' has per-viewer text; name the viewer" }
        val target = (anchor as? Anchor.Following)?.entity
        val shownTo = viewer.takeIf { node.perViewer }
        val resolver = placeholders?.let { registry ->
            when {
                shownTo != null && target != null -> registry.resolver(shownTo, target)
                shownTo != null -> registry.resolver(shownTo)
                target != null -> registry.resolver(target)
                else -> registry.resolver()
            }
        } ?: TagResolver.empty()
        return text.render(node.text, resolver)
    }
}
