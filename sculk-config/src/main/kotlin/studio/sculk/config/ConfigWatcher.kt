package studio.sculk.config

import studio.sculk.SculkHandle
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.name

/**
 * Watches one file's directory and runs [onChange] on its own daemon thread [debounceMillis] after
 * the last event of a burst. An overflowed event queue counts as a change.
 */
internal class ConfigWatcher private constructor(
    private val path: Path,
    private val service: WatchService,
    private val debounceMillis: Long,
    private val onChange: () -> Unit,
) : SculkHandle {
    private val debouncer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "sculk-config-${path.name}").apply { isDaemon = true }
    }

    // Read and written only by the polling thread.
    private var pending: ScheduledFuture<*>? = null

    private fun poll() {
        while (true) {
            val key = try {
                service.take()
            } catch (_: ClosedWatchServiceException) {
                return
            } catch (_: InterruptedException) {
                return
            }
            val changed = key.pollEvents().any {
                it.kind() == StandardWatchEventKinds.OVERFLOW || (it.context() as? Path)?.name == path.name
            }
            key.reset()
            if (changed) debounce()
        }
    }

    private fun debounce() {
        pending?.cancel(false)
        pending = try {
            debouncer.schedule(Runnable { onChange() }, debounceMillis, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            null
        }
    }

    override fun close() {
        debouncer.shutdownNow()
        service.close()
    }

    companion object {
        fun start(path: Path, debounceMillis: Long, onChange: () -> Unit): ConfigWatcher {
            val directory = path.toAbsolutePath().parent
            val service = directory.fileSystem.newWatchService()
            try {
                directory.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
            } catch (failure: IOException) {
                service.close()
                throw failure
            }
            val watcher = ConfigWatcher(path, service, debounceMillis, onChange)
            thread(isDaemon = true, name = "sculk-config-watch-${path.name}") { watcher.poll() }
            return watcher
        }
    }
}
