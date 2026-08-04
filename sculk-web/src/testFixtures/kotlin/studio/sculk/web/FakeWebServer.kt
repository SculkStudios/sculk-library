package studio.sculk.web

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * A [SculkWebServer] for testing the code that registers routes on one.
 *
 * Sculk's convention: every interface a consumer *calls* ships a fake. This one also lets a test
 * dispatch a request without binding a port, so a plugin's authorisation and routing can be
 * exercised without an HTTP client anywhere near it.
 */
@SculkStable
public class FakeWebServer(override val port: Int = 0) : SculkWebServer {
    private val routes = mutableMapOf<Pair<HttpMethod, String>, suspend (WebRequest) -> WebResponse>()

    /** Static mounts registered, for asserting a frontend was wired up. */
    public val staticMounts: MutableList<Pair<String, String>> = mutableListOf()

    override var running: Boolean = false
        private set

    /** Set to fail [start], so the caller's degraded path is testable. */
    public var startFailure: String? = null

    override suspend fun start(): SculkResult<Unit> {
        startFailure?.let { return SculkResult.failure(it) }
        running = true
        return SculkResult.success(Unit)
    }

    override fun route(method: HttpMethod, path: String, handler: suspend (WebRequest) -> WebResponse) {
        routes[method to path] = handler
    }

    override fun static(pathPrefix: String, resourceRoot: String) {
        staticMounts += pathPrefix to resourceRoot
    }

    /** Dispatches a request, or 404 if nothing is registered. */
    public suspend fun dispatch(request: WebRequest): WebResponse = routes[request.method to request.path]?.invoke(request)
        ?: WebResponse.error(404, "no route for ${request.method} ${request.path}")

    public fun registeredPaths(): Set<Pair<HttpMethod, String>> = routes.keys.toSet()

    override fun close() {
        running = false
        routes.clear()
    }
}
