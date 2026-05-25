package studio.sculk.integrations

import org.bukkit.Server
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import studio.sculk.core.SculkResult

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
        val dependency = mock<Plugin>()
        whenever(dependency.isEnabled).thenReturn(false)
        val integrations = integrationsWithPlugin(dependency)

        val result = integrations.luckPerms()

        assertTrue(result is SculkResult.Failure)
        assertEquals("LuckPerms is not installed or is not enabled.", (result as SculkResult.Failure).message)
    }

    private fun integrationsWithPlugin(dependency: Plugin?): SculkIntegrations {
        val plugin = mock<Plugin>()
        val server = mock<Server>()
        val pluginManager = mock<PluginManager>()
        whenever(plugin.server).thenReturn(server)
        whenever(server.pluginManager).thenReturn(pluginManager)
        whenever(pluginManager.getPlugin(org.mockito.kotlin.any())).thenReturn(dependency)
        return SculkIntegrations(plugin)
    }
}
