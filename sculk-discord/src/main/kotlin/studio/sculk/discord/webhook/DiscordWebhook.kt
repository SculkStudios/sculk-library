package studio.sculk.discord.webhook

import kotlinx.coroutines.Dispatchers
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

        // sendAsync rather than send: the blocking form parks whatever thread called it, and a chat
        // relay calls this from the server thread.
        val response = runCatching {
            withContext(Dispatchers.IO) { http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await() }
        }.getOrElse {
            return SculkResult.failure("The webhook request failed: ${it.message ?: it::class.simpleName}", it)
        }

        if (response.statusCode() in 200..299) return SculkResult.ok()

        val detail = response.body().orEmpty().trim().replace(Regex("\\s+"), " ").take(200)
        return SculkResult.failure(
            "Discord rejected the webhook with HTTP ${response.statusCode()}" + if (detail.isBlank()) "." else ": $detail",
        )
    }

    override fun close() {
        runCatching { http.close() }
    }
}
