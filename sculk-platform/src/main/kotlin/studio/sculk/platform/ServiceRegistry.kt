package studio.sculk.platform

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Where a plugin puts the things its own code needs to reach.
 *
 * ### This is deliberately not dependency injection
 *
 * Nothing here is constructed, resolved lazily, or wired by inspecting types. A service exists
 * because [register] was called with it, in an order the reader can see by reading `setup()` top to
 * bottom. That is the whole feature: start-up order stays explicit and debuggable rather than
 * emergent, and there is no framework behaviour to reason about when something is missing at the
 * wrong moment.
 *
 * ### Keyed by type, never by name
 *
 * A string key turns a compile error into a runtime one, and the runtime one arrives on a live
 * server rather than in the IDE.
 *
 * ### Shutdown order
 *
 * Services close in reverse registration order, because registration order is dependency order:
 * whatever was registered last is most likely to still be holding a reference to something
 * registered earlier. `ConcurrentHashMap` iteration order is arbitrary and shutdown order is not,
 * which is why the order is tracked separately rather than read off the map.
 *
 * ```kotlin
 * services.register(EconomyService(data))
 * val economy = services.get<EconomyService>()
 * ```
 */
@SculkStable
public class ServiceRegistry : SculkHandle {
    private val services = ConcurrentHashMap<KClass<*>, Any>()
    private val order = CopyOnWriteArrayList<KClass<*>>()

    @SculkStable
    public inline fun <reified T : Any> register(service: T): T = register(T::class, service)

    /**
     * Registers [service] under [type].
     *
     * Registering the same type twice fails loudly. Silently replacing would leave half the plugin
     * holding the old instance and the other half the new one, which is close to impossible to see
     * from the symptoms.
     */
    @SculkStable
    public fun <T : Any> register(type: KClass<T>, service: T): T {
        require(services.putIfAbsent(type, service) == null) {
            "A ${type.simpleName} is already registered. Registering twice would leave part of the " +
                "plugin holding the previous instance."
        }
        order += type
        return service
    }

    @SculkStable
    public inline fun <reified T : Any> find(): T? = find(T::class)

    @Suppress("UNCHECKED_CAST")
    @SculkStable
    public fun <T : Any> find(type: KClass<T>): T? = services[type] as T?

    /** The service, or a failure naming what was missing and what was there instead. */
    @SculkStable
    public inline fun <reified T : Any> get(): T = get(T::class)

    @SculkStable
    public fun <T : Any> get(type: KClass<T>): T = find(type) ?: error(
        "No ${type.simpleName} is registered. Registered: ${order.mapNotNull { it.simpleName }.sorted()}",
    )

    @SculkStable
    public inline fun <reified T : Any> has(): Boolean = has(T::class)

    @SculkStable
    public fun <T : Any> has(type: KClass<T>): Boolean = services.containsKey(type)

    /** The registered types, in registration order. */
    @SculkStable
    public val registered: List<KClass<*>> get() = order.toList()

    /** Closes every registered service that is a [SculkHandle], newest first. */
    override fun close() {
        // Passed in registration order: SculkHandle.all already closes in reverse, and reversing
        // here as well cancelled that out and closed oldest-first.
        val handles = order.mapNotNull { services[it] as? SculkHandle }
        services.clear()
        order.clear()
        SculkHandle.all(handles).close()
    }
}
