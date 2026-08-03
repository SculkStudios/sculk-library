package studio.sculk.discord

import kotlinx.coroutines.CoroutineScope
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.command.DiscordCommandSpec
import studio.sculk.discord.interaction.ComponentInteraction
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.discord.message.DiscordMessage
import java.util.ServiceLoader
import kotlin.time.Duration

/**
 * Finds a Discord backend and builds a gateway on it.
 *
 * ```kotlin
 * val gateway = SculkDiscord.create(BotConfig(token), scope).getOrElse { reason, _ ->
 *     logger.warning(reason)
 *     return
 * }
 * gateway.connect()
 * ```
 */
@SculkStable
public object SculkDiscord {
    /**
     * Builds a gateway, or fails saying which half is missing.
     *
     * [loaders] exists because a backend usually ships *inside* the consuming application's jar. The
     * single-argument [ServiceLoader.load] searches the thread context class loader, which for a
     * Paper plugin during enable belongs to the server and cannot see the plugin's own jar — so every
     * backend looks uninstalled. Pass the loader of a class you own.
     */
    public fun create(
        config: BotConfig,
        scope: CoroutineScope,
        loaders: List<ClassLoader> = listOf(SculkDiscord::class.java.classLoader),
    ): SculkResult<DiscordGateway> = createWith(config, scope, discover(loaders))

    /** Every backend on the classpath, whether or not its library is present. */
    public fun discover(loaders: List<ClassLoader>): List<DiscordGatewayProvider> =
        (loaders + DiscordGatewayProvider::class.java.classLoader)
            .distinct()
            .flatMap { loader ->
                runCatching { ServiceLoader.load(DiscordGatewayProvider::class.java, loader).toList() }
                    .getOrDefault(emptyList())
            }.distinctBy { it.javaClass.name }

    /**
     * Named rather than overloading [create]: two overloads separated only by their list element
     * type make `create(config, scope, emptyList())` ambiguous, and the caller has to annotate a
     * type parameter to say which one they meant.
     */
    internal fun createWith(
        config: BotConfig,
        scope: CoroutineScope,
        providers: List<DiscordGatewayProvider>,
    ): SculkResult<DiscordGateway> {
        if (!config.configured) {
            return SculkResult.failure(
                "No Discord bot token is configured, so the gateway was not started. " +
                    "Paste a token into the config, or leave it blank to keep Discord off.",
            )
        }
        if (providers.isEmpty()) {
            return SculkResult.failure(
                "No Discord backend found. Add sculk-discord-jda to your dependencies. If you shade " +
                    "Sculk, make sure your shadow configuration merges META-INF/services descriptors — " +
                    "a dropped descriptor is indistinguishable from a missing dependency at runtime.",
            )
        }
        val provider = providers.firstOrNull { it.isAvailable() }
            ?: return SculkResult.failure(
                "Backend(s) ${providers.joinToString(", ") { it.backend }} are on the classpath, but none " +
                    "reported their client library as loadable. For a Paper plugin, check that JDA is " +
                    "listed under `libraries:` in your paper-plugin.yml.",
            )
        return SculkResult.success(provider.create(config, scope))
    }

    /**
     * A gateway that is permanently down, whose every call fails by name.
     *
     * Handed out instead of null so a consumer never branches on nullability to find out that Discord
     * is off. The calls still report a reason, so "nothing posted to Discord" shows up in the log as
     * a sentence rather than as silence.
     */
    @SculkStable
    public fun disabled(reason: String): DiscordGateway = DisabledGateway(reason)
}

private class DisabledGateway(private val reason: String) : DiscordGateway {
    override val state: GatewayState = GatewayState.Disabled
    override val selfId: UserId? = null

    private fun <T> refuse(): SculkResult<T> = SculkResult.failure("Discord is not available: $reason")

    override suspend fun connect(): SculkResult<Unit> = refuse()

    override suspend fun send(channel: ChannelId, message: DiscordMessage): SculkResult<MessageId> = refuse()

    override suspend fun edit(channel: ChannelId, message: MessageId, replacement: DiscordMessage): SculkResult<Unit> = refuse()

    override suspend fun delete(channel: ChannelId, message: MessageId): SculkResult<Unit> = refuse()

    override suspend fun channelExists(channel: ChannelId): SculkResult<Boolean> = refuse()

    override suspend fun presence(activity: Presence): SculkResult<Unit> = refuse()

    override suspend fun registerCommands(commands: List<DiscordCommandSpec>, guilds: Set<GuildId>): SculkResult<Unit> = refuse()

    override fun route(router: InteractionRouter): SculkHandle = SculkHandle.NONE

    override suspend fun awaitComponent(message: MessageId, within: Duration, from: UserId?): SculkResult<ComponentInteraction> = refuse()

    override fun close() {}
}
