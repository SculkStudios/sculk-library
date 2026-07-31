package studio.sculk.platform

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * A typed store for the things a plugin's own code needs to reach. Not DI: nothing is constructed
 * or resolved lazily, so start-up order stays readable in `setup()`.
 *
 * ```kotlin
 * services.register(EconomyService(data))
 * val economy = services.get<EconomyService>()
 * ```
 *
 * See [docs.sculk.studio/platform/services](https://docs.sculk.studio/platform/services/).
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
