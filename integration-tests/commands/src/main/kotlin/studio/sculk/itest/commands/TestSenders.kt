package studio.sculk.itest.commands

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.permissions.Permissible
import org.bukkit.permissions.PermissibleBase
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * A player that exists only for permission checks, since the suite has no client: Bukkit's own
 * [PermissibleBase] answers every permission question against the server's real registry, the
 * world is a real one and the game mode is whatever the scenario sets.
 */
internal class TestPlayer(private val operator: Boolean, private val world: World) {
    @Volatile
    var gameMode: GameMode = GameMode.SURVIVAL

    private var permissible: PermissibleBase? = null

    val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
        answer(proxy, method, args ?: emptyArray())
    } as Player

    init {
        permissible = PermissibleBase(player)
    }

    private fun answer(proxy: Any, method: Method, args: Array<Any?>): Any? = when (method.name) {
        // Answered here because PermissibleBase asks its owner while it is being constructed.
        "isOp" -> operator

        "getWorld" -> world

        "getGameMode" -> gameMode

        "getName" -> if (operator) "SculkOperator" else "SculkPlayer"

        "hashCode" -> System.identityHashCode(proxy)

        "equals" -> proxy === args.firstOrNull()

        "toString" -> "TestPlayer(operator=$operator)"

        else -> if (method.declaringClass.isAssignableFrom(Permissible::class.java)) {
            val base = checkNotNull(permissible) { "${method.name} was called before the permissible was ready." }
            try {
                method.invoke(base, *args)
            } catch (thrown: InvocationTargetException) {
                throw thrown.targetException
            }
        } else {
            neutral(method.returnType)
        }
    }

    private fun neutral(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        else -> null
    }
}

/** A command source stack for [sender], executed as [executor]; nothing reads its location here. */
internal fun stackOf(sender: CommandSender, executor: Entity? = sender as? Entity): CommandSourceStack =
    Proxy.newProxyInstance(CommandSourceStack::class.java.classLoader, arrayOf(CommandSourceStack::class.java)) { proxy, method, _ ->
        when (method.name) {
            "getSender" -> sender
            "getExecutor" -> executor
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "TestStack(${sender.name})"
            else -> null
        }
    } as CommandSourceStack
