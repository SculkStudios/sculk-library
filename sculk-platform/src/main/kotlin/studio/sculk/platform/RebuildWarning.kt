package studio.sculk.platform

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.annotation.SculkInternal
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Warns when the plugin jar was replaced while the server was running.
 *
 * `PluginClassLoader` reads classes out of the jar lazily. Rebuilding over a running server's jar
 * therefore does not fail immediately — it fails later, as `ClassNotFoundException` or
 * `NoClassDefFoundError` for a class that is plainly there when you look. It reliably hits
 * whatever loads last: menus, lambdas, and anything only reached on a rare path.
 *
 * People lose hours to this, usually suspecting their own code. Detecting it costs one file
 * timestamp comparison at start-up.
 */
@SculkInternal
public object RebuildWarning {
    public fun check(plugin: JavaPlugin) {
        runCatching {
            val jar = File(plugin.javaClass.protectionDomain.codeSource.location.toURI())
            val started = ManagementFactory.getRuntimeMXBean().startTime
            if (jar.isFile && jar.lastModified() > started) {
                plugin.logger.warning(
                    "[Sculk] ${jar.name} was modified after this server started. Class loading is lazy, so " +
                        "anything not yet loaded may fail with ClassNotFoundException for a class that is in " +
                        "the jar. Restart before trusting anything you see from here on.",
                )
            }
        }
    }
}
