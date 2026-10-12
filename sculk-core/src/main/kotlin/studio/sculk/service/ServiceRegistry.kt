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

    public fun <T : Any> register(type: KClass<T>, service: T): T {
        val previous = services.putIfAbsent(type, service)
        check(previous == null) { "A ${type.simpleName} is already registered." }
        return service
    }

    public fun <T : Any> find(type: KClass<T>): T? = type.java.cast(services[type])

    /** The registered service; a missing one is a wiring bug and throws naming the type. */
    public fun <T : Any> require(type: KClass<T>): T = checkNotNull(find(type)) { "No ${type.simpleName} is registered." }

    public fun has(type: KClass<*>): Boolean = services.containsKey(type)

    public val types: Set<KClass<*>> get() = services.keys.toSet()
}

@SculkStable
public inline fun <reified T : Any> ServiceRegistry.register(service: T): T = register(T::class, service)

@SculkStable
public inline fun <reified T : Any> ServiceRegistry.find(): T? = find(T::class)

@SculkStable
public inline fun <reified T : Any> ServiceRegistry.require(): T = require(T::class)
