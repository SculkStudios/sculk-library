package studio.sculk.config

import studio.sculk.config.managed.SculkConfigManager
import studio.sculk.config.yaml.YamlMapper
import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkInternal
import studio.sculk.core.annotation.SculkStable
import java.io.File
import java.util.logging.Logger

/**
 * Entry point for the Sculk Studio configuration system.
 *
 * Obtain an instance via [SculkConfig.create] or through [studio.sculk.platform.SculkPlatform].
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
        private val migrationSteps: MutableMap<Class<*>, List<ConfigMigrationStep>> = mutableMapOf()

        /**
         * Loads the config for [T], creating defaults on disk if the file doesn't exist.
         *
         * Returns the current value. Call [reload] or the platform reload command to refresh.
         */
        public inline fun <reified T : Any> load(): T = load(T::class.java)

        /** Loads the config with explicit mode metadata for future strict-mode behavior. */
        public inline fun <reified T : Any> load(mode: ConfigLoadMode): T = load(T::class.java, mode)

        /**
         * Registers a [callback] to run after [T]'s config is reloaded.
         */
        public inline fun <reified T : Any> onReload(noinline callback: () -> Unit): Unit = onReload(T::class.java, callback)

        /** Registers versioned migrations for config [T]. Register these before [load]. */
        public inline fun <reified T : Any> migrations(noinline block: ConfigMigrationBuilder.() -> Unit): Unit =
            migrations(T::class.java, block)

        /**
         * Reloads all registered configs.
         */
        public fun reloadAll() {
            managers.values.forEach { it.reload() }
        }

        /** Reloads one registered config and returns a structured result. */
        public inline fun <reified T : Any> reload(): SculkResult<T> = reload(T::class.java)

        // ---------------------------------------------------------------------------
        // Non-inline internals (called by the inline functions above)
        // ---------------------------------------------------------------------------

        @SculkInternal
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> load(javaClass: Class<T>): T {
            val manager =
                managers.getOrPut(javaClass) {
                    SculkConfigManager(javaClass.kotlin, dataFolder, logger, migrationSteps[javaClass].orEmpty())
                } as SculkConfigManager<T>
            return manager.value
        }

        @SculkInternal
        public fun <T : Any> load(
            javaClass: Class<T>,
            mode: ConfigLoadMode,
        ): T =
            load(javaClass).also {
                if (mode == ConfigLoadMode.Strict) {
                    val violations = YamlMapper.validate(it)
                    require(violations.isEmpty()) {
                        "Config ${javaClass.simpleName} failed strict validation: ${violations.joinToString("; ")}"
                    }
                }
            }

        @SculkInternal
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> reload(javaClass: Class<T>): SculkResult<T> {
            val manager =
                managers.getOrPut(javaClass) {
                    SculkConfigManager(javaClass.kotlin, dataFolder, logger, migrationSteps[javaClass].orEmpty())
                } as SculkConfigManager<T>
            return manager.reloadResult()
        }

        @SculkInternal
        public fun <T : Any> migrations(
            javaClass: Class<T>,
            block: ConfigMigrationBuilder.() -> Unit,
        ) {
            require(javaClass !in managers) {
                "Register migrations for ${javaClass.simpleName} before loading the config."
            }
            val builder = ConfigMigrationBuilder().apply(block)
            migrationSteps[javaClass] = builder.steps.sortedBy { it.from }
        }

        @SculkInternal
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> onReload(
            javaClass: Class<T>,
            callback: () -> Unit,
        ) {
            val manager =
                managers.getOrPut(javaClass) {
                    SculkConfigManager(javaClass.kotlin, dataFolder, logger, migrationSteps[javaClass].orEmpty())
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

/** Config load behavior. */
@SculkStable
public enum class ConfigLoadMode {
    Lenient,
    Strict,
}
