package studio.sculk.placeholder

import kotlinx.coroutines.CoroutineExceptionHandler
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.SculkScope
import studio.sculk.time.SculkClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A plugin's placeholders, rendered as MiniMessage tags: `shop:balance` is `<shop_balance>`, with
 * arguments as `<shop_balance:formatted>`. Pass a [resolver] to `Text.render`:
 *
 * ```kotlin
 * val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { bank.balance(uniqueId) }
 * text.render("<primary>Balance: <shop_balance>", placeholders.resolver(player))
 * balance.invalidate(player) // after a payment
 * ```
 *
 * A value is shown as its string form, unparsed, or as a component the placeholder builds itself;
 * neither a value nor an argument is ever parsed as MiniMessage.
 *
 * Threading: a synchronous resolver runs on whichever thread renders, which on Folia is any region
 * thread, several at once. It must be thread-safe, must not block and must not touch the world;
 * anything slow is an async placeholder, whose resolver runs in `ExecutionContext.Async` while renders
 * show the last value. Every member of the registry may be called from any thread.
 *
 * Kept values are bounded to [cacheSize] per placeholder, least recently used first out. Every
 * placeholder and change listener belongs to [scope] and ends with it; closing the registry ends
 * them sooner. Registering a key twice is a wiring bug and throws.
 */
@SculkStable
public class PlaceholderRegistry(
    internal val scope: SculkScope,
    internal val clock: SculkClock = SculkClock.SYSTEM,
    public val cacheSize: Int = DEFAULT_CACHE_SIZE,
) : SculkHandle {
    private val lock = Any()
    private val byTag = ConcurrentHashMap<String, SculkPlaceholder<*>>()
    private val listeners = CopyOnWriteArrayList<(PlaceholderChange) -> Unit>()
    private var closed = false

    init {
        require(cacheSize > 0) { "A placeholder cache holds at least one value, but cacheSize was $cacheSize." }
        scope.resources.own(this)
    }

    /** Every open placeholder, ordered by tag: for listing, or for a bridge to another placeholder plugin. */
    public val registered: List<SculkPlaceholder<*>> get() = byTag.values.sortedBy { it.key.tag }

    /** Global placeholders only, for templates with no audience such as a console line or a server list message. */
    public fun resolver(): TagResolver = BoundResolver(this, Bound(null, null, null))

    /** Global placeholders, and audience placeholders for [audience]. */
    public fun resolver(audience: Audience): TagResolver = BoundResolver(this, Bound(audience, null, null))

    /**
     * For a line about [target] as [viewer] sees it, such as a nametag or a chat message: global
     * placeholders, audience placeholders for the target, and relational placeholders for the pair.
     */
    public fun resolver(viewer: Audience, target: Audience): TagResolver = BoundResolver(this, Bound(target, viewer, target))

    /**
     * Calls [listener] with every [PlaceholderChange], on the thread that caused it: the one that
     * invalidated, or the async thread that loaded a new value. It must be quick and thread-safe;
     * one that throws is reported to the scope's failure handler and the others still run.
     */
    public fun onChange(listener: (PlaceholderChange) -> Unit): SculkHandle {
        val registration = listen(listener)
        scope.resources.own(registration)
        return registration
    }

    /**
     * Registers a global placeholder computed by [resolve] on the rendering thread.
     *
     * [format] turns a value into what is shown; by default a component is inserted as built and
     * anything else as its string form, unparsed. A null value shows as nothing.
     */
    public fun <T : Any> global(
        key: String,
        refresh: Refresh = Refresh.Live,
        arguments: List<PlaceholderArgument<*>> = emptyList(),
        format: (T) -> ComponentLike = ::defaultFormat,
        resolve: PlaceholderContext.() -> T?,
    ): GlobalPlaceholder<T> =
        register(GlobalPlaceholder(PlaceholderKey.parse(key), refresh, arguments, format, this, ValueSource.Sync(resolve)))

    /** Registers a placeholder computed per audience by [resolve] on the rendering thread; see [global] for [format]. */
    public fun <T : Any> audience(
        key: String,
        refresh: Refresh = Refresh.Live,
        arguments: List<PlaceholderArgument<*>> = emptyList(),
        format: (T) -> ComponentLike = ::defaultFormat,
        resolve: AudienceContext.() -> T?,
    ): AudiencePlaceholder<T> =
        register(AudiencePlaceholder(PlaceholderKey.parse(key), refresh, arguments, format, this, ValueSource.Sync(resolve)))

    /** Registers a placeholder computed per viewer and target by [resolve] on the rendering thread; see [global] for [format]. */
    public fun <T : Any> relational(
        key: String,
        refresh: Refresh = Refresh.Live,
        arguments: List<PlaceholderArgument<*>> = emptyList(),
        format: (T) -> ComponentLike = ::defaultFormat,
        resolve: RelationalContext.() -> T?,
    ): RelationalPlaceholder<T> =
        register(RelationalPlaceholder(PlaceholderKey.parse(key), refresh, arguments, format, this, ValueSource.Sync(resolve)))

    /**
     * Registers a global placeholder loaded by [resolve] in `ExecutionContext.Async`, for values
     * behind a database or the network. A render never waits: it shows the last loaded value, or
     * [initial] before the first, and starts a load when [refresh] says the value is stale and none
     * is running. A load that delivers a different value signals a change.
     *
     * A failed load is reported to the scope's failure handler; the last value stays shown and the
     * next attempt waits five seconds unless the placeholder is invalidated first.
     */
    public fun <T : Any> globalAsync(
        key: String,
        refresh: Refresh,
        initial: T? = null,
        arguments: List<PlaceholderArgument<*>> = emptyList(),
        format: (T) -> ComponentLike = ::defaultFormat,
        resolve: suspend PlaceholderContext.() -> T?,
    ): GlobalPlaceholder<T> =
        register(GlobalPlaceholder(PlaceholderKey.parse(key), refresh, arguments, format, this, ValueSource.Async(resolve, initial)))

    /** Registers a placeholder loaded per audience by [resolve] in `ExecutionContext.Async`, as [globalAsync] describes. */
    public fun <T : Any> audienceAsync(
        key: String,
        refresh: Refresh,
        initial: T? = null,
        arguments: List<PlaceholderArgument<*>> = emptyList(),
        format: (T) -> ComponentLike = ::defaultFormat,
        resolve: suspend AudienceContext.() -> T?,
    ): AudiencePlaceholder<T> =
        register(AudiencePlaceholder(PlaceholderKey.parse(key), refresh, arguments, format, this, ValueSource.Async(resolve, initial)))

    /** Registers a placeholder loaded per viewer and target by [resolve] in `ExecutionContext.Async`, as [globalAsync] describes. */
    public fun <T : Any> relationalAsync(
        key: String,
        refresh: Refresh,
        initial: T? = null,
        arguments: List<PlaceholderArgument<*>> = emptyList(),
        format: (T) -> ComponentLike = ::defaultFormat,
        resolve: suspend RelationalContext.() -> T?,
    ): RelationalPlaceholder<T> =
        register(RelationalPlaceholder(PlaceholderKey.parse(key), refresh, arguments, format, this, ValueSource.Async(resolve, initial)))

    /** Unregisters every placeholder and listener. The scope does this when it closes; closing twice does nothing. */
    override fun close() {
        val open = synchronized(lock) {
            if (closed) return
            closed = true
            byTag.values.toList()
        }
        listeners.clear()
        open.forEach(SculkPlaceholder<*>::close)
        scope.resources.release(this)
    }

    private fun <P : SculkPlaceholder<*>> register(placeholder: P): P {
        synchronized(lock) {
            check(!closed) { "The placeholder registry of '${scope.name}' is closed; ${placeholder.key} was not registered." }
            val existing = byTag.putIfAbsent(placeholder.key.tag, placeholder)
            check(existing == null) { "Placeholder ${placeholder.key} is already registered; register each key once." }
        }
        return placeholder
    }

    internal fun find(tag: String): SculkPlaceholder<*>? = byTag[tag]

    internal fun unregister(placeholder: SculkPlaceholder<*>) {
        byTag.remove(placeholder.key.tag, placeholder)
    }

    internal fun <C : PlaceholderContext, T : Any> store(
        refresh: Refresh,
        source: ValueSource<C, T>,
        changed: (Slot) -> Unit,
    ): ValueStore<C, T> = ValueStore(refresh, source, cacheSize, clock, scope, changed)

    internal fun listen(listener: (PlaceholderChange) -> Unit): SculkHandle {
        listeners += listener
        return SculkHandle.once { listeners -= listener }
    }

    internal fun signal(change: PlaceholderChange) {
        for (listener in listeners) {
            try {
                listener(change)
            } catch (failure: Exception) {
                val handler = scope.coroutineContext[CoroutineExceptionHandler] ?: throw failure
                handler.handleException(scope.coroutineContext, failure)
            }
        }
    }

    @SculkStable
    public companion object {
        /** Values kept per placeholder: enough for every player on a large server with a few argument lists each. */
        public const val DEFAULT_CACHE_SIZE: Int = 1024
    }
}

/** A component as built; anything else as its string form, which is never parsed. */
private fun <T : Any> defaultFormat(value: T): ComponentLike = value as? ComponentLike ?: Component.text(value.toString())
