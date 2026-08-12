package studio.sculk.discord.jda

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent
import net.dv8tion.jda.api.events.session.SessionRecreateEvent
import net.dv8tion.jda.api.events.session.SessionResumeEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.coroutine.await
import studio.sculk.discord.BotConfig
import studio.sculk.discord.ChannelId
import studio.sculk.discord.ComponentId
import studio.sculk.discord.DeletedMessage
import studio.sculk.discord.DiscordAttachment
import studio.sculk.discord.DiscordChatMessage
import studio.sculk.discord.DiscordGateway
import studio.sculk.discord.GatewayState
import studio.sculk.discord.GuildId
import studio.sculk.discord.GuildService
import studio.sculk.discord.Intent
import studio.sculk.discord.MemberChange
import studio.sculk.discord.MessageId
import studio.sculk.discord.Presence
import studio.sculk.discord.ReplyContext
import studio.sculk.discord.RoleId
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
import kotlin.time.toJavaDuration

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
    private val messageHandlers = mutableListOf<suspend (DiscordChatMessage) -> Unit>()
    private val editHandlers = mutableListOf<suspend (DiscordChatMessage) -> Unit>()
    private val deleteHandlers = mutableListOf<suspend (DeletedMessage) -> Unit>()
    private val memberHandlers = mutableListOf<suspend (MemberChange) -> Unit>()
    private val collectors = ConcurrentHashMap<String, MutableList<Collector>>()
    private val sends = ChannelSends()

    @Volatile
    private var jda: JDA? = null

    @Volatile
    private var ready: CompletableDeferred<SculkResult<Unit>>? = null

    @Volatile
    private var shuttingDown = false
    private var retry: Job? = null
    private var attempts = 0
    private val reconnectLock = Any()

    override val state: GatewayState get() = stateRef.get()
    override val selfId: UserId? get() = jda?.selfUser?.id?.let(::UserId)
    override val guilds: GuildService = JdaGuildService { jda }

    /** The message id is the map key, so it is not repeated here. */
    private class Collector(val from: UserId?, val signal: CompletableDeferred<ComponentInteraction>)

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

        val cacheMembers = config.cacheAllMembers && Intent.GuildMembers in config.intents
        if (config.cacheAllMembers && !cacheMembers) {
            logger.warning(
                "cacheAllMembers is set but Intent.GuildMembers was not requested, so Discord never sends " +
                    "the members to cache and every member lookup stays a request. Add the intent, or turn " +
                    "the setting off.",
            )
        }

        val started = runCatching {
            JDABuilder.createLight(config.token, config.intents.map { it.toJda() })
                // Explicitly one of two states. MemberCachePolicy.DEFAULT is VOICE.or(OWNER), and
                // createLight has already disabled the voice-state cache it depends on — so "default"
                // here cached the guild owner and nothing else, while looking like it cached members.
                .setMemberCachePolicy(if (cacheMembers) MemberCachePolicy.ALL else MemberCachePolicy.NONE)
                .setChunkingFilter(if (cacheMembers) ChunkingFilter.ALL else ChunkingFilter.NONE)
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
        val target = messageChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return sends.ordered(channel.raw, "send to ${channel.raw}") {
            MessageId(target.value.sendMessage(message.toCreateData()).submit().await().id)
        }
    }

    override suspend fun edit(channel: ChannelId, message: MessageId, replacement: DiscordMessage): SculkResult<Unit> {
        val target = messageChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return attempt("edit ${message.raw}") {
            target.value
                .editMessageComponentsById(message.raw, replacement.toTopLevelComponents())
                .useComponentsV2()
                .submit()
                .await()
        }.map { }
    }

    override suspend fun react(channel: ChannelId, message: MessageId, emoji: String): SculkResult<Unit> {
        val target = messageChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return attempt("react to ${message.raw}") {
            target.value.addReactionById(message.raw, Emoji.fromFormatted(emoji)).submit().await()
        }.map { }
    }

    override suspend fun delete(channel: ChannelId, message: MessageId): SculkResult<Unit> {
        val target = messageChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return attempt("delete ${message.raw}") { target.value.deleteMessageById(message.raw).submit().await() }.map { }
    }

    /**
     * Whether the bot can post in [channel], and when it cannot, which of the three reasons applies.
     *
     * Returning a bare false for all of "not connected", "no such channel" and "no permission there"
     * makes a setup check useless — those are fixed in three different places by three different
     * people, and the one thing an operator needs from a diagnostic is which.
     */
    override suspend fun channelExists(channel: ChannelId): SculkResult<Boolean> {
        val client = jda ?: return SculkResult.failure("The gateway is $state, so no channel could be checked.")
        val target = client.getChannelById(GuildMessageChannel::class.java, channel.raw)
            ?: return SculkResult.success(false)
        return if (target.canTalk()) {
            SculkResult.success(true)
        } else {
            SculkResult.failure(
                "The bot can see #${target.name} but cannot post in it. Grant it View Channel and Send " +
                    "Messages there — for a thread, on the parent channel.",
            )
        }
    }

    override suspend fun sendTyping(channel: ChannelId): SculkResult<Unit> {
        val target = messageChannel(channel).let { it as? SculkResult.Success ?: return it as SculkResult.Failure }
        return attempt("show typing in ${channel.raw}") { target.value.sendTyping().submit().await() }.map { }
    }

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

    override fun onMessageEdit(handler: suspend (DiscordChatMessage) -> Unit): SculkHandle {
        synchronized(editHandlers) { editHandlers += handler }
        return SculkHandle { synchronized(editHandlers) { editHandlers -= handler } }
    }

    override fun onMessageDelete(handler: suspend (DeletedMessage) -> Unit): SculkHandle {
        synchronized(deleteHandlers) { deleteHandlers += handler }
        return SculkHandle { synchronized(deleteHandlers) { deleteHandlers -= handler } }
    }

    override fun onMemberChange(handler: suspend (MemberChange) -> Unit): SculkHandle {
        synchronized(memberHandlers) { memberHandlers += handler }
        if (Intent.GuildMembers !in config.intents) {
            logger.warning(
                "A member handler was registered but Intent.GuildMembers was not requested, so Discord " +
                    "will never deliver one. GuildMembers is privileged: enable it on the application at " +
                    "discord.com/developers before requesting it.",
            )
        }
        return SculkHandle { synchronized(memberHandlers) { memberHandlers -= handler } }
    }

    override fun onMessage(handler: suspend (DiscordChatMessage) -> Unit): SculkHandle {
        synchronized(messageHandlers) { messageHandlers += handler }
        if (Intent.GuildMessages !in config.intents) {
            logger.warning(
                "A message handler was registered but Intent.GuildMessages was not requested, so Discord " +
                    "will never deliver one. Add it to the bot config.",
            )
        } else if (Intent.MessageContent !in config.intents) {
            logger.warning(
                "Message handlers are registered without Intent.MessageContent, so every message will " +
                    "arrive with empty content. MessageContent is privileged: enable it on the application " +
                    "at discord.com/developers before requesting it.",
            )
        }
        return SculkHandle { synchronized(messageHandlers) { messageHandlers -= handler } }
    }

    override suspend fun awaitComponent(message: MessageId, within: Duration, from: UserId?): SculkResult<ComponentInteraction> {
        val collector = Collector(from, CompletableDeferred())
        collectors.computeIfAbsent(message.raw) { mutableListOf() }.also { synchronized(it) { it += collector } }
        return try {
            withTimeoutOrNull(within) { collector.signal.await() }
                ?.let { SculkResult.success(it) }
                ?: SculkResult.failure("Nobody used a component on ${message.raw} within $within.")
        } finally {
            // Always unregistered, including on timeout: a collector left behind holds its coroutine
            // and matches a click made an hour later against a flow nobody is waiting on any more.
            //
            // The removal is inside the same lock as the drain. The two-step version — remove from the
            // list, then remove the key if the list happened to equal an empty one — could interleave
            // with a concurrent registration, leaving an empty entry per message id behind for as long
            // as the bot ran.
            collectors.computeIfPresent(message.raw) { _, list ->
                synchronized(list) {
                    list -= collector
                    list.ifEmpty { null }
                }
            }
        }
    }

    override fun close() {
        stopping()?.let { client ->
            runCatching { client.shutdown() }
                .onFailure { logger.fine("The Discord gateway did not shut down cleanly: ${it.message}") }
        }
    }

    override suspend fun closeAwaiting(timeout: Duration): SculkResult<Unit> {
        val client = stopping() ?: return SculkResult.ok()
        return runCatching {
            client.shutdown()
            // awaitShutdown parks the calling thread, so it does not belong on whichever dispatcher
            // the caller happened to be on — least of all a server's main thread during disable.
            withContext(Dispatchers.IO) { client.awaitShutdown(timeout.toJavaDuration()) }
        }.fold(
            { finished ->
                if (finished) {
                    SculkResult.ok()
                } else {
                    SculkResult.failure("The Discord gateway still had work in flight after $timeout, so it was left to close on its own.")
                }
            },
            { SculkResult.failure("Could not shut the Discord gateway down: ${it.message ?: it::class.simpleName}", it) },
        )
    }

    /** Marks the gateway stopped and hands back the client to shut down, or null if there was none. */
    private fun stopping(): JDA? {
        shuttingDown = true
        stateRef.set(GatewayState.Disconnected)
        retry?.cancel()
        retry = null
        return jda.also { jda = null }
    }

    /**
     * Resolves anything a message can be posted to.
     *
     * `GuildMessageChannel` rather than `TextChannel`: a thread, an announcement channel and a forum
     * post are all valid targets, and looking only for a plain text channel reported every one of them
     * as "not visible to the bot" — which sends an operator to check permissions on a channel whose id
     * was perfectly correct.
     */
    private fun messageChannel(channel: ChannelId): SculkResult<GuildMessageChannel> {
        // Checked before the client, and not merely because the client may be null: during a
        // reconnect the old JDA is still referenced but dead, so a send would be dispatched into it
        // and fail with whatever JDA throws rather than saying the gateway is down.
        if (!state.usable) return SculkResult.failure("The gateway is $state, so nothing could be sent.")
        val client = jda ?: return SculkResult.failure("The gateway is $state, so nothing could be sent.")
        // Two different fixes, in two different places, by two different people — so say which.
        return client.getChannelById(GuildMessageChannel::class.java, channel.raw)?.let { SculkResult.success(it) }
            ?: SculkResult.failure(
                "Channel ${channel.raw} is not visible to the bot. Either the id is wrong (check the config) " +
                    "or the bot has not been given View Channel there (check the channel's permissions). " +
                    "For a thread, the permission is on its parent channel.",
            )
    }

    /**
     * Backs off and reconnects, once.
     *
     * Synchronized because the callers are a JDA event thread, a coroutine and whatever thread called
     * [connect]: an unguarded check-then-set on [retry] lets two disconnect events a millisecond apart
     * both see no active retry and start their own, and two reconnect loops against the same token is
     * a way to get rate-limited off the gateway entirely.
     */
    private fun scheduleReconnect(reason: String): Unit = synchronized(reconnectLock) {
        if (shuttingDown || retry?.isActive == true) return
        val seconds = minOf(MAX_BACKOFF_SECONDS, 1L shl attempts.coerceAtMost(6))
        attempts++
        stateRef.set(GatewayState.Degraded)
        // Dropped now rather than inside the delayed coroutine. While the field still pointed at the
        // dead client, a send during the backoff window was dispatched into it and failed with
        // whatever JDA threw, instead of saying the gateway was reconnecting.
        val dead = jda
        jda = null
        logger.warning("Discord gateway degraded ($reason). Reconnecting in ${seconds}s.")
        retry = scope.launch {
            delay(seconds * 1000)
            // Off the event thread: JDA deadlocks if a listener shuts its own client down.
            runCatching { dead?.shutdownNow() }
            if (!shuttingDown) connect()
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

        /**
         * Relays a human's message.
         *
         * Bots and webhooks are dropped here, this bot included. A relay that reacts to its own post
         * is an infinite loop whose only brake is a rate limit, and it is the first bug every chat
         * bridge writes — so it is not left to the handler to remember.
         */
        override fun onMessageReceived(event: MessageReceivedEvent) {
            if (event.jda !== jda || event.author.isBot || event.isWebhookMessage) return
            val handlers = synchronized(messageHandlers) { messageHandlers.toList() }
            if (handlers.isEmpty()) return
            val guild = if (event.isFromGuild) GuildId(event.guild.id) else null
            val message = event.message.toChatMessage(event.member, guild)
            scope.launch { handlers.deliver("message") { it(message) } }
        }

        /** Runs every handler, so one throwing does not stop the rest from seeing the event. */
        private suspend fun <T> List<T>.deliver(what: String, block: suspend (T) -> Unit) {
            forEach { handler ->
                runCatching { block(handler) }
                    .onFailure { logger.warning("A Discord $what handler failed: ${it.message}") }
            }
        }

        override fun onMessageUpdate(event: MessageUpdateEvent) {
            if (event.jda !== jda || event.author.isBot || event.message.isWebhookMessage) return
            val handlers = synchronized(editHandlers) { editHandlers.toList() }
            if (handlers.isEmpty()) return
            val message = event.message.toChatMessage(event.member, if (event.isFromGuild) GuildId(event.guild.id) else null)
            scope.launch { handlers.deliver("edit") { it(message) } }
        }

        override fun onMessageDelete(event: MessageDeleteEvent) {
            val guild = if (event.isFromGuild) GuildId(event.guild.id) else null
            deleted(listOf(DeletedMessage(MessageId(event.messageId), ChannelId(event.channel.id), guild)))
        }

        /**
         * A purge arrives as one event listing many ids.
         *
         * Fanned out to one [DeletedMessage] each so a consumer writes the single-deletion path once
         * and gets purges for free — the alternative is two handlers where one is always the one
         * somebody forgot.
         */
        override fun onMessageBulkDelete(event: MessageBulkDeleteEvent) {
            val guild = GuildId(event.guild.id)
            deleted(event.messageIds.map { DeletedMessage(MessageId(it), ChannelId(event.channel.id), guild) })
        }

        override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
            member(MemberChange.Joined(GuildId(event.guild.id), UserId(event.member.id), event.member.toActor()))
        }

        override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
            member(MemberChange.Left(GuildId(event.guild.id), UserId(event.user.id)))
        }

        override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
            member(
                MemberChange.RolesChanged(
                    guild = GuildId(event.guild.id),
                    user = UserId(event.member.id),
                    actor = event.member.toActor(),
                    added = event.roles.map { RoleId(it.id) }.toSet(),
                ),
            )
        }

        override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) {
            member(
                MemberChange.RolesChanged(
                    guild = GuildId(event.guild.id),
                    user = UserId(event.member.id),
                    actor = event.member.toActor(),
                    removed = event.roles.map { RoleId(it.id) }.toSet(),
                ),
            )
        }

        private fun deleted(messages: List<DeletedMessage>) {
            val handlers = synchronized(deleteHandlers) { deleteHandlers.toList() }
            if (handlers.isEmpty()) return
            scope.launch { messages.forEach { message -> handlers.deliver("delete") { it(message) } } }
        }

        private fun member(change: MemberChange) {
            val handlers = synchronized(memberHandlers) { memberHandlers.toList() }
            if (handlers.isEmpty()) return
            scope.launch { handlers.deliver("member") { it(change) } }
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
                // Logged rather than swallowed: a throwing supplier used to produce an empty list,
                // which in the client is indistinguishable from "nothing matches what you typed".
                val choices = runCatching { suggest(event.focusedOption.value) }.getOrElse { error ->
                    logger.warning("Autocomplete for /$path failed: ${error.message ?: error::class.simpleName}")
                    emptyList()
                }
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

/**
 * The neutral form of a message, for both the received and the edited paths.
 *
 * [member] is passed rather than read off the message because JDA exposes it on the event, not the
 * message, and the two paths reach it differently.
 */
private fun Message.toChatMessage(member: net.dv8tion.jda.api.entities.Member?, guild: GuildId?): DiscordChatMessage = DiscordChatMessage(
    id = MessageId(id),
    channel = ChannelId(channel.id),
    guild = guild,
    author = member?.toActor() ?: author.toActor(guild),
    content = contentRaw,
    // contentDisplay resolves mentions to names. Relaying contentRaw instead puts a bare <@493...> in
    // front of players, which is the message and is not readable.
    displayContent = contentDisplay,
    attachments = attachments.map { it.toAttachment() },
    reply = referencedMessage?.toReplyContext(guild),
)

private fun Message.Attachment.toAttachment(): DiscordAttachment = DiscordAttachment(
    fileName = fileName,
    url = url,
    sizeBytes = size.toLong(),
    contentType = contentType,
)

/**
 * The quoted half of a reply, shortened for display.
 *
 * Truncated here rather than by each consumer: the excerpt exists to be rendered above a relayed line,
 * and a paragraph-long quote pushed into Minecraft chat costs more screen than the reply it explains.
 * The author resolves to null when Discord did not send the referenced message — deleted, or old
 * enough to have fallen out of cache — which is why the field is nullable rather than a lie.
 */
private fun Message.toReplyContext(guild: GuildId?): ReplyContext {
    val text = contentDisplay.replace('\n', ' ').trim()
    return ReplyContext(
        messageId = MessageId(id),
        author = member?.toActor() ?: author.toActor(guild),
        excerpt = if (text.length <= ReplyContext.MAX_EXCERPT) text else text.take(ReplyContext.MAX_EXCERPT - 1) + "…",
    )
}
