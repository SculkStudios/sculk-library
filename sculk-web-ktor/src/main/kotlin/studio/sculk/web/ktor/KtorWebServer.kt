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
        // Named, because the overwhelmingly common cause is a port already in use and an
        // operator reading "failed to start" learns nothing they can act on.
        onFailure = { error ->
            SculkResult.failure(
                "Could not bind ${config.bind}:${config.port} (${error.message}). " +
                    "Another process may already be using that port.",
                error,
            )
        },
    )

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
    }
}

/** Discovers the Ktor backend. */
@SculkInternal
public class KtorWebProvider : SculkWebProvider {
    override val backend: String = "ktor"

    override fun create(config: WebConfig): SculkWebServer = KtorWebServer(config)
}
