package gg.sculk.config.managed

import gg.sculk.config.annotation.ConfigFile
import gg.sculk.config.yaml.YamlMapper
import gg.sculk.core.annotation.SculkInternal
import java.io.File
import java.util.logging.Logger
import kotlin.reflect.KClass

/**
 * Manages the lifecycle of a single typed config file.
 *
 * Handles loading, default generation, validation, hot reload, and
 * reload callbacks. Used internally by [SculkConfig].
 */
@SculkInternal
public class SculkConfigManager<T : Any>(
    private val klass: KClass<T>,
    private val dataFolder: File,
    private val logger: Logger,
) {
    private val path: String =
        requireNotNull(
            klass.annotations
                .filterIsInstance<ConfigFile>()
                .firstOrNull()
                ?.path,
        ) {
            "Config class ${klass.simpleName} must be annotated with @ConfigFile."
        }

    private val file: File get() = File(dataFolder, path)
    private var current: T = loadOrDefault()
    private val reloadCallbacks: MutableList<() -> Unit> = mutableListOf()

    /** Returns the current config value. */
    public val value: T get() = current

    /** Registers a callback to be invoked after a successful reload. */
    public fun onReload(callback: () -> Unit) {
        reloadCallbacks += callback
    }

    /**
     * Reloads the config from disk.
     *
     * If the loaded values fail validation, logs errors and keeps the previous
     * valid config. Fires all registered reload callbacks on success.
     */
    public fun reload() {
        val loaded = loadOrDefault()
        val violations = YamlMapper.validate(loaded)
        if (violations.isNotEmpty()) {
            violations.forEach { logger.warning("[SculkConfig] Validation error in $path: $it") }
            logger.warning("[SculkConfig] Keeping previous valid config for $path.")
            return
        }
        current = loaded
        reloadCallbacks.forEach { it.invoke() }
        logger.info("[SculkConfig] Reloaded $path.")
    }

    private fun loadOrDefault(): T {
        YamlMapper.writeDefaults(file, klass)
        val instance = YamlMapper.load(file, klass)
        val violations = YamlMapper.validate(instance)
        if (violations.isNotEmpty()) {
            violations.forEach { logger.warning("[SculkConfig] Validation error in $path: $it") }
        }
        return instance
    }
}
