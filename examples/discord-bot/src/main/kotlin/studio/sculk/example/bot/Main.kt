package studio.sculk.example.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import studio.sculk.SculkHandle
import studio.sculk.discord.BotConfig
import studio.sculk.discord.GuildId
import studio.sculk.discord.Intent
import studio.sculk.discord.Presence
import studio.sculk.discord.SculkDiscord
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.getOrElse
import java.util.concurrent.CountDownLatch
import java.util.logging.Logger
import kotlin.coroutines.EmptyCoroutineContext

private val logger: Logger = Logger.getLogger("example-bot")

/**
 * A standalone Discord bot.
 *
 * There is no Minecraft anywhere in this example, and that is the point: `sculk-discord` imports no
 * Bukkit, so the API a plugin uses to run a bot alongside a server is the same one this uses to run
 * without one. A build check enforces it rather than trusting the import list.
 *
 * Run it with:
 * ```
 * DISCORD_TOKEN=… DISCORD_GUILD=… ./gradlew :examples:discord-bot:run
 * ```
 *
 * `DISCORD_GUILD` is optional and only affects how fast commands appear: naming a guild registers
 * instantly, while a global registration is cached by Discord for about an hour — which is a long
 * time to wait to find out a description had a typo.
 */
fun main() = runBlocking {
    val token = System.getenv("DISCORD_TOKEN").orEmpty()
    val devGuild = System.getenv("DISCORD_GUILD").orEmpty().takeIf { it.isNotBlank() }?.let(::GuildId)

    val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

    val gateway = SculkDiscord.create(
        BotConfig(
            token = token,
            // Only what is used. MessageContent is privileged and has to be enabled on the
            // application first; asking for one the bot does not need is an approval step somebody
            // has to justify to Discord for a feature that was never built.
            intents = setOf(Intent.GuildMessages, Intent.MessageContent),
            commandGuilds = setOfNotNull(devGuild),
        ),
        scope,
        listOf(BotMarker::class.java.classLoader),
    ).getOrElse { reason, _ ->
        // A missing token or backend is reported, not thrown: the message already reads as an
        // instruction, and a stack trace here would bury it.
        logger.severe(reason)
        return@runBlocking
    }

    gateway.connect().getOrElse { reason, cause ->
        logger.severe("Could not connect: $reason")
        cause?.let { logger.fine(it.stackTraceToString()) }
        return@runBlocking
    }
    logger.info("Connected as ${gateway.selfId?.raw}.")

    val router = InteractionRouter(logger)
    val handles = mutableListOf<SculkHandle>()

    handles += registerCommands(router, gateway)
    handles += gateway.route(router)
    handles += registerRelay(gateway)

    gateway.registerCommands(router.registered, setOfNotNull(devGuild))
        .getOrElse { reason, _ -> logger.warning("Commands were not registered: $reason") }
    gateway.presence(Presence("the console", Presence.Kind.Watching))

    logger.info("Ready. ${router.registered.size} command(s) registered. Ctrl-C to stop.")

    // Everything registered is closed in reverse, and the gateway last. Skipping this leaves JDA's
    // threads running, which for a bot means the process never exits.
    val stopped = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.info("Shutting down.")
            SculkHandle.all(handles).close()
            gateway.close()
            scope.cancel()
            stopped.countDown()
        },
    )
    stopped.await()
}

/**
 * Names this jar's class loader for backend discovery.
 *
 * `ServiceLoader`'s single-argument form searches the thread context loader, which is not reliably
 * the one holding the adapter — so discovery is handed a class we own instead of guessing.
 */
private object BotMarker
