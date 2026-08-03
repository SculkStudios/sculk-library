package studio.sculk.config

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.io.DirectoryWatcher
import studio.sculk.onSuccess
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

private val REVISION_MARKER = Regex("^#\\s*revision:\\s*(\\d+)\\s*$", RegexOption.MULTILINE)
private val ENV_PLACEHOLDER = Regex("""\$\{[A-Za-z_][A-Za-z0-9_]*(?::-[^}]*)?}""")

/**
 * Typed YAML configuration, driven by the compiler-generated `SerialDescriptor` rather than
 * reflection.
 *
 * The data class *is* the shipped file: defaults are written on first load, later loads add keys
 * the file is missing, and nothing you or a server owner put there is ever removed.
 *
 * ```kotlin
 * @Serializable
 * @ConfigFile("settings.yml")
 * data class Settings(
 *     @Comment("How many homes a player may set.")
 *     val maxHomes: Int = 5,
 * )
 *
 * val settings = config.load<Settings>().getOrThrow()
 * ```
 *
 * See [docs.sculk.studio/config/overview](https://docs.sculk.studio/config/overview/).
 */
@SculkStable
public class SculkConfig
@SculkInternal
constructor(
    private val dataFolder: File,
    private val logger: Logger,
    private val environment: (String) -> String? = System::getenv,
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            // What makes "the defaults are the file" work at all.
            encodeDefaults = true,
            // A key left over from an older version must not stop the server booting.
            strictMode = false,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
            sequenceBlockIndent = 2,
            // Config files are read and edited by server owners, not by Kotlin: max-homes, not
            // maxHomes. This is also the format every previously generated Sculk config used.
            yamlNamingStrategy = YamlNamingStrategy.KebabCase,
        ),
    )

    private val cache = ConcurrentHashMap<String, Any>()
    private val loaders = ConcurrentHashMap<String, () -> Unit>()
    private val listeners = ConcurrentHashMap<String, MutableList<() -> Unit>>()
    private val migrations = ConcurrentHashMap<String, List<ConfigMigrationStep>>()

    // The type argument on serializer<T>() is not decoration: where T does not appear in the
    // return type, inference resolves it against the KSerializer<T> parameter, finds nothing to
    // pin it to, and settles on the upper bound — producing "Serializer for class 'kotlin.Any'
    // is not found" at runtime rather than a compile error.

    @SculkStable
    public inline fun <reified T : Any> load(): SculkResult<T> = load(serializer<T>())

    @SculkStable
    public inline fun <reified T : Any> reload(): SculkResult<T> = reload(serializer<T>())

    @SculkStable
    public inline fun <reified T : Any> save(value: T): SculkResult<Unit> = save(serializer<T>(), value)

    @SculkStable
    public inline fun <reified T : Any> violations(): List<String> = violations(serializer<T>())

    @SculkStable
    public inline fun <reified T : Any> onReload(noinline listener: () -> Unit): Unit = onReload(serializer<T>(), listener)

    /** Registers migrations for [T]. Must happen before the first [load]. */
    @SculkStable
    public inline fun <reified T : Any> migrations(noinline block: ConfigMigrationBuilder.() -> Unit): Unit =
        migrations(serializer<T>(), block)

    /** Loads [serializer]'s config, returning the cached instance if it is already loaded. */
    @Suppress("UNCHECKED_CAST")
    @SculkStable
    public fun <T : Any> load(serializer: KSerializer<T>): SculkResult<T> {
        val name = fileName(serializer)
        cache[name]?.let { return SculkResult.success(it as T) }
        loaders[name] = { reload(serializer) }
        return read(serializer).onSuccess { cache[name] = it }
    }

    /** Rereads [serializer]'s config from disk and notifies its reload listeners. */
    @SculkStable
    public fun <T : Any> reload(serializer: KSerializer<T>): SculkResult<T> {
        val name = fileName(serializer)
        return read(serializer).onSuccess { value ->
            cache[name] = value
            listeners[name]?.forEach { listener ->
                runCatching(listener).onFailure { logger.warning("[SculkConfig] A reload listener for $name failed: ${it.message}") }
            }
        }
    }

    /** Writes [value] out, replacing whatever is on disk. */
    @SculkStable
    public fun <T : Any> save(serializer: KSerializer<T>, value: T): SculkResult<Unit> {
        val name = fileName(serializer)
        return SculkResult.catching("write $name") {
            val file = File(dataFolder, name)
            file.parentFile?.mkdirs()
            file.writeText(render(serializer, value))
            cache[name] = value
        }
    }

    /**
     * The constraint violations in [serializer]'s *shipped defaults*.
     *
     * Public so a test can assert the values a plugin ships are inside their own limits. Catching
     * that in CI is the difference between a warning nobody reads and a value nobody ever set.
     */
    @SculkStable
    public fun <T : Any> violations(serializer: KSerializer<T>): List<String> {
        val defaults = runCatching { yaml.decodeFromString(serializer, "{}") }.getOrNull() ?: return emptyList()
        val node = yaml.parseToYamlNode(yaml.encodeToString(serializer, defaults))
        return ConfigValidation.violations(node, serializer.descriptor)
    }

    @SculkStable
    public fun <T : Any> onReload(serializer: KSerializer<T>, listener: () -> Unit) {
        listeners.getOrPut(fileName(serializer)) { mutableListOf() } += listener
    }

    @SculkInternal
    public fun <T : Any> migrations(serializer: KSerializer<T>, block: ConfigMigrationBuilder.() -> Unit) {
        val name = fileName(serializer)
        require(!cache.containsKey(name)) { "Register migrations for $name before loading it." }
        migrations[name] = ConfigMigrationBuilder().apply(block).steps.sortedBy { it.from }
    }

    /** Rereads every config that has been loaded. */
    @SculkStable
    public fun reloadAll() {
        loaders.values.forEach { it() }
    }

    /**
     * Watches the data folder and reloads a config when its file changes.
     *
     * Pass `{ scheduler.runSync(it) }` so listeners run on the main thread; the watch itself is on
     * a daemon thread.
     */
    @SculkStable
    public fun watch(dispatch: (Runnable) -> Unit = Runnable::run): SculkHandle =
        DirectoryWatcher(dataFolder, loaders.mapValues { (_, reload) -> reload }, logger, "SculkConfig", dispatch)

    @OptIn(ExperimentalSerializationApi::class)
    private fun fileName(serializer: KSerializer<*>): String =
        serializer.descriptor.annotations.filterIsInstance<ConfigFile>().firstOrNull()?.name
            ?: (serializer.descriptor.serialName.substringAfterLast('.').replaceFirstChar { it.lowercase() } + ".yml")

    @OptIn(ExperimentalSerializationApi::class)
    private fun declaredRevision(serializer: KSerializer<*>): Int =
        serializer.descriptor.annotations.filterIsInstance<ConfigFile>().firstOrNull()?.revision ?: 1

    private fun <T : Any> read(serializer: KSerializer<T>): SculkResult<T> {
        val name = fileName(serializer)
        val file = File(dataFolder, name)

        val defaults = runCatching { yaml.decodeFromString(serializer, "{}") }.getOrElse { error ->
            // Every config property must have a default, or there is nothing to ship.
            return SculkResult.failure(
                "Config $name cannot be generated because a property has no default value: ${error.message}",
                error,
            )
        }

        if (!file.exists()) {
            return SculkResult.catching("create $name") {
                file.parentFile?.mkdirs()
                file.writeText(render(serializer, defaults))
                defaults
            }
        }

        return SculkResult.catching("read $name") {
            val original = file.readText().replace("\r\n", "\n")

            val declared = declaredRevision(serializer)
            val onDisk = REVISION_MARKER.find(original)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            if (onDisk < declared) {
                val backup = File(dataFolder, "$name.$onDisk.bak")
                file.copyTo(backup, overwrite = true)
                file.writeText(render(serializer, defaults))
                logger.info("[SculkConfig] $name was revision $onDisk, now $declared; the old file is at ${backup.name}.")
                return@catching defaults
            }

            val substituted = substituteEnvironment(original, environment)
            val migrated = applyMigrations(name, substituted)
            val node = yaml.parseToYamlNode(migrated)

            ConfigValidation.violations(node, serializer.descriptor).forEach {
                logger.warning("[SculkConfig] $name: $it")
            }

            val value = yaml.decodeFromString(serializer, migrated)

            // Never rewrite a file that reads from the environment: the render is built from
            // substituted text, so writing it back would bake the resolved secret into the file on
            // disk. The cost is that such a file does not gain new keys automatically.
            if (!ENV_PLACEHOLDER.containsMatchIn(original)) {
                // Append-only. The render is a source of keys the file is missing, never a
                // replacement for it -- see ConfigMerge for what a full rewrite destroys.
                val merged = ConfigMerge.appendMissing(original, render(serializer, value))
                if (merged != original) file.writeText(merged)
            }

            value
        }
    }

    private fun applyMigrations(name: String, text: String): String {
        val steps = migrations[name].orEmpty()
        if (steps.isEmpty()) return text

        val plain = PlainYaml.toPlain(yaml.parseToYamlNode(text))

        @Suppress("UNCHECKED_CAST")
        val values = (plain as? Map<String, Any?>)?.toMutableMap() ?: return text

        var version = (values["config-version"] as? String)?.toIntOrNull() ?: 1
        for (step in steps) {
            if (step.from != version) continue
            ConfigDocument(values).apply(step.block)
            version = step.to
        }
        values["config-version"] = version.toString()
        return PlainYaml.emit(values)
    }

    private fun <T : Any> render(serializer: KSerializer<T>, value: T): String = CommentedYaml.decorate(
        quotePlaceholders(yaml.encodeToString(serializer, value).replace("\r\n", "\n")),
        serializer.descriptor,
        declaredRevision(serializer),
    )

    public companion object {
        @SculkStable
        public fun create(dataFolder: File, logger: Logger): SculkConfig = SculkConfig(dataFolder, logger)
    }
}
