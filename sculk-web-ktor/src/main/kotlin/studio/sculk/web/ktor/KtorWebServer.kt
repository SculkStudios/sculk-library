package studio.sculk.web.ktor

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.web.HttpMethod
import studio.sculk.web.SculkWebProvider
import studio.sculk.web.SculkWebServer
import studio.sculk.web.WebConfig
import studio.sculk.web.WebRequest
import studio.sculk.web.WebResponse
import java.net.BindException
import io.ktor.http.HttpStatusCode as KtorStatus

/**
 * The Ktor backend.
 *
 * CIO rather than Netty: Paper already ships Netty, and a second copy on the plugin classloader is
 * the version clash that surfaces as a `NoSuchMethodError` on the first request rather than at
 * boot. CIO has no such overlap.
 */
@SculkInternal
public class KtorWebServer(private val config: WebConfig) : SculkWebServer {
    private val routes = mutableListOf<Route>()
    private val statics = mutableListOf<Pair<String, String>>()

    private var engine: EmbeddedServer<*, *>? = null

    override var running: Boolean = false
        private set

    override val port: Int get() = config.port

    override fun route(method: HttpMethod, path: String, handler: suspend (WebRequest) -> WebResponse) {
        // Registration after start is rejected rather than ignored: a route silently not being
        // served is far harder to diagnose than one that refuses to be added.
        check(!running) { "Routes must be registered before the server starts" }
        routes += Route(method, path, handler)
    }

    override fun static(pathPrefix: String, resourceRoot: String) {
        check(!running) { "Static mounts must be registered before the server starts" }
        statics += pathPrefix to resourceRoot
    }

    override suspend fun start(): SculkResult<Unit> = runCatching {
        val server =
            embeddedServer(CIO, port = config.port, host = config.bind) {
                routing {
                    routes.forEach { mount(it) }
                    // Static assets are mounted after the API routes so a frontend served at
                    // "/" cannot shadow "/api/...".
                    statics.forEach { (prefix, resourceRoot) -> staticResources(prefix, resourceRoot) }
                }
            }
        server.start(wait = false)
        engine = server
        running = true
        Unit
    }.fold(
        onSuccess = { SculkResult.success(Unit) },
        onFailure = { error -> SculkResult.failure(explain(error), error) },
    )

    /**
     * Says what actually went wrong.
     *
     * This used to report every failure as "another process may already be using that port",
     * which is a guess presented as a diagnosis. A consumer hit a `LinkageError` -- its HTTP
     * library and its Kotlin stdlib had been resolved into different classloaders, so
     * `embeddedServer` could not link at all -- and spent the investigation looking for a process
     * on port 8080 that never existed. A wrong cause is worse than no cause: it is a working
     * theory that costs hours before it is discarded.
     *
     * The port explanation is still offered, but only where it can be true.
     *
     * Matched against the cause chain rather than the thrown type: the engine starts the bind on a
     * coroutine and rethrows what surfaces, so the `BindException` arrives wrapped. Checking the
     * top-level type alone sent the one case this function exists for -- a port already in use --
     * down the generic branch, which is how the test asserting it names the port caught this.
     */
    private fun explain(error: Throwable): String = when {
        error.causes().any { it is BindException } ->
            "Could not bind ${config.bind}:${config.port} (${error.rootMessage()}). " +
                "Another process is already using that port."

        // Not a startup failure at all -- the classes could not be wired together. Almost always
        // one library loaded by a different classloader than the one it was compiled against.
        error.causes().any { it is LinkageError } ->
            "The web server could not be linked: ${error.rootMessage()}. This is a classloading " +
                "problem, not a port or configuration problem -- the HTTP library and the Kotlin " +
                "runtime it was compiled against have been loaded by different classloaders. Shade " +
                "them together rather than loading either separately."

        else -> "The web server did not start: ${error.message}"
    }

    /** The throwable and everything it wraps. Bounded, because a cause chain can be circular. */
    private fun Throwable.causes(): Sequence<Throwable> =
        generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSE_DEPTH)

    /** The innermost message, which is the one naming the actual failure. */
    private fun Throwable.rootMessage(): String? = causes().lastOrNull { it.message != null }?.message ?: message

    /**
     * Mounts one route.
     *
     * Named `mount` rather than `install` because Ktor already has an `install` in scope here, and
     * a same-named extension on a Ktor type resolves to whichever the compiler prefers rather than
     * whichever was meant.
     */
    private fun Routing.mount(route: Route) {
        when (route.method) {
            HttpMethod.GET -> get(route.path) { call.serve(route) }
            HttpMethod.POST -> post(route.path) { call.serve(route) }
            HttpMethod.PUT -> put(route.path) { call.serve(route) }
            HttpMethod.DELETE -> delete(route.path) { call.serve(route) }
            HttpMethod.PATCH -> patch(route.path) { call.serve(route) }
        }
    }

    private suspend fun ApplicationCall.serve(route: Route) {
        val result = route.handler(toWebRequest())

        // Headers are appended before the body is written: once respondText commits the response,
        // header changes are silently dropped rather than erroring.
        result.headers.forEach { (name, value) -> response.headers.append(name, value) }

        respondText(
            text = result.body,
            contentType = ContentType.parse(result.contentType),
            status = KtorStatus.fromValue(result.status),
        )
    }

    private suspend fun ApplicationCall.toWebRequest(): WebRequest = WebRequest(
        method = HttpMethod.valueOf(request.local.method.value),
        path = request.uri.substringBefore('?'),
        query = request.queryParameters.entries().associate { it.key to it.value.firstOrNull().orEmpty() },
        headers = request.headers.entries().associate { it.key to it.value.firstOrNull().orEmpty() },
        body = receiveText(),
        remoteAddress = resolveAddress(),
    )

    /**
     * The client address.
     *
     * `X-Forwarded-For` is honoured **only** when the immediate peer is a configured trusted proxy.
     * Believing it unconditionally lets any client claim any address, which defeats rate limiting
     * and address-bound sessions in one header.
     */
    private fun ApplicationCall.resolveAddress(): String {
        val peer = request.origin.remoteAddress
        if (peer !in config.trustedProxies) return peer
        return request.headers["X-Forwarded-For"]
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: peer
    }

    override fun close() {
        running = false
        engine?.stop(gracePeriodMillis = GRACE_MILLIS, timeoutMillis = TIMEOUT_MILLIS)
        engine = null
    }

    private data class Route(val method: HttpMethod, val path: String, val handler: suspend (WebRequest) -> WebResponse)

    private companion object {
        const val GRACE_MILLIS = 500L
        const val TIMEOUT_MILLIS = 2_000L

        /** Deep enough for any real wrapping, short enough that a cycle cannot hang start-up. */
        const val MAX_CAUSE_DEPTH = 16
    }
}

/** Discovers the Ktor backend. */
@SculkInternal
public class KtorWebProvider : SculkWebProvider {
    override val backend: String = "ktor"

    override fun create(config: WebConfig): SculkWebServer = KtorWebServer(config)
}
