package studio.sculk.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import studio.sculk.SculkFailure
import studio.sculk.SculkFailureException
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.map
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * One typed configuration file. [value] is always a valid, fully decoded configuration: a reload
 * or save that fails to parse, migrate or validate leaves it and the file untouched and returns why.
 *
 * Thread-safe. Saving and reloading are serialised; reads of [value] never block. Listeners run
 * after the file is released, so a listener may itself save or reload.
 */
@SculkStable
public class ConfigFile<T : Any> internal constructor(
    public val path: Path,
    private val serializer: KSerializer<T>,
    private val options: ConfigOptions<T>,
    loaded: ConfigLoader.Loaded<T>,
) : SculkHandle {
    @Volatile
    private var current: T = loaded.value

    @Volatile
    private var lastWritten: String = loaded.text

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    // Guarded by itself, together with closed.
    private val watchers = mutableListOf<SculkHandle>()
    private var closed = false

    public val value: T get() = current

    /**
     * Reads the file again. On success the new value replaces [value] and the listeners run on the
     * calling thread; on failure nothing changes. A listener's exception is rethrown after every
     * listener has run.
     */
    public fun reload(): SculkResult<T> = refresh().also { result -> if (result is SculkResult.Success) notifyAll(result.value) }

    /**
     * Writes [value] into the file, keeping the operator's comments, key order and unknown keys, then
     * makes it the current value and runs the listeners on the calling thread. The merged file must
     * pass every check a load runs, or nothing is written. The write is atomic where the file system
     * allows: a crash leaves the old file or the new one.
     */
    public fun save(value: T): SculkResult<Unit> {
        val saved = synchronized(lock) { ConfigLoader.save(path, serializer, options, value).map(::install) }
        return saved.map(::notifyAll)
    }

    /** Runs [listener] with the new value after every successful reload or save. */
    public fun onChange(listener: (T) -> Unit): SculkHandle {
        listeners += listener
        return SculkHandle { listeners -= listener }
    }

    /**
     * Reloads when the file changes on disk, [debounceMillis] after the last change of a burst, so an
     * editor's several writes per save cause one reload. Changes Sculk itself wrote are ignored.
     *
     * The file is read on a watcher thread; listeners and [onFailure] then run through [executor],
     * directly on that thread by default. [onFailure] receives every rejected reload and every
     * exception a listener throws, which does not stop the other listeners.
     *
     * Fails when the platform cannot watch the directory. Watching a [close]d file is a wiring bug and throws.
     */
    public fun watch(
        debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
        executor: Executor = Executor(Runnable::run),
        onFailure: (SculkFailure) -> Unit,
    ): SculkResult<SculkHandle> {
        require(debounceMillis >= 0) { "debounceMillis must not be negative, but was $debounceMillis." }
        synchronized(watchers) {
            check(!closed) { "${path.name} is closed and can no longer be watched." }
            return SculkResult.catching("watch ${path.name}") {
                val watcher = ConfigWatcher.start(path, debounceMillis) { reloadChanged(executor, onFailure) }
                watchers += watcher
                SculkHandle {
                    synchronized(watchers) { watchers -= watcher }
                    watcher.close()
                }
            }
        }
    }

    /** Stops every watcher; later [watch] calls throw. [value] stays readable and [reload] and [save] keep working. */
    override fun close() {
        val open = synchronized(watchers) {
            closed = true
            watchers.toList().also { watchers.clear() }
        }
        SculkHandle.all(open).close()
    }

    private fun refresh(): SculkResult<T> = synchronized(lock) { ConfigLoader.read(path, serializer, options).map(::install) }

    private fun install(loaded: ConfigLoader.Loaded<T>): T {
        lastWritten = loaded.text
        current = loaded.value
        return loaded.value
    }

    private fun reloadChanged(executor: Executor, onFailure: (SculkFailure) -> Unit) {
        try {
            val text = try {
                path.readText()
            } catch (_: NoSuchFileException) {
                return
            }
            if (text == lastWritten) return
            when (val result = refresh()) {
                is SculkResult.Success -> executor.execute { notify(result.value) { error -> onFailure(failureOf(error)) } }
                is SculkFailure -> executor.execute { onFailure(result) }
            }
        } catch (error: Exception) {
            onFailure(failureOf(error))
        }
    }

    private fun failureOf(error: Exception): SculkFailure =
        (error as? SculkFailureException)?.failure ?: SculkFailure.OperationFailed("apply a change to ${path.name}", error)

    private fun notify(value: T, onError: (Exception) -> Unit) {
        for (listener in listeners) {
            try {
                listener(value)
            } catch (error: Exception) {
                onError(error)
            }
        }
    }

    private fun notifyAll(value: T) {
        var first: Exception? = null
        notify(value) { error -> first?.addSuppressed(error) ?: run { first = error } }
        first?.let { throw it }
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
        return ConfigLoader.read(path, serializer, configured).map { loaded -> ConfigFile(path, serializer, configured, loaded) }
    }

    public inline fun <reified T : Any> load(path: Path, noinline options: ConfigOptions<T>.() -> Unit = {}): SculkResult<ConfigFile<T>> =
        load(path, serializer<T>(), options)
}
