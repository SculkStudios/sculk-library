package studio.sculk.config.managed

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.config.ConfigDocument
import studio.sculk.config.ConfigMigrationStep
import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.yaml.YamlMapper
import java.io.File
import java.util.logging.Logger
import kotlin.reflect.KClass

/**
 * Manages the lifecycle of a single typed config file.
 *
 * Handles loading, default generation, validation, version migration,
 * hot reload, and reload callbacks. Used internally by [SculkConfig].
 */
@SculkInternal
public class SculkConfigManager<T : Any>
@SculkInternal
internal constructor(
    private val klass: KClass<T>,
    private val dataFolder: File,
    private val logger: Logger,
    private val migrations: List<ConfigMigrationStep> = emptyList(),
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

    /** The configured file path (relative to the data folder) for this config. */
    public val configPath: String get() = path

    private val file: File get() = File(dataFolder, path)
    private var current: T = initialLoad()
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
     * If the file is unreadable or the loaded values fail validation, logs errors and keeps
     * the previous valid config. Fires all registered reload callbacks on success.
     */
    public fun reload() {
        reloadResult()
    }

    /** Reloads and returns a structured result for command/admin workflows. */
    public fun reloadResult(): SculkResult<T> {
        val loaded =
            runCatching { readFromDisk() }.getOrElse { error ->
                logger.severe(
                    "[SculkConfig] Could not read $path (${summarize(error)}). " +
                        "Keeping the previous values; the file was left untouched.",
                )
                return SculkResult.failure("Could not read $path: ${summarize(error)}", error)
            }
        val violations = YamlMapper.validate(loaded)
        if (violations.isNotEmpty()) {
            violations.forEach { logger.warning("[SculkConfig] Validation error in $path: $it") }
            logger.warning("[SculkConfig] Keeping previous valid config for $path.")
            return SculkResult.failure("Validation failed for $path: ${violations.joinToString("; ")}")
        }
        current = loaded
        reloadCallbacks.forEach { it.invoke() }
        logger.info("[SculkConfig] Reloaded $path.")
        return SculkResult.success(current)
    }

    /**
     * First load at startup. A broken file (YAML syntax error, unreadable disk) must not take
     * the whole plugin down with a stack trace: log what is wrong, fall back to built-in
     * defaults, and leave the user's file untouched so nothing they wrote is lost.
     */
    private fun initialLoad(): T {
        val instance =
            runCatching { readFromDisk() }.getOrElse { error ->
                logger.severe(
                    "[SculkConfig] Could not read $path (${summarize(error)}). " +
                        "Using built-in defaults; the file was left untouched. " +
                        "Fix the file and reload to apply it.",
                )
                return YamlMapper.defaults(klass)
            }
        val violations = YamlMapper.validate(instance)
        violations.forEach { logger.warning("[SculkConfig] Validation error in $path: $it") }
        return instance
    }

    private fun readFromDisk(): T {
        applyMigrations()
        checkVersion()
        YamlMapper.writeDefaults(file, klass)
        return YamlMapper.load(file, klass) { message -> logger.warning("[SculkConfig] $path: $message") }
    }

    /** SnakeYAML errors are long and multi-line; collapse to one readable line. */
    private fun summarize(error: Throwable): String = (error.message ?: error.toString()).replace(Regex("\\s+"), " ").take(200)

    private fun applyMigrations() {
        if (!file.exists() || migrations.isEmpty()) return
        val classVersion = YamlMapper.defaultConfigVersion(klass) ?: return
        var fileVersion = YamlMapper.fileConfigVersion(file) ?: return
        if (fileVersion >= classVersion) return

        val raw = YamlMapper.loadRaw(file)
        var changed = false
        while (fileVersion < classVersion) {
            val step = migrations.firstOrNull { it.from == fileVersion } ?: break
            ConfigDocument(raw).apply(step.block)
            raw["config-version"] = step.to
            fileVersion = step.to
            changed = true
            logger.info("[SculkConfig] Migrated $path to config version $fileVersion.")
        }

        if (changed) {
            YamlMapper.saveRaw(file, raw)
        }
    }

    /**
     * Compares the `config-version` in the file against the class default.
     *
     * When a version bump is detected, logs a warning so operators know that
     * new keys have been added. Existing keys are always preserved by [YamlMapper.writeDefaults].
     */
    private fun checkVersion() {
        val fileVersion = YamlMapper.fileConfigVersion(file) ?: return
        val classVersion = YamlMapper.defaultConfigVersion(klass) ?: return
        if (fileVersion < classVersion) {
            logger.warning(
                "[SculkConfig] $path is outdated (v$fileVersion → v$classVersion). " +
                    "New keys will use defaults. Existing keys are preserved.",
            )
        }
    }
}
