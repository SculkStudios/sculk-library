package studio.sculk.service

import studio.sculk.annotation.SculkStable
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Services keyed by type, one instance per type. Replaces static singletons, so two plugins
 * shading Sculk never share state by accident.
 *
 * Thread-safe. Registering a type twice is a wiring bug and throws.
 */
@SculkStable
public class ServiceRegistry {
    private val services = ConcurrentHashMap<KClass<*>, Any>()

    /** Registers [service] as the one [type] and returns it; a second registration of [type] throws. */
    public fun <T : Any> register(type: KClass<T>, service: T): T {
        val previous = services.putIfAbsent(type, service)
        check(previous == null) { "A ${type.simpleName} is already registered." }
        return service
    }

    /** The service registered as exactly [type], or null; a subtype's registration does not count. */
    public fun <T : Any> find(type: KClass<T>): T? = type.java.cast(services[type])

    /** The registered service; a missing one is a wiring bug and throws naming the type. */
    public fun <T : Any> require(type: KClass<T>): T = checkNotNull(find(type)) { "No ${type.simpleName} is registered." }

    /** True when a service is registered as exactly [type]. */
    public fun has(type: KClass<*>): Boolean = services.containsKey(type)

    /** A snapshot of the registered types. */
    public val types: Set<KClass<*>> get() = services.keys.toSet()
}

@SculkStable
public inline fun <reified T : Any> ServiceRegistry.register(service: T): T = register(T::class, service)

@SculkStable
public inline fun <reified T : Any> ServiceRegistry.find(): T? = find(T::class)

@SculkStable
public inline fun <reified T : Any> ServiceRegistry.require(): T = require(T::class)
