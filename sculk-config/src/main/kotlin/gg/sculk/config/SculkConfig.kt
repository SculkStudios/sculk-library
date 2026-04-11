package gg.sculk.config

import gg.sculk.config.managed.SculkConfigManager
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import java.io.File
import java.util.logging.Logger

/**
 * Entry point for the Sculk Studio configuration system.
 *
 * Obtain an instance via [SculkConfig.create] or through [gg.sculk.platform.SculkPlatform].
 *
 * Example:
 * ```kotlin
 * val config = SculkConfig.create(plugin.dataFolder, plugin.logger)
 *
 * @ConfigFile("settings.yml")
 * data class Settings(val maxHomes: Int = 5)
 *
 * val settings = config.load<Settings>()
 * config.onReload<Settings> { /* called after /reload */ }
 * ```
 */
@SculkStable
public class SculkConfig
    @SculkInternal
    constructor(
        private val dataFolder: File,
        private val logger: Logger,
    ) {
        private val managers: MutableMap<Class<*>, SculkConfigManager<*>> = mutableMapOf()

        /**
         * Loads the config for [T], creating defaults on disk if the file doesn't exist.
         *
         * Returns the current value. Call [reload] or the platform reload command to refresh.
         */
        public inline fun <reified T : Any> load(): T = load(T::class.java)

        /**
         * Registers a [callback] to run after [T]'s config is reloaded.
         */
        public inline fun <reified T : Any> onReload(noinline callback: () -> Unit): Unit = onReload(T::class.java, callback)

        /**
         * Reloads all registered configs.
         */
        public fun reloadAll() {
            managers.values.forEach { it.reload() }
        }

        // ---------------------------------------------------------------------------
        // Non-inline internals (called by the inline functions above)
        // ---------------------------------------------------------------------------

        @SculkInternal
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> load(javaClass: Class<T>): T {
            val manager =
                managers.getOrPut(javaClass) {
                    SculkConfigManager(javaClass.kotlin, dataFolder, logger)
                } as SculkConfigManager<T>
            return manager.value
        }

        @SculkInternal
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> onReload(
            javaClass: Class<T>,
            callback: () -> Unit,
        ) {
            val manager =
                managers.getOrPut(javaClass) {
                    SculkConfigManager(javaClass.kotlin, dataFolder, logger)
                } as SculkConfigManager<T>
            manager.onReload(callback)
        }

        public companion object {
            /**
             * Creates a new [SculkConfig] bound to [dataFolder].
             *
             * @param dataFolder The plugin's data folder (e.g. `plugin.dataFolder`).
             * @param logger The plugin's logger for validation warnings.
             */
            @JvmStatic
            @SculkStable
            public fun create(
                dataFolder: File,
                logger: Logger,
            ): SculkConfig = SculkConfig(dataFolder, logger)
        }
    }
