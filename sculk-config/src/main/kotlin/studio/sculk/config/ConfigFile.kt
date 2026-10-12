package studio.sculk.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.Tag
import studio.sculk.SculkFailure
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * One typed configuration file. [value] is always a valid, fully decoded configuration: a reload
 * that fails to parse, migrate or validate leaves it untouched and returns why.
 *
 * Thread-safe. Saving and reloading are serialised; reads of [value] never block.
 */
@SculkStable
public class ConfigFile<T : Any> internal constructor(
    public val path: Path,
    private val serializer: KSerializer<T>,
    private val options: ConfigOptions<T>,
    initial: T,
    private var lastWritten: String,
) : SculkHandle {
    @Volatile
    private var current: T = initial
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()
    private val watchers = CopyOnWriteArrayList<SculkHandle>()

    public val value: T get() = current

    /**
     * Reads the file again. On success the new value replaces [value] and listeners run on the
     * calling thread; on failure nothing changes.
     */
    public fun reload(): SculkResult<T> = synchronized(lock) {
        when (val loaded = ConfigLoader.read(path, serializer, options)) {
            is SculkResult.Success -> {
                lastWritten = loaded.value.text
                current = loaded.value.value
                listeners.forEach { it(current) }
                SculkResult.success(current)
            }

            is SculkFailure -> loaded
        }
    }

    /**
     * Writes [value] into the file, keeping the operator's comments, key order and unknown keys,
     * then makes it the current value. The write is atomic: a crash leaves the old file or the new one.
     */
    public fun save(value: T): SculkResult<Unit> = synchronized(lock) {
        SculkResult.catching("save ${path.name}") {
            val tree = if (path.exists()) YamlTree.parse(path.readText()) else YamlTree.emptyMapping()
            val merged = YamlTree.apply(tree, YamlEncoding.encode(serializer, value), ConfigLoader.resolver(options))
            val text = YamlTree.print(merged)
            ConfigLoader.write(path, text)
            lastWritten = text
            current = value
            listeners.forEach { it(value) }
        }
    }

    /** Runs [listener] after every successful reload or save. */
    public fun onChange(listener: (T) -> Unit): SculkHandle {
        listeners += listener
        return SculkHandle { listeners -= listener }
    }

    /**
     * Reloads when the file changes on disk, [debounceMillis] after the last change of a burst,
     * so an editor's several writes per save cause one reload. Changes Sculk itself wrote are
     * ignored. [onFailure] receives every reload that was rejected.
     */
    public fun watch(debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS, onFailure: (SculkFailure) -> Unit): SculkHandle {
        val directory = path.toAbsolutePath().parent
        val service = directory.fileSystem.newWatchService()
        directory.register(service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE)
        val debouncer = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "sculk-config-${path.name}").apply {
                isDaemon =
                    true
            }
        }
        var pending: ScheduledFuture<*>? = null
        val reloadIfChanged = Runnable {
            val text = runCatching { path.readText() }.getOrNull() ?: return@Runnable
            if (text == lastWritten) return@Runnable
            when (val result = reload()) {
                is SculkFailure -> onFailure(result)
                is SculkResult.Success -> Unit
            }
        }
        val watcher = thread(isDaemon = true, name = "sculk-config-watch-${path.name}") {
            try {
                while (true) {
                    val key = service.take()
                    val ours = key.pollEvents().any { (it.context() as? Path)?.name == path.name }
                    key.reset()
                    if (ours) {
                        pending?.cancel(false)
                        pending = debouncer.schedule(reloadIfChanged, debounceMillis, TimeUnit.MILLISECONDS)
                    }
                }
            } catch (_: InterruptedException) {
                // closed
            } catch (_: java.nio.file.ClosedWatchServiceException) {
                // closed
            }
        }
        val handle = SculkHandle {
            service.close()
            watcher.interrupt()
            debouncer.shutdownNow()
        }
        watchers += handle
        return handle
    }

    /** Stops every watcher. The last value stays readable. */
    override fun close() {
        watchers.forEach(SculkHandle::close)
        watchers.clear()
    }

    @SculkStable
    public companion object {
        public const val DEFAULT_DEBOUNCE_MILLIS: Long = 500
    }
}

/** Loads typed configuration files. */
@SculkStable
public object SculkConfig {
    /** The key holding a file's schema version. */
    public const val VERSION_KEY: String = "config-version"

    /**
     * Reads [path] into a [ConfigFile], creating it from the type's defaults when it does not exist
     * and adding keys the file lacks. Every property of [T] needs a default so the file can be
     * generated; a type without one is a wiring bug and throws.
     */
    public fun <T : Any> load(
        path: Path,
        serializer: KSerializer<T>,
        options: ConfigOptions<T>.() -> Unit = {},
    ): SculkResult<ConfigFile<T>> {
        val configured = ConfigOptions<T>().apply(options)
        return when (val loaded = ConfigLoader.read(path, serializer, configured)) {
            is SculkResult.Success -> SculkResult.success(ConfigFile(path, serializer, configured, loaded.value.value, loaded.value.text))
            is SculkFailure -> loaded
        }
    }

    public inline fun <reified T : Any> load(path: Path, noinline options: ConfigOptions<T>.() -> Unit = {}): SculkResult<ConfigFile<T>> =
        load(path, serializer<T>(), options)
}

internal object ConfigLoader {
    class Loaded<T>(val value: T, val text: String)

    fun resolver(options: ConfigOptions<*>): (String) -> String = { text ->
        ENVIRONMENT.replace(text) { match -> options.environment(match.groupValues[1]) ?: match.groups[2]?.value ?: match.value }
    }

    fun <T : Any> read(path: Path, serializer: KSerializer<T>, options: ConfigOptions<T>): SculkResult<Loaded<T>> {
        val file = path.name
        val context = DecodeContext(file, options.environment)
        return try {
            if (!path.exists()) generate(path, serializer, options, context) else upgrade(path, serializer, options, context)
        } catch (invalid: ConfigDecodingException) {
            InvalidConfig(
                file,
                invalid.failure.path,
                invalid.failure.line,
                invalid.failure.expected,
                invalid.failure.received,
                invalid.failure.hint,
            )
        } catch (syntax: MarkedYamlEngineException) {
            val line = syntax.problemMark.map { it.line + 1 }.orElse(null)
            InvalidConfig(
                file,
                "",
                line,
                "valid YAML",
                syntax.problem ?: "a syntax error",
                "Check indentation and quoting around that line.",
            )
        } catch (syntax: YamlEngineException) {
            InvalidConfig(file, "", null, "valid YAML", syntax.message ?: "a syntax error")
        } catch (io: java.io.IOException) {
            SculkFailure.OperationFailed("read $file", io)
        }
    }

    private fun <T : Any> generate(
        path: Path,
        serializer: KSerializer<T>,
        options: ConfigOptions<T>,
        context: DecodeContext,
    ): SculkResult<Loaded<T>> {
        val defaults = try {
            serializer.deserialize(ValueDecoder(context, YamlTree.emptyMapping(), ""))
        } catch (missing: ConfigDecodingException) {
            throw IllegalStateException(
                "${serializer.descriptor.serialName} needs a default for every property so ${path.name} can be generated: ${missing.failure.message}",
            )
        }
        val tree = YamlEncoding.encode(serializer, defaults) as MappingNode
        setVersion(tree, options.version)
        tree.value.getOrNull(1)?.keyNode?.let { next ->
            next.blockComments = YamlEncoding.comments(emptyList(), blankLineFirst = true) + next.blockComments.orEmpty()
        }
        if (options.header.isNotEmpty()) {
            val version = tree.value.first().keyNode
            version.blockComments = YamlEncoding.comments(options.header) + YamlEncoding.comments(emptyList(), blankLineFirst = true) + version.blockComments.orEmpty()
        }
        val text = YamlTree.print(tree)
        Files.createDirectories(path.toAbsolutePath().parent)
        write(path, text)
        return SculkResult.success(Loaded(defaults, text))
    }

    private fun <T : Any> upgrade(
        path: Path,
        serializer: KSerializer<T>,
        options: ConfigOptions<T>,
        context: DecodeContext,
    ): SculkResult<Loaded<T>> {
        val original = path.readText()
        val tree = YamlTree.parse(original)
        val fileVersion = (YamlTree.get(tree, SculkConfig.VERSION_KEY) as? ScalarNode)?.value?.toIntOrNull() ?: 1
        if (fileVersion > options.version) {
            return InvalidConfig(
                path.name,
                SculkConfig.VERSION_KEY,
                tree.value.firstOrNull()?.keyNode?.line(),
                "at most ${options.version}",
                "$fileVersion",
                "This file was written by a newer version of the plugin.",
            )
        }
        for (version in fileVersion until options.version) {
            val migration = options.migrations[version]
                ?: return InvalidConfig(
                    path.name,
                    SculkConfig.VERSION_KEY,
                    null,
                    "a migration from version $version",
                    "none",
                    "The plugin cannot upgrade this file; restore a backup or regenerate it.",
                )
            migration.steps.forEach { it(tree) }
        }
        val value = serializer.deserialize(ValueDecoder(context, tree, ""))
        val violations = Violations().apply { options.validations.forEach { rule -> rule(value) } }
        violations.problems.firstOrNull()?.let { (where, expected, received) ->
            val more = violations.problems.size - 1
            return InvalidConfig(
                path.name,
                where,
                YamlTree.find(tree, where)?.line(),
                expected,
                received,
                if (more >
                    0
                ) {
                    "$more more problem(s) after this one."
                } else {
                    null
                },
            )
        }
        YamlTree.fillMissing(tree, YamlEncoding.encode(serializer, value) as MappingNode)
        setVersion(tree, options.version)
        val text = YamlTree.print(tree)
        if (text != original) {
            if (fileVersion <
                options.version
            ) {
                Files.copy(path, path.resolveSibling("${path.name}.v$fileVersion.bak"), StandardCopyOption.REPLACE_EXISTING)
            }
            write(path, text)
        }
        return SculkResult.success(Loaded(value, text))
    }

    private fun setVersion(tree: MappingNode, version: Int) {
        val index = tree.value.indexOfFirst { YamlTree.keyOf(it) == SculkConfig.VERSION_KEY }
        val node = YamlEncoding.scalar(version.toString(), Tag.INT)
        if (index >= 0) {
            tree.value[index] = NodeTuple(tree.value[index].keyNode, node)
        } else {
            val key = YamlEncoding.scalar(SculkConfig.VERSION_KEY).apply {
                blockComments = YamlEncoding.comments(listOf("Used by the plugin to upgrade this file. Do not change it."))
            }
            tree.value.add(0, NodeTuple(key, node))
        }
    }

    fun write(path: Path, text: String) {
        val temporary = path.resolveSibling(".${path.name}.tmp")
        Files.writeString(temporary, text)
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private val ENVIRONMENT = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}""")
}
