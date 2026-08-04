package studio.sculk.web

import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.util.ServiceLoader

/**
 * An embedded HTTP server, described without naming one.
 *
 * The same split as [studio.sculk.discord.DiscordGateway]: this module holds the shape, a backend
 * module holds the library. A plugin that wants a dashboard, a metrics endpoint or a webhook
 * receiver writes against this and does not care which server is underneath -- and swapping the
 * backend is a dependency change rather than a rewrite.
 *
 * ### Bind address
 *
 * [WebConfig.bind] defaults to loopback, which is deliberately the inconvenient choice. The
 * overwhelmingly correct deployment is behind a reverse proxy that terminates TLS, and a server
 * that binds `0.0.0.0` on first enable is a plaintext admin panel on the public internet. Sculk
 * does not terminate TLS: certificate issuance and renewal are solved better by Caddy or nginx
 * than by a Minecraft plugin.
 */
@SculkStable
public interface SculkWebServer : SculkHandle {
    @SculkStable
    public val running: Boolean

    /** The port actually bound. Differs from the requested one when 0 was asked for. */
    @SculkStable
    public val port: Int

    @SculkStable
    public suspend fun start(): SculkResult<Unit>

    /**
     * Registers a handler.
     *
     * Routes are declared before [start] and are immutable afterwards, so a plugin cannot add an
     * unauthenticated endpoint at runtime by accident.
     */
    @SculkStable
    public fun route(method: HttpMethod, path: String, handler: suspend (WebRequest) -> WebResponse)

    /** Serves static assets from a classpath resource root, for a bundled frontend. */
    @SculkStable
    public fun static(pathPrefix: String, resourceRoot: String)
}

@SculkStable
public enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
}

/**
 * One inbound request.
 *
 * [body] is a String rather than a stream: it is already bounded by [WebConfig.maxBodyBytes], and
 * handing a plugin an unbounded stream is how a request body becomes an out-of-memory error.
 */
@SculkStable
public data class WebRequest(
    public val method: HttpMethod,
    public val path: String,
    public val query: Map<String, String>,
    public val headers: Map<String, String>,
    public val body: String,
    /**
     * The client address.
     *
     * Behind a reverse proxy this is the proxy unless [WebConfig.trustedProxies] names it, because
     * believing `X-Forwarded-For` from an untrusted source lets anyone claim any address -- which
     * defeats rate limiting and address-bound sessions in one step.
     */
    public val remoteAddress: String,
) {
    @SculkStable
    public fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    @SculkStable
    public fun cookie(name: String): String? = header("Cookie")
        ?.split(';')
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
}

@SculkStable
public data class WebResponse(
    public val status: Int,
    public val body: String = "",
    public val contentType: String = "application/json",
    public val headers: Map<String, String> = emptyMap(),
) {
    @SculkStable
    public companion object {
        @SculkStable
        public fun json(body: String): WebResponse = WebResponse(200, body)

        @SculkStable
        public fun noContent(): WebResponse = WebResponse(204)

        /**
         * A refusal.
         *
         * The message is for a staff member reading it, so it must not carry a stack trace or a
         * SQL fragment -- an error body is the cheapest place to leak how something works.
         */
        @SculkStable
        public fun error(status: Int, message: String): WebResponse = WebResponse(status, """{"error":${quote(message)}}""")

        private fun quote(text: String): String = buildString {
            append('"')
            text.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
            append('"')
        }
    }
}

@SculkStable
public data class WebConfig(
    public val port: Int = 8080,
    /** Loopback by default. See [SculkWebServer] for why that is the right inconvenience. */
    public val bind: String = "127.0.0.1",
    /**
     * Bounded, because an unbounded body is a denial of service that needs no skill to perform.
     */
    public val maxBodyBytes: Long = 1L * 1024 * 1024,
    /**
     * Proxies whose `X-Forwarded-For` is believed.
     *
     * Empty means believe nobody, which is the safe default: an unvalidated forwarded header lets
     * a client claim any address it likes.
     */
    public val trustedProxies: Set<String> = emptySet(),
) {
    init {
        require(port in 0..65535) { "Port must be 0..65535, got $port" }
        require(maxBodyBytes > 0) { "Body limit must be positive" }
    }
}

/** Finds a backend and builds a server. */
@SculkStable
public interface SculkWebProvider {
    public val backend: String

    public fun create(config: WebConfig): SculkWebServer
}

/**
 * Finds a backend and builds a server.
 *
 * The same entry point shape as `SculkDiscord.create`, and for the same reason: a consumer names
 * the abstraction, never the implementation, so swapping Ktor for something else is a dependency
 * change rather than an edit.
 */
@SculkStable
public object SculkWeb {
    /**
     * Creates a server from whichever backend is on the classpath.
     *
     * [loaders] matters more than it looks. The single-argument `ServiceLoader.load` uses the
     * thread-context classloader, which **cannot see a Paper plugin's own jar** -- so a plugin must
     * pass one of its own classes' loaders or discovery silently finds nothing. `sculk-discord`
     * carries the identical caveat, and it is the single most common way this kind of wiring fails.
     */
    @SculkStable
    public fun create(
        config: WebConfig,
        loaders: List<ClassLoader> = listOf(SculkWeb::class.java.classLoader),
    ): SculkResult<SculkWebServer> {
        val providers = discover(loaders)

        return when {
            providers.isEmpty() ->
                SculkResult.failure(
                    "No sculk-web backend is on the classpath. Add sculk-web-ktor (or another " +
                        "backend) and make sure its ServiceLoader descriptor survived shading -- " +
                        "shadowJar needs mergeServiceFiles().",
                )

            else -> SculkResult.success(providers.first().create(config))
        }
    }

    /** Every backend visible to [loaders], for diagnostics. */
    @SculkStable
    public fun discover(loaders: List<ClassLoader>): List<SculkWebProvider> = loaders
        .flatMap { loader ->
            // A broken descriptor from one jar must not hide every other backend.
            runCatching { ServiceLoader.load(SculkWebProvider::class.java, loader).toList() }
                .getOrDefault(emptyList())
        }.distinctBy { it.backend }

    /**
     * A server whose every call fails by name.
     *
     * Returned instead of null so a consumer that ignores the failure gets a message saying what
     * went wrong rather than a NullPointerException three frames away.
     */
    @SculkStable
    public fun disabled(reason: String): SculkWebServer = DisabledWebServer(reason)
}

private class DisabledWebServer(private val reason: String) : SculkWebServer {
    override val running: Boolean = false
    override val port: Int = 0

    override suspend fun start(): SculkResult<Unit> = SculkResult.failure("Web server disabled: $reason")

    override fun route(method: HttpMethod, path: String, handler: suspend (WebRequest) -> WebResponse): Unit = Unit

    override fun static(pathPrefix: String, resourceRoot: String): Unit = Unit

    override fun close(): Unit = Unit
}
