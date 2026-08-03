package studio.sculk.discord.jda

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent
import net.dv8tion.jda.api.events.session.SessionRecreateEvent
import net.dv8tion.jda.api.events.session.SessionResumeEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.coroutine.await
import studio.sculk.discord.BotConfig
import studio.sculk.discord.ChannelId
import studio.sculk.discord.ComponentId
import studio.sculk.discord.DiscordGateway
import studio.sculk.discord.GatewayState
import studio.sculk.discord.GuildId
import studio.sculk.discord.Intent
import studio.sculk.discord.MessageId
import studio.sculk.discord.Presence
import studio.sculk.discord.UserId
import studio.sculk.discord.command.DiscordCommandSpec
import studio.sculk.discord.interaction.ComponentInteraction
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.discord.message.DiscordMessage
import studio.sculk.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import kotlin.time.Duration

/**
 * The JDA implementation of [DiscordGateway].
 *
 * Reconnection is owned here, once. One of the two projects this replaces had a full backoff state
 * machine and the other had nothing at all — the second simply stopped posting after its first
 * disconnect, with no log line saying so, until someone restarted the server.
 */
@SculkInternal
public class JdaGateway(
    private val config: BotConfig,
    private val scope: CoroutineScope,
    private val logger: Logger = Logger.getLogger("SculkDiscord"),
) : DiscordGateway {
    private val stateRef = AtomicReference(GatewayState.Disabled)
    private val routers = mutableListOf<InteractionRouter>()
    private val collectors = ConcurrentHashMap<String, MutableList<Collector>>()

    @Volatile
    private var jda: JDA? = null

    @Volatile
    private var ready: CompletableDeferred<SculkResult<Unit>>? = null

    @Volatile
    private var shuttingDown = false
    private var retry: Job? = null
    private var attempts = 0

    override val state: GatewayState get() = stateRef.get()
    override val selfId: UserId? get() = jda?.selfUser?.id?.let(::UserId)

    private class Collector(val messageId: String, val from: UserId?, val signal: CompletableDeferred<ComponentInteraction>)

    /**
     * Connects and waits for the gateway to become usable.
     *
     * Awaiting matters: JDA's `build()` returns before the handshake finishes, and a caller that sends
     * immediately after a non-awaited connect has its message dropped with nothing to indicate it was
     * ever attempted.
     */
    override suspend fun connect(): SculkResult<Unit> {
        if (!config.configured) {
            stateRef.set(GatewayState.Disabled)
            return SculkResult.failure("No bot token is configured, so nothing was connected.")
        }
        shuttingDown = false
        val signal = CompletableDeferred<SculkResult<Unit>>()
        ready = signal
        stateRef.set(GatewayState.Connecting)

        val started = runCatching {
            JDABuilder.createLight(config.token, config.intents.map { it.toJda() })
                .setMemberCachePolicy(if (Intent.GuildMembers in config.intents) MemberCachePolicy.DEFAULT else MemberCachePolicy.NONE)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(Listener())
                .build()
        }
        started.onFailure { error ->
            stateRef.set(GatewayState.Degraded)
            scheduleReconnect("startup failed: ${error.message}")
            return SculkResult.failure("Could not start the Discord gateway: ${error.message}", error)
        }
        jda = started.getOrNull()
        return signal.await()
    }

    override suspend fun send(channel: ChannelId, message: DiscordMessage): SculkResult<MessageId> {
        val target = textChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return attempt("send to ${channel.raw}") {
            MessageId(target.value.sendMessage(message.toCreateData()).submit().await().id)
        }
    }

    override suspend fun edit(channel: ChannelId, message: MessageId, replacement: DiscordMessage): SculkResult<Unit> {
        val target = textChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return attempt("edit ${message.raw}") {
            target.value
                .editMessageComponentsById(message.raw, replacement.toTopLevelComponents())
                .useComponentsV2()
                .submit()
                .await()
        }.map { }
    }

    override suspend fun delete(channel: ChannelId, message: MessageId): SculkResult<Unit> {
        val target = textChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return attempt("delete ${message.raw}") { target.value.deleteMessageById(message.raw).submit().await() }.map { }
    }

    override suspend fun channelExists(channel: ChannelId): SculkResult<Boolean> =
        SculkResult.success(jda?.getTextChannelById(channel.raw) != null)

    override suspend fun presence(activity: Presence): SculkResult<Unit> {
        val client = jda ?: return SculkResult.failure("The gateway is not connected.")
        return attempt("set the presence") {
            client.presence.activity = when (activity.kind) {
                Presence.Kind.Playing -> Activity.playing(activity.text)
                Presence.Kind.Watching -> Activity.watching(activity.text)
                Presence.Kind.Listening -> Activity.listening(activity.text)
                Presence.Kind.Competing -> Activity.competing(activity.text)
            }
        }
    }

    /**
     * Pushes commands, replacing whatever Discord currently has.
     *
     * Replace rather than merge, deliberately: a command deleted from the code but left registered
     * keeps appearing in the client and answering with "not a command this server handles", which
     * reads as a broken bot rather than a stale registration.
     */
    override suspend fun registerCommands(commands: List<DiscordCommandSpec>, guilds: Set<GuildId>): SculkResult<Unit> {
        val client = jda ?: return SculkResult.failure("The gateway is not connected, so commands were not registered.")
        val data = commands.map { it.toJda() }
        return attempt("register ${commands.size} command(s)") {
            if (guilds.isEmpty()) {
                client.updateCommands().addCommands(data).submit().await()
            } else {
                guilds.map { guild ->
                    val target = client.getGuildById(guild.raw)
                        ?: error("guild ${guild.raw} is not one the bot is in")
                    target.updateCommands().addCommands(data).submit().await()
                }
            }
        }.map { }
    }

    override fun route(router: InteractionRouter): SculkHandle {
        synchronized(routers) { routers += router }
        return SculkHandle { synchronized(routers) { routers -= router } }
    }

    override suspend fun awaitComponent(message: MessageId, within: Duration, from: UserId?): SculkResult<ComponentInteraction> {
        val collector = Collector(message.raw, from, CompletableDeferred())
        collectors.computeIfAbsent(message.raw) { mutableListOf() }.also { synchronized(it) { it += collector } }
        return try {
            withTimeoutOrNull(within) { collector.signal.await() }
                ?.let { SculkResult.success(it) }
                ?: SculkResult.failure("Nobody used a component on ${message.raw} within $within.")
        } finally {
            // Always unregistered, including on timeout: a collector left behind holds its coroutine
            // and matches a click made an hour later against a flow nobody is waiting on any more.
            collectors[message.raw]?.let { list -> synchronized(list) { list -= collector } }
            collectors.remove(message.raw, mutableListOf<Collector>())
        }
    }

    override fun close() {
        shuttingDown = true
        stateRef.set(GatewayState.Disconnected)
        retry?.cancel()
        retry = null
        val client = jda
        jda = null
        runCatching { client?.shutdown() }
            .onFailure { logger.fine("The Discord gateway did not shut down cleanly: ${it.message}") }
    }

    private fun textChannel(channel: ChannelId): SculkResult<net.dv8tion.jda.api.entities.channel.concrete.TextChannel> {
        val client = jda ?: return SculkResult.failure("The gateway is $state, so nothing could be sent.")
        // Two different fixes, in two different places, by two different people — so say which.
        return client.getTextChannelById(channel.raw)?.let { SculkResult.success(it) }
            ?: SculkResult.failure(
                "Channel ${channel.raw} is not visible to the bot. Either the id is wrong (check the config) " +
                    "or the bot has not been given View Channel there (check the channel's permissions).",
            )
    }

    private fun scheduleReconnect(reason: String) {
        if (shuttingDown || retry?.isActive == true) return
        val seconds = minOf(MAX_BACKOFF_SECONDS, 1L shl attempts.coerceAtMost(6))
        attempts++
        stateRef.set(GatewayState.Degraded)
        logger.warning("Discord gateway degraded ($reason). Reconnecting in ${seconds}s.")
        retry = scope.launch {
            delay(seconds * 1000)
            if (!shuttingDown) {
                runCatching { jda?.shutdownNow() }
                jda = null
                connect()
            }
        }
    }

    private fun onReady() {
        stateRef.set(GatewayState.Ready)
        attempts = 0
        ready?.complete(SculkResult.ok())
    }

    private inner class Listener : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            if (event.jda !== jda) return
            onReady()
        }

        override fun onSessionResume(event: SessionResumeEvent) {
            if (event.jda === jda) onReady()
        }

        override fun onSessionRecreate(event: SessionRecreateEvent) {
            if (event.jda === jda) onReady()
        }

        override fun onSessionDisconnect(event: SessionDisconnectEvent) {
            if (event.jda === jda) scheduleReconnect(event.closeCode?.name ?: "session disconnected")
        }

        override fun onShutdown(event: ShutdownEvent) {
            if (event.jda === jda && !shuttingDown) scheduleReconnect(event.closeCode?.name ?: "gateway shutdown")
        }

        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            dispatch { it.dispatch(JdaCommandContext(event)) }
        }

        override fun onGenericComponentInteractionCreate(event: GenericComponentInteractionCreateEvent) {
            val id = ComponentId.parse(event.componentId) ?: return
            val interaction = JdaComponentInteraction(event, id)
            // A collector waiting on this message takes it; nothing else sees it, so a confirm flow
            // does not also fire whatever handler owns the namespace.
            if (offerToCollector(event.messageId, interaction)) return
            dispatch { it.dispatch(interaction) }
        }

        override fun onModalInteraction(event: ModalInteractionEvent) {
            val id = ComponentId.parse(event.modalId) ?: return
            dispatch { it.dispatch(JdaModalInteraction(event, id)) }
        }

        /**
         * Answers autocomplete from the spec's lambda.
         *
         * Resolved per keystroke rather than from a list captured at registration, which stops
         * matching the moment a config reload changes the set and then stays wrong until a restart.
         */
        override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
            val path = listOfNotNull(event.name, event.subcommandGroup, event.subcommandName).joinToString(" ")
            val option = snapshot().firstNotNullOfOrNull { router ->
                router.resolve(path)?.options?.firstOrNull { it.name == event.focusedOption.name }
            }
            val suggest = option?.autocomplete ?: return
            scope.launch {
                val choices = runCatching { suggest(event.focusedOption.value) }.getOrDefault(emptyList())
                runCatching {
                    event.replyChoiceStrings(choices.take(MAX_SUGGESTIONS).map { it.value }).submit().await()
                }
            }
        }

        private inline fun dispatch(crossinline block: suspend (InteractionRouter) -> Unit) {
            val targets = snapshot()
            if (targets.isEmpty()) return
            scope.launch { targets.forEach { block(it) } }
        }
    }

    private fun snapshot(): List<InteractionRouter> = synchronized(routers) { routers.toList() }

    private fun offerToCollector(messageId: String, interaction: ComponentInteraction): Boolean {
        val waiting = collectors[messageId] ?: return false
        val match = synchronized(waiting) {
            waiting.firstOrNull { it.from == null || it.from == interaction.actor.id }
        } ?: return false
        return match.signal.complete(interaction)
    }

    private companion object {
        const val MAX_BACKOFF_SECONDS = 300L
        const val MAX_SUGGESTIONS = 25

        fun Intent.toJda(): GatewayIntent = when (this) {
            Intent.GuildMessages -> GatewayIntent.GUILD_MESSAGES
            Intent.DirectMessages -> GatewayIntent.DIRECT_MESSAGES
            Intent.GuildMembers -> GatewayIntent.GUILD_MEMBERS
            Intent.MessageContent -> GatewayIntent.MESSAGE_CONTENT
            Intent.GuildPresences -> GatewayIntent.GUILD_PRESENCES
        }
    }
}

private inline fun <T> attempt(what: String, block: () -> T): SculkResult<T> = runCatching { block() }.fold(
    { SculkResult.success(it) },
    { SculkResult.failure("Could not $what: ${it.message ?: it::class.simpleName}", it) },
)
