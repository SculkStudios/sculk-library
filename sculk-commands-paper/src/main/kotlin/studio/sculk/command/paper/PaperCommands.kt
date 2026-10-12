package studio.sculk.command.paper

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.command.ArgumentHandle
import studio.sculk.command.ArgumentShape
import studio.sculk.command.ArgumentType
import studio.sculk.command.BuiltinArgument
import studio.sculk.command.CommandExecutor
import studio.sculk.command.CommandMessages
import studio.sculk.command.CommandNode
import studio.sculk.command.CommandResult
import studio.sculk.command.CommandSpec
import studio.sculk.command.SuggestionProvider
import studio.sculk.paper.PaperRuntime
import studio.sculk.service.find
import studio.sculk.service.register
import studio.sculk.text.Text
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import studio.sculk.command.CommandContext as SculkContext

/**
 * Registers command specs with Paper through `LifecycleEvents.COMMANDS`, compiling each into
 * Paper's own Brigadier tree: built-in arguments become native types the client validates, others
 * travel as strings the server converts with the same rules as [studio.sculk.command.CommandDispatcher].
 *
 * Executors start in the sender's context (THREADING.md rule 9) inside the plugin scope, so a
 * command still running when the plugin disables is cancelled with it.
 */
@SculkStable
public class PaperCommands internal constructor(
    private val runtime: PaperRuntime,
    public val text: Text,
    public val messages: CommandMessages,
) {
    private val specs = CopyOnWriteArrayList<CommandSpec>()

    @Volatile
    private var published = false

    init {
        // Paper fires the event once at startup and again on every datapack reload; each time
        // the tree is rebuilt from the registered specs.
        runtime.plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            published = true
            val registrar = event.registrar()
            specs.forEach { spec -> registrar.register(compile(spec), spec.description, spec.aliases) }
        }
    }

    /**
     * Adds [spec]. Commands must be registered while the plugin enables, before the server builds
     * its command tree; registering later is a wiring bug and throws. A requirement that reads the
     * sender's world or game mode resends a player's tree when it changes.
     */
    public fun register(spec: CommandSpec) {
        check(!published) { "/${spec.name} was registered after the server built its command tree; register commands in start()." }
        specs += spec
        followContext(spec.root)
    }

    private fun followContext(node: CommandNode) {
        node.requirement.check?.takeIf { it.contextKeys.isNotEmpty() }?.let(runtime.permissions::follow)
        node.children.forEach(::followContext)
    }

    public fun register(vararg specs: CommandSpec) {
        specs.forEach(::register)
    }

    internal fun compile(spec: CommandSpec): LiteralCommandNode<CommandSourceStack> {
        @Suppress("UNCHECKED_CAST")
        return compileNode(spec.root, emptyList()).build() as LiteralCommandNode<CommandSourceStack>
    }

    /** A literal's aliases become sibling literals with the same subtree, which is how Brigadier spells an alias. */
    private fun compileWithAliases(node: CommandNode, path: List<CommandNode.Argument<*>>): List<ArgumentBuilder<CommandSourceStack, *>> =
        if (node is CommandNode.Literal) {
            (listOf(node.name) + node.aliases).map { word -> compileNode(node, path, word) }
        } else {
            listOf(compileNode(node, path))
        }

    private fun compileNode(
        node: CommandNode,
        path: List<CommandNode.Argument<*>>,
        word: String = node.name,
    ): ArgumentBuilder<CommandSourceStack, *> {
        val here = if (node is CommandNode.Argument<*>) path + node else path
        val builder = when (node) {
            is CommandNode.Literal -> Commands.literal(word)

            is CommandNode.Argument<*> -> Commands.argument(node.name, nativeType(node)).also { argument ->
                node.suggestions?.let { provider -> argument.suggests { context, suggestions -> suggest(provider, context, suggestions) } }
            }
        }
        val requirement = node.requirement
        builder.requires { stack -> requirement.test(PaperCommandSource(stack)) }
        node.executor?.let { executor -> builder.executes { context -> execute(executor, here, context) } }
        node.children.forEach { child -> compileWithAliases(child, here).forEach(builder::then) }
        return builder
    }

    private fun nativeType(node: CommandNode.Argument<*>): com.mojang.brigadier.arguments.ArgumentType<*> = when (val type = node.type) {
        is PaperArgument<*, *> -> type.native

        is BuiltinArgument.BooleanArgument -> BoolArgumentType.bool()

        is BuiltinArgument.IntegerArgument -> IntegerArgumentType.integer(type.min, type.max)

        is BuiltinArgument.LongArgument -> LongArgumentType.longArg(type.min, type.max)

        is BuiltinArgument.DoubleArgument -> DoubleArgumentType.doubleArg(type.min, type.max)

        is BuiltinArgument.StringArgument -> if (type.maxLength == Int.MAX_VALUE &&
            type.pattern == null
        ) {
            stringType(type.shape)
        } else {
            Converted(type)
        }

        is BuiltinArgument.UuidArgument -> ArgumentTypes.uuid()

        is BuiltinArgument.KeyArgument -> ArgumentTypes.key()

        else -> Converted(type)
    }

    private fun execute(executor: CommandExecutor, path: List<CommandNode.Argument<*>>, context: CommandContext<CommandSourceStack>): Int {
        val source = PaperCommandSource(context.source)
        val values = path.associate<CommandNode.Argument<*>, ArgumentHandle<*>, Any?> { node -> node.handle to value(node, context) }
        val label = context.input.substringBefore(' ')
        val sculkContext = SculkContext.of(source, context.input, label, values, text)
        val scope = runtime.scope
        scope.launch(scope.execution.dispatcher(source.executionContext)) {
            val result = executor.run(sculkContext)
            result.message(text, messages)?.let(source.audience::sendMessage)
            if (result is CommandResult.Error) runtime.log.error("/${context.input} failed for ${source.name}.", result.cause)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun value(node: CommandNode.Argument<*>, context: CommandContext<CommandSourceStack>): Any {
        val raw = context.getArgument(node.name, Any::class.java)
        return when (val type = node.type) {
            is PaperArgument<*, *> -> type.resolveRaw(raw, context.source)
            else -> raw
        }
    }

    private fun suggest(
        provider: SuggestionProvider,
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        provider.suggest(builder.remaining, PaperCommandSource(context.source)).forEach(builder::suggest)
        return builder.buildFuture()
    }

    /**
     * A Sculk argument type carried as a string: the client sees a plain string, the server converts
     * it with the type's own rules, and a failure reaches the player as a normal syntax error.
     */
    private class Converted<T : Any>(private val type: ArgumentType<T>) : CustomArgumentType.Converted<T, String> {
        override fun getNativeType(): com.mojang.brigadier.arguments.ArgumentType<String> = stringType(type.shape)

        override fun convert(nativeType: String): T = throw invalid("${type.expected}, read with a sender")

        override fun <S : Any> convert(nativeType: String, source: S): T =
            when (val parsed = type.parse(nativeType, PaperCommandSource(source as CommandSourceStack))) {
                is SculkResult.Success -> parsed.value
                is SculkFailure.InvalidInput -> throw invalid("${parsed.expected}, found ${parsed.received}")
                is SculkFailure -> throw invalid(parsed.describe())
            }

        override fun <S : Any> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
            val stack = context.source as? CommandSourceStack ?: return builder.buildFuture()
            type.suggest(builder.remaining, PaperCommandSource(stack)).forEach(builder::suggest)
            return builder.buildFuture()
        }

        private fun invalid(message: String): CommandSyntaxException = SimpleCommandExceptionType(
            MessageComponentSerializer.message().serialize(net.kyori.adventure.text.Component.text("Expected $message")),
        ).create()
    }
}

private fun stringType(shape: ArgumentShape): StringArgumentType = when (shape) {
    ArgumentShape.WORD -> StringArgumentType.word()
    ArgumentShape.QUOTABLE -> StringArgumentType.string()
    ArgumentShape.GREEDY -> StringArgumentType.greedyString()
}

/**
 * This runtime's command registry, created on first use. Call it while the plugin enables, so
 * its handler is in place before Paper builds the command tree.
 */
@SculkStable
public fun PaperRuntime.commands(text: Text = Text(), messages: CommandMessages = CommandMessages.DEFAULT): PaperCommands =
    synchronized(services) { services.find<PaperCommands>() ?: services.register(PaperCommands(this, text, messages)) }
