package studio.sculk.integrations

import org.bukkit.Server
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.core.SculkResult
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class SculkIntegrationsTest {
    @Test
    fun `missing PlaceholderAPI returns failure`() {
        val integrations = integrationsWithPlugin(null)

        val result = integrations.placeholderApi()

        assertTrue(result is SculkResult.Failure)
        assertEquals("PlaceholderAPI is not installed or is not enabled.", (result as SculkResult.Failure).message)
    }

    @Test
    fun `disabled integration plugin returns failure`() {
        val dependency =
            proxy<Plugin> { method, _ ->
                when (method.name) {
                    "isEnabled" -> false
                    else -> defaultValue(method.returnType)
                }
            }
        val integrations = integrationsWithPlugin(dependency)

        val result = integrations.luckPerms()

        assertTrue(result is SculkResult.Failure)
        assertEquals("LuckPerms is not installed or is not enabled.", (result as SculkResult.Failure).message)
    }

    private fun integrationsWithPlugin(dependency: Plugin?): SculkIntegrations {
        val pluginManager =
            proxy<PluginManager> { method, _ ->
                when (method.name) {
                    "getPlugin" -> dependency
                    else -> defaultValue(method.returnType)
                }
            }
        val server =
            proxy<Server> { method, _ ->
                when (method.name) {
                    "getPluginManager" -> pluginManager
                    else -> defaultValue(method.returnType)
                }
            }
        val plugin =
            proxy<Plugin> { method, _ ->
                when (method.name) {
                    "getServer" -> server
                    else -> defaultValue(method.returnType)
                }
            }
        return SculkIntegrations(plugin)
    }
}

private inline fun <reified T : Any> proxy(noinline handler: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
        InvocationHandler { _, method, args -> handler(method, args) },
    ) as T

private fun defaultValue(type: Class<*>): Any? =
    when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        java.lang.Void.TYPE -> null
        else -> null
    }
