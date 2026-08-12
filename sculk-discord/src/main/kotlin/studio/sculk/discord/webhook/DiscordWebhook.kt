package studio.sculk.discord.webhook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.coroutine.await
import studio.sculk.discord.Mentions
import studio.sculk.discord.RateLimiter
import studio.sculk.discord.message.DiscordMessage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Posts to a Discord webhook.
 *
 * The zero-gateway path: no bot token, no intents, no threads beyond the HTTP client, and it works on
 * a server whose operator wants incident logging and nothing else. Worth keeping available even where
 * a gateway exists — a webhook still delivers while the gateway is reconnecting, which is exactly when
 * something worth reporting tends to be happening.
 *
 * A [SculkHandle]: closing shuts the HTTP client's selector and executor threads down. Skipping that
 * leaks a thread set per plugin enable, which a `/reload`-happy server turns into a real leak.
 */
@SculkStable
public class DiscordWebhook(
    private val url: String,
    private val maxPerMinute: Int = 30,
    clock: () -> Long = System::currentTimeMillis,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : SculkHandle {
    private val limiter = RateLimiter(clock)
    private val json = Json {
        encodeDefaults = true
        // A null username means "use the webhook's configured name", which is not the same request as
        // sending `"username": null` — Discord rejects the latter.
        explicitNulls = false
    }

    /**
     * Sends [message], or reports why it could not.
     *
     * A rate-limited send fails by name rather than returning quietly: the caller usually has a
     * fallback, and "dropped, over the limit" is a different decision from "the network refused it".
     */
    public suspend fun send(message: DiscordMessage, username: String? = null, avatarUrl: String? = null): SculkResult<Unit> {
        undeliverableReason(message)?.let { return SculkResult.failure(it) }
        if (!limiter.acquire(maxPerMinute)) {
            return SculkResult.failure("Webhook rate limit of $maxPerMinute/minute reached; the message was dropped.")
        }
        return post(payloadFor(message, username, avatarUrl))
    }

    /** The plain-text case: a relayed chat line posted under a player's name and face. */
    public suspend fun sendText(
        content: String,
        username: String? = null,
        avatarUrl: String? = null,
        mentions: Mentions = Mentions.None,
    ): SculkResult<Unit> {
        if (!limiter.acquire(maxPerMinute)) {
            return SculkResult.failure("Webhook rate limit of $maxPerMinute/minute reached; the message was dropped.")
        }
        return post(
            WebhookPayload(
                content = content.take(MAX_CONTENT),
                username = username?.take(MAX_USERNAME),
                avatarUrl = avatarUrl,
                allowedMentions = allowedMentionsFor(mentions),
            ),
        )
    }

    /**
     * Posts once, and once more if Discord asked us to wait.
     *
     * The local [RateLimiter] guesses; Discord knows. A 429 carries `Retry-After` saying exactly how
     * long, and treating it as an ordinary rejection throws away the one piece of information that
     * would have let the message through — which for a chat relay means a dropped line during exactly
     * the burst that caused the limit. Retried once, not in a loop: repeated 429s mean the caller is
     * over budget, and hammering a rate limit is how a webhook gets shut off entirely.
     */
    private suspend fun post(payload: WebhookPayload): SculkResult<Unit> {
        val request = runCatching {
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(payload), StandardCharsets.UTF_8))
                .build()
        }.getOrElse {
            return SculkResult.failure("The webhook URL is not usable; correct or disable it. (${it.message})")
        }

        val first = attempt(request)
        val wait = first.retryAfter() ?: return first.toResult()
        delay(wait)
        return attempt(request).toResult()
    }

    private suspend fun attempt(request: HttpRequest): SculkResult<HttpResponse<String>> = runCatching {
        // sendAsync rather than send: the blocking form parks whatever thread called it, and a chat
        // relay calls this from the server thread.
        withContext(Dispatchers.IO) { http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await() }
    }.fold(
        { SculkResult.success(it) },
        { SculkResult.failure("The webhook request failed: ${it.message ?: it::class.simpleName}", it) },
    )

    /** How long Discord said to wait, or null when this was not a rate limit worth retrying. */
    private fun SculkResult<HttpResponse<String>>.retryAfter(): Long? {
        val response = (this as? SculkResult.Success)?.value ?: return null
        if (response.statusCode() != TOO_MANY_REQUESTS) return null
        val seconds = response.headers().firstValue("Retry-After").orElse(null)?.toDoubleOrNull() ?: return null
        val millis = (seconds * 1000).toLong()
        // A limit measured in minutes is not something to sit on holding a coroutine; that one is the
        // caller's problem to solve by sending less.
        return millis.takeIf { it in 0..MAX_RETRY_AFTER_MILLIS }
    }

    private fun SculkResult<HttpResponse<String>>.toResult(): SculkResult<Unit> {
        val response = when (this) {
            is SculkResult.Success -> value
            is SculkResult.Failure -> return SculkResult.failure(message, cause)
        }
        if (response.statusCode() in 200..299) return SculkResult.ok()

        val detail = response.body().orEmpty().trim().replace(Regex("\\s+"), " ").take(MAX_DETAIL)
        val hint = if (response.statusCode() == TOO_MANY_REQUESTS) {
            " The webhook is being posted to faster than Discord allows; lower maxPerMinute or send less."
        } else {
            ""
        }
        return SculkResult.failure(
            "Discord rejected the webhook with HTTP ${response.statusCode()}" +
                (if (detail.isBlank()) "." else ": $detail") + hint,
        )
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val MAX_RETRY_AFTER_MILLIS = 10_000L
        const val MAX_DETAIL = 200
    }

    override fun close() {
        runCatching { http.close() }
    }
}
