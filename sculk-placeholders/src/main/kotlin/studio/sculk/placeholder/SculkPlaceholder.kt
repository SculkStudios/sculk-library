package studio.sculk.placeholder

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import studio.sculk.SculkFailure
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.map
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A registered placeholder: global, per audience, or relational. Values of type [T] are kept and
 * refreshed by its [refresh] policy, and shown through its format, so a value is never parsed as
 * MiniMessage unless the placeholder itself builds a component from it.
 *
 * Closing it unregisters it; the registry's scope closes it too. Thread-safe.
 */
@SculkStable
public sealed class SculkPlaceholder<T : Any>(
    public val key: PlaceholderKey,
    public val refresh: Refresh,
    /** True when values load in the async context while renders show the last one. */
    public val isAsync: Boolean,
    arguments: List<PlaceholderArgument<*>>,
    private val format: (T) -> ComponentLike,
    internal val registry: PlaceholderRegistry,
) : SculkHandle {
    /** The arguments its tag takes, in order. */
    public val arguments: List<PlaceholderArgument<*>> = arguments.toList()

    private val closed = AtomicBoolean()
    private val sources = CopyOnWriteArraySet<SculkPlaceholder<*>>()
    private val links = CopyOnWriteArrayList<SculkHandle>()

    init {
        require(this.arguments.distinctBy { it.name }.size == this.arguments.size) { "<${key.tag}> declares two arguments with one name." }
        require(this.arguments.dropWhile { !it.isOptional }.all { it.isOptional }) {
            "<${key.tag}> declares a required argument after an optional one."
        }
    }

    public val isClosed: Boolean get() = closed.get()

    /** [value] as this placeholder shows it; null shows as nothing. */
    public fun render(value: T?): Component = value?.let { format(it).asComponent() } ?: Component.empty()

    /** Marks every value stale and signals [PlaceholderChange.Everyone]. */
    public abstract fun invalidate()

    /**
     * Invalidates this placeholder whenever [source] signals a change: for the same audience or
     * pair when both placeholders are of the same kind, for everyone otherwise. For a value derived
     * from another placeholder's. Lasts until either is closed. Linking across registries or in a
     * cycle is a wiring bug and throws.
     */
    public fun invalidateWith(source: SculkPlaceholder<*>) {
        require(source.registry === registry) { "${source.key} belongs to another placeholder registry." }
        require(!source.reaches(this)) { "Invalidating $key with ${source.key} would form a cycle." }
        sources += source
        links += registry.listen { change -> if (change.key == source.key) invalidateFor(change) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        links.forEach(SculkHandle::close)
        links.clear()
        values.close()
        registry.unregister(this)
    }

    override fun toString(): String = "${this::class.simpleName}($key)"

    internal abstract fun accepts(bound: Bound): Boolean

    internal abstract fun component(bound: Bound, arguments: List<String>): SculkResult<Component>

    internal abstract fun invalidateFor(change: PlaceholderChange)

    internal abstract val values: ValueStore<*, T>

    internal fun signal(change: PlaceholderChange) {
        if (!isClosed) registry.signal(change)
    }

    private fun reaches(other: SculkPlaceholder<*>): Boolean = this === other || sources.any { it.reaches(other) }

    /** The tag's [raw] arguments read against [arguments]; a missing, extra or invalid one is a failure naming it. */
    internal fun parse(raw: List<String>): SculkResult<List<Any>> {
        if (raw.size > arguments.size) {
            return SculkFailure.InvalidInput("<${key.tag}>", "at most ${arguments.size} arguments", "${raw.size}")
        }
        val parsed = arguments.mapIndexed { index, argument ->
            val text = raw.getOrNull(index)
            val value = if (text == null) argument.default else argument.parse(text)
            value
                ?: return SculkFailure.InvalidInput(
                    "<${key.tag}> argument $argument",
                    argument.expected,
                    text?.let {
                        "'$it'"
                    } ?: "nothing",
                )
        }
        return SculkResult.success(parsed)
    }
}

/**
 * A placeholder with one value for everyone, such as the number of players online. Its tag
 * resolves in every template.
 */
@SculkStable
public class GlobalPlaceholder<T : Any> internal constructor(
    key: PlaceholderKey,
    refresh: Refresh,
    arguments: List<PlaceholderArgument<*>>,
    format: (T) -> ComponentLike,
    registry: PlaceholderRegistry,
    source: ValueSource<PlaceholderContext, T>,
) : SculkPlaceholder<T>(key, refresh, source is ValueSource.Async, arguments, format, registry) {
    override val values: ValueStore<PlaceholderContext, T> = registry.store(refresh, source) { signal(PlaceholderChange.Everyone(key)) }

    /**
     * The value for [arguments] under the refresh policy: computed now, kept, or for an async
     * placeholder the last loaded value while a newer one loads. Invalid arguments are a failure.
     */
    public fun value(arguments: List<String> = emptyList()): SculkResult<T?> =
        parse(arguments).map { values.read(Slot(null, null, arguments), PlaceholderContext(key, this.arguments, it)) }

    override fun invalidate() {
        values.invalidate(null)
        signal(PlaceholderChange.Everyone(key))
    }

    override fun accepts(bound: Bound): Boolean = true

    override fun component(bound: Bound, arguments: List<String>): SculkResult<Component> = value(arguments).map(::render)

    override fun invalidateFor(change: PlaceholderChange): Unit = invalidate()
}

/**
 * A placeholder with a value per audience, such as a player's balance. Its tag resolves where a
 * template is rendered for an audience; elsewhere it is left as written.
 */
@SculkStable
public class AudiencePlaceholder<T : Any> internal constructor(
    key: PlaceholderKey,
    refresh: Refresh,
    arguments: List<PlaceholderArgument<*>>,
    format: (T) -> ComponentLike,
    registry: PlaceholderRegistry,
    source: ValueSource<AudienceContext, T>,
) : SculkPlaceholder<T>(key, refresh, source is ValueSource.Async, arguments, format, registry) {
    override val values: ValueStore<AudienceContext, T> = registry.store(refresh, source) { signal(changeOf(it.subject)) }

    /** The value for [audience] and [arguments] under the refresh policy, as [GlobalPlaceholder.value] describes. */
    public fun value(audience: Audience, arguments: List<String> = emptyList()): SculkResult<T?> =
        parse(arguments).map { values.read(Slot(identity(audience), null, arguments), AudienceContext(key, this.arguments, it, audience)) }

    override fun invalidate() {
        values.invalidate(null)
        signal(PlaceholderChange.Everyone(key))
    }

    /** Marks [audience]'s values stale and signals the change, such as after a payment. */
    public fun invalidate(audience: Audience): Unit = invalidateSubject(identity(audience))

    /** Marks the values of the audience with [uniqueId] stale, for changes known only by id, such as an offline payment. */
    public fun invalidate(uniqueId: UUID): Unit = invalidateSubject(uniqueId)

    private fun invalidateSubject(subject: Any) {
        values.invalidate { it.subject == subject }
        signal(changeOf(subject))
    }

    private fun changeOf(subject: Any?): PlaceholderChange =
        if (subject is UUID) PlaceholderChange.Audience(key, subject) else PlaceholderChange.Everyone(key)

    override fun accepts(bound: Bound): Boolean = bound.audience != null

    override fun component(bound: Bound, arguments: List<String>): SculkResult<Component> =
        value(checkNotNull(bound.audience) { "<${key.tag}> needs an audience." }, arguments).map(::render)

    override fun invalidateFor(change: PlaceholderChange): Unit = when (change) {
        is PlaceholderChange.Audience -> invalidate(change.uniqueId)
        is PlaceholderChange.Everyone, is PlaceholderChange.Relation -> invalidate()
    }
}

/**
 * A placeholder with a value per viewer and target, such as a nametag colour that depends on
 * whether the viewer and the target share a team. Its tag resolves where a template is rendered
 * for a viewer and a target; elsewhere it is left as written.
 */
@SculkStable
public class RelationalPlaceholder<T : Any> internal constructor(
    key: PlaceholderKey,
    refresh: Refresh,
    arguments: List<PlaceholderArgument<*>>,
    format: (T) -> ComponentLike,
    registry: PlaceholderRegistry,
    source: ValueSource<RelationalContext, T>,
) : SculkPlaceholder<T>(key, refresh, source is ValueSource.Async, arguments, format, registry) {
    override val values: ValueStore<RelationalContext, T> = registry.store(refresh, source) { signal(changeOf(it.subject, it.target)) }

    /** The value for [viewer] seeing [target] with [arguments], as [GlobalPlaceholder.value] describes. */
    public fun value(viewer: Audience, target: Audience, arguments: List<String> = emptyList()): SculkResult<T?> = parse(arguments).map {
        values.read(Slot(identity(viewer), identity(target), arguments), RelationalContext(key, this.arguments, it, viewer, target))
    }

    override fun invalidate() {
        values.invalidate(null)
        signal(PlaceholderChange.Everyone(key))
    }

    /** Marks the values for [viewer] seeing [target] stale and signals the change. */
    public fun invalidate(viewer: Audience, target: Audience): Unit = invalidatePair(identity(viewer), identity(target))

    private fun invalidatePair(viewer: Any, target: Any) {
        values.invalidate { it.subject == viewer && it.target == target }
        signal(changeOf(viewer, target))
    }

    private fun changeOf(viewer: Any?, target: Any?): PlaceholderChange =
        if (viewer is UUID && target is UUID) PlaceholderChange.Relation(key, viewer, target) else PlaceholderChange.Everyone(key)

    override fun accepts(bound: Bound): Boolean = bound.viewer != null && bound.target != null

    override fun component(bound: Bound, arguments: List<String>): SculkResult<Component> {
        val viewer = checkNotNull(bound.viewer) { "<${key.tag}> needs a viewer." }
        val target = checkNotNull(bound.target) { "<${key.tag}> needs a target." }
        return value(viewer, target, arguments).map(::render)
    }

    override fun invalidateFor(change: PlaceholderChange): Unit = when (change) {
        is PlaceholderChange.Relation -> invalidatePair(change.viewer, change.target)
        is PlaceholderChange.Everyone, is PlaceholderChange.Audience -> invalidate()
    }
}
