package studio.sculk.io

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.logging.Logger

/**
 * Calls back when a named file inside [folder] changes on disk.
 *
 * Lives in `sculk-common` rather than beside the config system because two subsystems want it —
 * configs and language bundles — and `sculk-text` deliberately does not depend on `sculk-config`.
 *
 * The watch runs on a daemon thread, so callbacks arrive off the main thread. [dispatch] is the
 * marshaller: pass `{ scheduler.runSync(it) }` and handlers may touch the Paper API.
 *
 * @param handlers keyed by file name, not path; a file not named here is ignored.
 * @param label prefixes log lines so a failure names the subsystem that registered the watch.
 */
@SculkInternal
public class DirectoryWatcher(
    folder: File,
    private val handlers: Map<String, () -> Unit>,
    private val logger: Logger,
    private val label: String,
    private val dispatch: (Runnable) -> Unit,
) : SculkHandle {
    private val watchService = FileSystems.getDefault().newWatchService()

    @Volatile private var running = true

    private val thread = Thread({ runLoop() }, "sculk-watcher-${label.lowercase()}").apply { isDaemon = true }

    init {
        folder.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
        thread.start()
    }

    private fun runLoop() {
        while (running) {
            val key = try {
                watchService.take()
            } catch (_: ClosedWatchServiceException) {
                return
            } catch (_: InterruptedException) {
                return
            }
            for (event in key.pollEvents()) {
                val changed = (event.context() as? Path)?.fileName?.toString() ?: continue
                val handler = handlers[changed] ?: continue
                dispatch {
                    // A throwing handler must not kill the watch thread, or one bad edit silently
                    // disables reloading for the rest of the server's uptime.
                    runCatching(handler).onFailure {
                        logger.warning("[$label] Reloading $changed failed: ${it.message}")
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
