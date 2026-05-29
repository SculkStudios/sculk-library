package studio.sculk.config.managed

import studio.sculk.config.ConfigDocument
import studio.sculk.config.ConfigMigrationStep
import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.yaml.YamlMapper
import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkInternal
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
            reloadResult()
        }

        /** Reloads and returns a structured result for command/admin workflows. */
        public fun reloadResult(): SculkResult<T> {
            val loaded = loadOrDefault()
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

        private fun loadOrDefault(): T {
            applyMigrations()
            checkVersion()
            YamlMapper.writeDefaults(file, klass)
            val instance = YamlMapper.load(file, klass)
            val violations = YamlMapper.validate(instance)
            if (violations.isNotEmpty()) {
                violations.forEach { logger.warning("[SculkConfig] Validation error in $path: $it") }
            }
            return instance
        }

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
