package studio.sculk.scene

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.pointer.Pointers
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

/** Stand-ins for the few server objects scenes read without a server. */
internal object Fixtures {
    /** Block data that only knows its state string, as a node reads it. */
    fun block(state: String): BlockData = proxy(BlockData::class.java) { self, method ->
        when (method.name) {
            "getAsString" -> state
            "clone" -> self
            else -> null
        }
    }

    /** A player that only knows its id and name; anything else it is asked fails the test. */
    fun player(name: String, id: UUID = UUID.nameUUIDFromBytes(name.toByteArray())): Player = proxy(Player::class.java) { _, method ->
        when (method.name) {
            "getUniqueId" -> id
            "getName" -> name
            "pointers" -> Pointers.builder().withStatic(Identity.UUID, id).withStatic(Identity.NAME, name).build()
            else -> null
        }
    }

    /** An audience with a name and an id, as the placeholder registry identifies one. */
    fun audience(name: String): Audience = object : Audience {
        private val pointers = Pointers.builder()
            .withStatic(Identity.UUID, UUID.nameUUIDFromBytes(name.toByteArray()))
            .withStatic(Identity.NAME, name)
            .build()

        override fun pointers(): Pointers = pointers

        override fun toString(): String = "audience $name"
    }

    // Object's methods answer by identity; an interface's default method runs as written; any other
    // method must be answered by [answer], or the test fails naming it.
    private fun <T> proxy(type: Class<T>, answer: (Any, Method) -> Any?): T {
        val handler = InvocationHandler { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.get(0)

                "hashCode" -> System.identityHashCode(self)

                "toString" -> "fake ${type.simpleName}"

                else -> answer(self, method) ?: if (method.isDefault) {
                    InvocationHandler.invokeDefault(self, method, *(args ?: emptyArray()))
                } else {
                    error("the test fake ${type.simpleName} cannot answer ${method.name}")
                }
            }
        }
        return type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
    }
}
