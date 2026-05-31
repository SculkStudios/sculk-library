package studio.sculk.config.managed

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.logging.Logger

/**
 * Watches a plugin data folder and reloads configs when their files change on disk.
 *
 * Backed by a daemon thread running a [java.nio.file.WatchService]. File-change callbacks are
 * marshalled through [dispatch] so reloads (and their callbacks) can run on the server's main
 * thread — pass `{ scheduler.runSync(it) }` from the platform.
 */
@SculkInternal
public class ConfigWatcher(
    private val dataFolder: File,
    private val reloaders: Map<String, () -> Unit>,
    private val logger: Logger,
    private val dispatch: (Runnable) -> Unit,
) : SculkHandle {
    private val watchService = FileSystems.getDefault().newWatchService()

    @Volatile private var running = true

    private val thread =
        Thread({ runLoop() }, "sculk-config-watcher").apply {
            isDaemon = true
        }

    init {
        dataFolder.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
        thread.start()
    }

    private fun runLoop() {
        while (running) {
            val key =
                try {
                    watchService.take()
                } catch (_: ClosedWatchServiceException) {
                    return
                } catch (_: InterruptedException) {
                    return
                }
            for (event in key.pollEvents()) {
                val changed = (event.context() as? java.nio.file.Path)?.fileName?.toString() ?: continue
                val reload = reloaders[changed] ?: continue
                dispatch {
                    runCatching(reload).onFailure {
                        logger.warning("[SculkConfig] Auto-reload of $changed failed: ${it.message}")
                    }
                }
            }
            if (!key.reset()) break
        }
    }

    override fun close() {
        running = false
        runCatching { watchService.close() }
    }
}
