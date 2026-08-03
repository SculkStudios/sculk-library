package studio.sculk.discord.interaction

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.ComponentId
import studio.sculk.discord.command.DiscordCommandSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Sends an interaction to whatever should handle it.
 *
 * **Commands resolve by walking the spec tree, not by a `when` on the name.** The chain this replaces
 * had a real defect of exactly that shape on the Brigadier side: a node declaring two handlers only
 * ever reached the first branch, so half of what the DSL advertised silently did nothing. A tree walk
 * cannot develop that bug, because there is no second place for a path to be listed.
 *
 * **Components resolve by [ComponentId.namespace].** Two plugins posting buttons in the same channel
 * see each other's clicks; anything not addressed to a registered namespace is ignored rather than
 * mis-parsed.
 */
@SculkStable
public class InteractionRouter(private val logger: Logger) {
    private val commands = ConcurrentHashMap<String, DiscordCommandSpec>()
    private val components = ConcurrentHashMap<String, suspend (ComponentInteraction) -> Unit>()
    private val modals = ConcurrentHashMap<String, suspend (ModalInteraction) -> Unit>()

    /** Every command registered, in the order Discord should be given them. */
    public val registered: List<DiscordCommandSpec> get() = commands.values.sortedBy { it.name }

    public fun register(spec: DiscordCommandSpec): SculkHandle {
        require(commands.putIfAbsent(spec.name, spec) == null) {
            "A command named '${spec.name}' is already registered. Discord keys commands by name, so the " +
                "second registration would silently replace the first."
        }
        return SculkHandle { commands.remove(spec.name) }
    }

    /** Handles every component whose id carries [namespace]. */
    public fun onComponent(namespace: String, handler: suspend (ComponentInteraction) -> Unit): SculkHandle {
        require(components.putIfAbsent(namespace, handler) == null) {
            "A component handler for namespace '$namespace' is already registered."
        }
        return SculkHandle { components.remove(namespace) }
    }

    public fun onModal(namespace: String, handler: suspend (ModalInteraction) -> Unit): SculkHandle {
        require(modals.putIfAbsent(namespace, handler) == null) {
            "A modal handler for namespace '$namespace' is already registered."
        }
        return SculkHandle { modals.remove(namespace) }
    }

    /**
     * Finds the node a path resolves to, or null.
     *
     * Separate from [dispatch] so the resolution half is testable without an interaction.
     */
    public fun resolve(path: String): DiscordCommandSpec? {
        val root = commands[path.trim().split(" ").firstOrNull().orEmpty()] ?: return null
        return root.at(path)?.takeIf { it.executable }
    }

    /**
     * Runs the handler for [context], reporting anything it cannot route.
     *
     * Every failure path ends in a message to the user. A deferred interaction that is never followed
     * up shows "thinking…" until Discord times it out, which is indistinguishable from the bot being
     * dead — so an exception here must still say something, and saying nothing is not an option the
     * caller can accidentally take.
     */
    public suspend fun dispatch(context: DiscordCommandContext) {
        val node = resolve(context.path)
        if (node?.executor == null) {
            logger.warning("No handler for /${context.path}; the registered set may be stale on Discord's side.")
            context.answer(
                "`/${context.path}` is not a command this server handles any more. It may still be cached by Discord for up to an hour.",
            )
            return
        }
        guarded("/${context.path}", context) { node.executor.invoke(context) }
    }

    public suspend fun dispatch(context: ComponentInteraction) {
        val handler = components[context.componentId.namespace] ?: return
        guarded("component ${context.componentId}", context) { handler(context) }
    }

    public suspend fun dispatch(context: ModalInteraction) {
        val handler = modals[context.modalId.namespace] ?: return
        guarded("modal ${context.modalId}", context) { handler(context) }
    }

    private suspend inline fun guarded(what: String, interaction: Interaction, block: () -> Unit) {
        runCatching { block() }.onFailure { error ->
            logger.warning("Handling $what failed: ${error.message}")
            interaction.answer("Something went wrong handling that — check the server console.")
        }
    }
}

/**
 * Says something, whichever side of acknowledgement this interaction is on.
 *
 * The router cannot know whether a handler already deferred before it threw, and picking wrong means
 * the error message itself is rejected and the user is left on "thinking…". Trying the direct reply
 * and falling back to a follow-up covers both without the caller tracking state.
 */
private suspend fun Interaction.answer(markdown: String) {
    val message = studio.sculk.discord.message.message {
        text(markdown)
        ephemeral = true
    }
    if (!acknowledged && reply(message).isSuccess) return
    runCatching { defer(ephemeral = true).getOrNull()?.respond(message) }
}
